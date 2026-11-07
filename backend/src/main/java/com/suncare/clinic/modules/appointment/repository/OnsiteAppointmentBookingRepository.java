package com.suncare.clinic.modules.appointment.repository;

import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentBookingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class OnsiteAppointmentBookingRepository {
    private final JdbcTemplate jdbc;

    public OnsiteAppointmentBookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int acquireLock(String resource) {
        Integer transactionCount = jdbc.queryForObject("SELECT @@TRANCOUNT", Integer.class);
        if (transactionCount != null && transactionCount == 0) {
            // The SQL Server driver starts its transaction lazily; app locks need an active database transaction.
            jdbc.execute("BEGIN TRANSACTION");
        }
        Integer result = jdbc.queryForObject("""
                SET NOCOUNT ON;
                DECLARE @lock_result INT;
                EXEC @lock_result = sys.sp_getapplock
                    @Resource = ?,
                    @LockMode = 'Exclusive',
                    @LockOwner = 'Transaction',
                    @LockTimeout = 10000;
                SELECT @lock_result;
                """, Integer.class, resource);
        return result == null ? -999 : result;
    }

    public Optional<Receipt> findReceipt(String commandType, String idempotencyKey) {
        return jdbc.query("""
                SELECT account_id, request_hash, resource_id
                FROM dbo.CommandReceipt
                WHERE command_type = ? AND idempotency_key = ?
                """, (rs, rowNum) -> new Receipt(rs.getLong("account_id"),
                rs.getString("request_hash"), rs.getLong("resource_id")), commandType, idempotencyKey)
                .stream().findFirst();
    }

    public List<Candidate> findAvailableCandidates(long specialtyId, LocalDate date, long shiftId, Long doctorId) {
        return jdbc.query("""
                SELECT assignment.id AS assignment_id, assignment.capacity, staff.id AS doctor_id,
                       staff.code AS doctor_code, staff.full_name AS doctor_name,
                       schedule.department_id, specialty.id AS specialty_id, shift.id AS shift_id,
                       shift.name AS shift_name, assignment.work_date, COALESCE(bookings.booked_count, 0) AS booked_count,
                       selected_price.price_id, selected_price.amount
                FROM dbo.WorkAssignment assignment WITH (UPDLOCK, HOLDLOCK, ROWLOCK)
                JOIN dbo.WorkSchedule schedule WITH (HOLDLOCK) ON schedule.id = assignment.schedule_id
                JOIN dbo.Department department WITH (HOLDLOCK) ON department.id = schedule.department_id
                JOIN dbo.DepartmentSpecialty department_specialty WITH (HOLDLOCK)
                  ON department_specialty.department_id = department.id
                 AND department_specialty.specialty_id = assignment.specialty_id
                 AND department_specialty.is_active = 1
                JOIN dbo.Specialty specialty WITH (HOLDLOCK)
                  ON specialty.id = assignment.specialty_id AND specialty.is_active = 1
                JOIN dbo.ShiftDefinition shift WITH (HOLDLOCK)
                  ON shift.id = assignment.shift_id AND shift.is_active = 1
                 AND shift.code IN ('MORNING', 'AFTERNOON')
                JOIN dbo.Room room WITH (HOLDLOCK)
                  ON room.id = assignment.room_id AND room.is_active = 1 AND room.room_type = 'CONSULTATION'
                JOIN dbo.Staff staff WITH (HOLDLOCK) ON staff.id = assignment.staff_id AND staff.is_active = 1
                JOIN dbo.StaffDepartment staff_department WITH (HOLDLOCK)
                  ON staff_department.staff_id = staff.id
                 AND staff_department.department_id = department.id
                 AND staff_department.valid_from <= assignment.starts_at
                 AND (staff_department.valid_to IS NULL OR staff_department.valid_to >= assignment.ends_at)
                JOIN dbo.StaffSpecialty staff_specialty WITH (HOLDLOCK)
                  ON staff_specialty.staff_id = staff.id
                 AND staff_specialty.specialty_id = specialty.id
                 AND staff_specialty.is_active = 1
                CROSS APPLY (
                    SELECT COUNT_BIG(*) AS booked_count
                    FROM dbo.[Appointment] appointment WITH (HOLDLOCK)
                    WHERE appointment.onsite_assignment_id = assignment.id
                      AND appointment.visit_mode = 'ONSITE'
                      AND appointment.status IN ('BOOKED', 'COMPLETED', 'NO_SHOW')
                ) bookings
                CROSS APPLY (
                    SELECT TOP (1) price.id AS price_id, price.amount
                    FROM dbo.ServicePrice price WITH (HOLDLOCK)
                    JOIN dbo.[Service] service WITH (HOLDLOCK) ON service.id = price.service_id
                    WHERE price.doctor_id = staff.id AND price.visit_mode = 'ONSITE'
                      AND price.valid_from <= assignment.starts_at
                      AND (price.valid_to IS NULL OR price.valid_to >= assignment.ends_at)
                      AND service.service_type = 'EXAMINATION' AND service.is_active = 1
                      AND (service.specialty_id IS NULL OR service.specialty_id = specialty.id)
                    ORDER BY CASE WHEN service.specialty_id = specialty.id THEN 0 ELSE 1 END,
                             price.valid_from DESC, price.id DESC
                ) selected_price
                WHERE assignment.mode = 'ONSITE' AND assignment.capacity > 0
                  AND assignment.specialty_id = ? AND assignment.work_date = ? AND assignment.shift_id = ?
                  AND schedule.status = 'PUBLISHED'
                  AND assignment.work_date BETWEEN schedule.period_start AND schedule.period_end
                  AND department.is_active = 1 AND department.department_type = 'CLINICAL'
                  AND room.department_id = department.id
                  AND bookings.booked_count < assignment.capacity
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.[Account] account WITH (HOLDLOCK)
                      JOIN dbo.AccountRole account_role WITH (HOLDLOCK) ON account_role.account_id = account.id
                      JOIN dbo.[Role] role WITH (HOLDLOCK) ON role.id = account_role.role_id
                      WHERE account.staff_id = staff.id AND account.account_type = 'STAFF'
                        AND account.status = 'ACTIVE' AND role.code = 'DOCTOR'
                  )
                  AND (? IS NULL OR staff.id = ?)
                ORDER BY assignment.id
                """, (rs, rowNum) -> mapCandidate(rs), specialtyId, date, shiftId, doctorId, doctorId);
    }

    public long insertAppointment(String code, long patientId, Candidate candidate) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.[Appointment] (code, patient_id, doctor_id, department_id, specialty_id,
                                               visit_mode, onsite_assignment_id, price_id, examination_amount,
                                               status, booked_at)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, 'ONSITE', ?, ?, ?, 'BOOKED', SYSUTCDATETIME())
                """, Long.class, code, patientId, candidate.doctorId(), candidate.departmentId(),
                candidate.specialtyId(), candidate.assignmentId(), candidate.priceId(), candidate.amount());
    }

    public void insertAppointmentEvent(long appointmentId, long actorId) {
        jdbc.update("""
                INSERT INTO dbo.AppointmentEvent (appointment_id, event_type, actor_id, occurred_at)
                VALUES (?, 'BOOKED', ?, SYSUTCDATETIME())
                """, appointmentId, actorId);
    }

    public long insertEncounter(String code, long appointmentId, long actorId) {
        long encounterId = jdbc.queryForObject("""
                INSERT INTO dbo.Encounter (code, appointment_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'PLANNED')
                """, Long.class, code, appointmentId);
        jdbc.update("""
                INSERT INTO dbo.EncounterEvent (encounter_id, from_status, to_status, actor_id, occurred_at)
                VALUES (?, NULL, 'PLANNED', ?, SYSUTCDATETIME())
                """, encounterId, actorId);
        return encounterId;
    }

    public void insertPrevisitResponse(long appointmentId, long accountId, String symptoms) {
        jdbc.update("""
                INSERT INTO dbo.PrevisitResponse (appointment_id, symptoms, submitted_at, submitted_by)
                VALUES (?, ?, SYSUTCDATETIME(), ?)
                """, appointmentId, symptoms, accountId);
    }

    public void insertAudit(long actorId, long appointmentId, String correlationId) {
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id,
                                            occurred_at, correlation_id, redacted_change)
                VALUES (?, 'USER', 'ONSITE_APPOINTMENT_BOOKED', 'Appointment', ?,
                        SYSUTCDATETIME(), ?, N'{"status":"BOOKED"}')
                """, actorId, appointmentId, correlationId);
    }

    public void insertReceipt(long accountId, String commandType, String idempotencyKey,
                              String requestHash, long appointmentId) {
        jdbc.update("""
                INSERT INTO dbo.CommandReceipt (account_id, command_type, idempotency_key,
                                                request_hash, resource_type, resource_id)
                VALUES (?, ?, ?, ?, 'Appointment', ?)
                """, accountId, commandType, idempotencyKey, requestHash, appointmentId);
    }

    public Optional<OnsiteAppointmentBookingResponse> bookingForPatient(long appointmentId, long patientId,
                                                                         boolean replayed) {
        return jdbc.query("""
                SELECT appointment.id AS appointment_id, appointment.code AS appointment_code,
                       encounter.id AS encounter_id, encounter.code AS encounter_code,
                       appointment.doctor_id, staff.full_name AS doctor_name, appointment.department_id,
                       department.name AS department_name, appointment.specialty_id, specialty.name AS specialty_name,
                       room.code AS room_code, room.name AS room_name, assignment.work_date, assignment.shift_id,
                       shift.name AS shift_name, appointment.examination_amount,
                       appointment.status AS appointment_status, encounter.status AS encounter_status
                FROM dbo.[Appointment] appointment
                JOIN dbo.Encounter encounter ON encounter.appointment_id = appointment.id
                JOIN dbo.Staff staff ON staff.id = appointment.doctor_id
                JOIN dbo.Department department ON department.id = appointment.department_id
                JOIN dbo.Specialty specialty ON specialty.id = appointment.specialty_id
                JOIN dbo.WorkAssignment assignment ON assignment.id = appointment.onsite_assignment_id
                JOIN dbo.Room room ON room.id = assignment.room_id
                JOIN dbo.ShiftDefinition shift ON shift.id = assignment.shift_id
                WHERE appointment.id = ? AND appointment.patient_id = ? AND appointment.visit_mode = 'ONSITE'
                """, (rs, rowNum) -> mapResponse(rs, replayed), appointmentId, patientId)
                .stream().findFirst();
    }

    private Candidate mapCandidate(ResultSet rs) throws SQLException {
        return new Candidate(rs.getLong("assignment_id"), rs.getLong("doctor_id"), rs.getString("doctor_code"),
                rs.getString("doctor_name"), rs.getLong("department_id"), rs.getLong("specialty_id"),
                rs.getLong("shift_id"), rs.getString("shift_name"), rs.getDate("work_date").toLocalDate(),
                rs.getLong("booked_count"), rs.getInt("capacity"), rs.getLong("price_id"),
                rs.getBigDecimal("amount"));
    }

    private OnsiteAppointmentBookingResponse mapResponse(ResultSet rs, boolean replayed) throws SQLException {
        BigDecimal amount = rs.getBigDecimal("examination_amount");
        return new OnsiteAppointmentBookingResponse(rs.getLong("appointment_id"), rs.getString("appointment_code"),
                rs.getLong("encounter_id"), rs.getString("encounter_code"), rs.getLong("doctor_id"),
                rs.getString("doctor_name"), rs.getLong("department_id"), rs.getString("department_name"),
                rs.getLong("specialty_id"), rs.getString("specialty_name"),
                rs.getString("room_code"), rs.getString("room_name"),
                rs.getDate("work_date").toLocalDate(), rs.getLong("shift_id"), rs.getString("shift_name"),
                amount.toBigIntegerExact().toString(), rs.getString("appointment_status"),
                rs.getString("encounter_status"), replayed);
    }

    public record Candidate(long assignmentId, long doctorId, String doctorCode, String doctorName,
                            long departmentId, long specialtyId, long shiftId, String shiftName,
                            LocalDate workDate, long bookedCount, int capacity, long priceId, BigDecimal amount) { }

    public record Receipt(long accountId, String requestHash, long resourceId) { }
}
