package com.suncare.clinic.modules.appointment.repository;

import com.suncare.clinic.modules.appointment.dto.AppointmentScheduleDetailResponse;
import com.suncare.clinic.modules.appointment.dto.AppointmentScheduleListItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class AppointmentScheduleRepository {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Saigon");
    private static final String APPOINTMENT_JOINS = """
            FROM dbo.[Appointment] appointment
            JOIN dbo.Patient patient ON patient.id = appointment.patient_id
            JOIN dbo.Staff doctor ON doctor.id = appointment.doctor_id
            JOIN dbo.Department department ON department.id = appointment.department_id
            JOIN dbo.Specialty specialty ON specialty.id = appointment.specialty_id
            LEFT JOIN dbo.Encounter encounter ON encounter.appointment_id = appointment.id
            LEFT JOIN dbo.WorkAssignment onsite_assignment
                ON onsite_assignment.id = appointment.onsite_assignment_id
            LEFT JOIN dbo.OnlineSlot online_slot ON online_slot.id = appointment.online_slot_id
            LEFT JOIN dbo.WorkAssignment online_assignment ON online_assignment.id = online_slot.assignment_id
            LEFT JOIN dbo.Room room ON room.id = COALESCE(onsite_assignment.room_id, online_assignment.room_id)
            """;
    private static final String APPOINTMENT_ORDER = """
            ORDER BY CASE WHEN COALESCE(onsite_assignment.starts_at, online_slot.starts_at) >= SYSUTCDATETIME()
                          THEN 0 ELSE 1 END,
                     COALESCE(onsite_assignment.starts_at, online_slot.starts_at), appointment.id
            """;
    private static final String LIST_APPOINTMENTS = """
            SELECT appointment.id AS appointment_id, appointment.code AS appointment_code,
                   appointment.visit_mode, appointment.status AS appointment_status,
                   patient.full_name AS patient_name, doctor.full_name AS doctor_name,
                   specialty.name AS specialty_name,
                   COALESCE(onsite_assignment.starts_at, online_slot.starts_at) AS starts_at
            %s
            WHERE %s = ?
            %s
            """;
    private static final String APPOINTMENT_DETAIL = """
            SELECT appointment.id AS appointment_id, appointment.code AS appointment_code,
                   appointment.visit_mode, appointment.status AS appointment_status,
                   appointment.examination_amount,
                   encounter.status AS encounter_status,
                   patient.code AS patient_code, patient.full_name AS patient_name,
                   doctor.code AS doctor_code, doctor.full_name AS doctor_name,
                   department.name AS department_name, specialty.name AS specialty_name,
                   room.code AS room_code, room.name AS room_name,
                   previsit.symptoms,
                   COALESCE(onsite_assignment.starts_at, online_slot.starts_at) AS starts_at,
                   COALESCE(onsite_assignment.ends_at, online_slot.ends_at) AS ends_at
            %s
            LEFT JOIN dbo.PrevisitResponse previsit ON previsit.appointment_id = appointment.id
            WHERE appointment.id = ? AND %s = ?
            """;

    private final JdbcTemplate jdbc;

    public AppointmentScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AppointmentScheduleListItemResponse> findForPatient(long patientId) {
        return queryList("appointment.patient_id", patientId);
    }

    public List<AppointmentScheduleListItemResponse> findAssignedToDoctor(long staffId) {
        return queryList("appointment.doctor_id", staffId);
    }

    public Optional<AppointmentScheduleDetailResponse> findDetailForPatient(long patientId, long appointmentId) {
        return queryDetail("appointment.patient_id", patientId, appointmentId);
    }

    public Optional<AppointmentScheduleDetailResponse> findDetailAssignedToDoctor(long staffId, long appointmentId) {
        return queryDetail("appointment.doctor_id", staffId, appointmentId);
    }

    private List<AppointmentScheduleListItemResponse> queryList(String ownerColumn, long ownerId) {
        String sql = LIST_APPOINTMENTS.formatted(APPOINTMENT_JOINS, ownerColumn, APPOINTMENT_ORDER);
        return jdbc.query(sql, (rs, rowNum) -> mapListItem(rs), ownerId);
    }

    private Optional<AppointmentScheduleDetailResponse> queryDetail(String ownerColumn, long ownerId,
                                                                    long appointmentId) {
        String sql = APPOINTMENT_DETAIL.formatted(APPOINTMENT_JOINS, ownerColumn);
        return jdbc.query(sql, (rs, rowNum) -> mapDetail(rs), appointmentId, ownerId)
                .stream().findFirst();
    }

    private AppointmentScheduleListItemResponse mapListItem(ResultSet rs) throws SQLException {
        return new AppointmentScheduleListItemResponse(
                rs.getLong("appointment_id"),
                rs.getString("appointment_code"),
                rs.getString("visit_mode"),
                toBusinessTime(rs.getTimestamp("starts_at")),
                rs.getString("appointment_status"),
                rs.getString("patient_name"),
                rs.getString("doctor_name"),
                rs.getString("specialty_name"));
    }

    private AppointmentScheduleDetailResponse mapDetail(ResultSet rs) throws SQLException {
        return new AppointmentScheduleDetailResponse(
                rs.getLong("appointment_id"),
                rs.getString("appointment_code"),
                rs.getString("visit_mode"),
                toBusinessTime(rs.getTimestamp("starts_at")),
                toBusinessTime(rs.getTimestamp("ends_at")),
                rs.getString("appointment_status"),
                rs.getString("encounter_status"),
                rs.getString("patient_code"),
                rs.getString("patient_name"),
                rs.getString("doctor_code"),
                rs.getString("doctor_name"),
                rs.getString("department_name"),
                rs.getString("specialty_name"),
                rs.getString("room_code"),
                rs.getString("room_name"),
                rs.getString("symptoms"),
                rs.getBigDecimal("examination_amount").toBigIntegerExact().toString());
    }

    private OffsetDateTime toBusinessTime(Timestamp timestamp) {
        if (timestamp == null) return null;
        return timestamp.toLocalDateTime()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(BUSINESS_ZONE)
                .toOffsetDateTime();
    }
}
