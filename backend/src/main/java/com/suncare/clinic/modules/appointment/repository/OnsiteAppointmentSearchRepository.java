package com.suncare.clinic.modules.appointment.repository;

import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentAvailabilityResponse;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentOptionsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;

@Repository
public class OnsiteAppointmentSearchRepository {
    private final JdbcTemplate jdbc;

    public OnsiteAppointmentSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OnsiteAppointmentOptionsResponse options() {
        List<OnsiteAppointmentOptionsResponse.SpecialtyOption> specialties = jdbc.query("""
                SELECT DISTINCT specialty.id, specialty.code, specialty.name
                FROM dbo.Specialty specialty
                JOIN dbo.DepartmentSpecialty link ON link.specialty_id = specialty.id AND link.is_active = 1
                JOIN dbo.Department department ON department.id = link.department_id
                WHERE specialty.is_active = 1 AND department.is_active = 1
                  AND department.department_type = 'CLINICAL'
                ORDER BY specialty.name, specialty.code
                """, (rs, rowNum) -> new OnsiteAppointmentOptionsResponse.SpecialtyOption(
                rs.getLong("id"), rs.getString("code"), rs.getString("name")));

        List<OnsiteAppointmentOptionsResponse.ShiftOption> shifts = jdbc.query("""
                SELECT id, code, name, local_start, local_end
                FROM dbo.ShiftDefinition
                WHERE is_active = 1 AND code IN ('MORNING', 'AFTERNOON')
                ORDER BY CASE code WHEN 'MORNING' THEN 1 ELSE 2 END
                """, (rs, rowNum) -> new OnsiteAppointmentOptionsResponse.ShiftOption(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                timeText(rs.getTime("local_start")), timeText(rs.getTime("local_end"))));
        return new OnsiteAppointmentOptionsResponse(specialties, shifts);
    }

    public boolean activeSpecialty(long specialtyId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.Specialty specialty
                JOIN dbo.DepartmentSpecialty link ON link.specialty_id = specialty.id AND link.is_active = 1
                JOIN dbo.Department department ON department.id = link.department_id
                WHERE specialty.id = ? AND specialty.is_active = 1
                  AND department.is_active = 1 AND department.department_type = 'CLINICAL'
                """, Integer.class, specialtyId);
        return count != null && count > 0;
    }

    public OnsiteAppointmentOptionsResponse.ShiftOption activeShift(long shiftId) {
        List<OnsiteAppointmentOptionsResponse.ShiftOption> shifts = jdbc.query("""
                SELECT id, code, name, local_start, local_end
                FROM dbo.ShiftDefinition
                WHERE id = ? AND is_active = 1 AND code IN ('MORNING', 'AFTERNOON')
                """, (rs, rowNum) -> new OnsiteAppointmentOptionsResponse.ShiftOption(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                timeText(rs.getTime("local_start")), timeText(rs.getTime("local_end"))), shiftId);
        return shifts.isEmpty() ? null : shifts.getFirst();
    }

    public List<OnsiteAppointmentAvailabilityResponse> availability(Long specialtyId, LocalDate dateFrom,
                                                                     LocalDate dateTo, Long shiftId,
                                                                     String doctorName, LocalDate today) {
        StringBuilder sql = new StringBuilder("""
                SELECT staff.id AS doctor_id, staff.full_name AS doctor_name,
                       department.name AS department_name, room.code AS room_code, room.name AS room_name,
                       specialty.id AS specialty_id, specialty.code AS specialty_code,
                       specialty.name AS specialty_name, assignment.work_date,
                       shift.id AS shift_id, shift.code AS shift_code, shift.name AS shift_name,
                       shift.local_start, shift.local_end,
                       SUM(CAST(assignment.capacity - bookings.booked_count AS BIGINT)) AS available_places,
                       MIN(fees.minimum_fee) AS minimum_fee, MAX(fees.maximum_fee) AS maximum_fee
                FROM dbo.WorkAssignment assignment
                JOIN dbo.WorkSchedule schedule ON schedule.id = assignment.schedule_id
                JOIN dbo.Department department ON department.id = schedule.department_id
                JOIN dbo.Specialty specialty ON specialty.id = assignment.specialty_id
                JOIN dbo.DepartmentSpecialty department_specialty
                  ON department_specialty.department_id = department.id
                 AND department_specialty.specialty_id = specialty.id
                 AND department_specialty.is_active = 1
                JOIN dbo.ShiftDefinition shift ON shift.id = assignment.shift_id
                JOIN dbo.Room room ON room.id = assignment.room_id
                JOIN dbo.Staff staff ON staff.id = assignment.staff_id
                JOIN dbo.StaffDepartment staff_department
                  ON staff_department.staff_id = assignment.staff_id
                 AND staff_department.department_id = department.id
                 AND staff_department.valid_from <= assignment.starts_at
                 AND (staff_department.valid_to IS NULL OR staff_department.valid_to >= assignment.ends_at)
                JOIN dbo.StaffSpecialty staff_specialty
                  ON staff_specialty.staff_id = assignment.staff_id
                 AND staff_specialty.specialty_id = specialty.id
                 AND staff_specialty.is_active = 1
                CROSS APPLY (
                    SELECT COUNT_BIG(*) AS booked_count
                    FROM dbo.[Appointment] appointment
                    WHERE appointment.onsite_assignment_id = assignment.id
                      AND appointment.visit_mode = 'ONSITE'
                      AND appointment.status IN ('BOOKED', 'COMPLETED', 'NO_SHOW')
                ) bookings
                CROSS APPLY (
                    SELECT MIN(price.amount) AS minimum_fee, MAX(price.amount) AS maximum_fee
                    FROM dbo.ServicePrice price
                    JOIN dbo.[Service] service ON service.id = price.service_id
                    WHERE price.doctor_id = assignment.staff_id AND price.visit_mode = 'ONSITE'
                      AND price.valid_from <= assignment.starts_at
                      AND (price.valid_to IS NULL OR price.valid_to >= assignment.ends_at)
                      AND service.service_type = 'EXAMINATION' AND service.is_active = 1
                      AND (service.specialty_id IS NULL OR service.specialty_id = specialty.id)
                ) fees
                WHERE assignment.mode = 'ONSITE' AND assignment.capacity > 0
                  AND schedule.status = 'PUBLISHED'
                  AND assignment.work_date BETWEEN schedule.period_start AND schedule.period_end
                  AND assignment.work_date >= ?
                  AND department.is_active = 1 AND department.department_type = 'CLINICAL'
                  AND specialty.is_active = 1
                  AND room.is_active = 1 AND room.room_type = 'CONSULTATION'
                  AND staff.is_active = 1 AND shift.is_active = 1
                  AND shift.code IN ('MORNING', 'AFTERNOON')
                  AND fees.minimum_fee IS NOT NULL
                  AND bookings.booked_count < assignment.capacity
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.[Account] account
                      JOIN dbo.AccountRole account_role ON account_role.account_id = account.id
                      JOIN dbo.[Role] role ON role.id = account_role.role_id
                      WHERE account.staff_id = staff.id AND account.account_type = 'STAFF'
                        AND account.status = 'ACTIVE' AND role.code = 'DOCTOR'
                  )
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(today);
        if (specialtyId != null) {
            sql.append(" AND specialty.id = ?");
            parameters.add(specialtyId);
        }
        if (dateFrom != null) {
            sql.append(" AND assignment.work_date >= ?");
            parameters.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND assignment.work_date <= ?");
            parameters.add(dateTo);
        }
        if (shiftId != null) {
            sql.append(" AND shift.id = ?");
            parameters.add(shiftId);
        }
        if (doctorName != null) {
            sql.append(" AND UPPER(staff.full_name) LIKE UPPER(?) ESCAPE '!'");
            parameters.add("%" + escapeLike(doctorName) + "%");
        }
        sql.append("""
                GROUP BY staff.id, staff.full_name, department.name, room.code, room.name,
                         specialty.id, specialty.code, specialty.name, assignment.work_date,
                         shift.id, shift.code, shift.name, shift.local_start, shift.local_end
                ORDER BY staff.full_name, assignment.work_date, shift.local_start, specialty.name, specialty.code
                """);
        return jdbc.query(sql.toString(), (rs, rowNum) -> new OnsiteAppointmentAvailabilityResponse(
                rs.getLong("doctor_id"), rs.getString("doctor_name"),
                rs.getString("department_name"), rs.getString("room_code"), rs.getString("room_name"),
                rs.getLong("specialty_id"), rs.getString("specialty_code"), rs.getString("specialty_name"),
                rs.getDate("work_date").toLocalDate(), rs.getLong("shift_id"),
                rs.getString("shift_code"), rs.getString("shift_name"),
                timeText(rs.getTime("local_start")), timeText(rs.getTime("local_end")),
                rs.getLong("available_places"), decimalText(rs.getBigDecimal("minimum_fee")),
                decimalText(rs.getBigDecimal("maximum_fee"))), parameters.toArray());
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
    }

    private String timeText(Time time) {
        return time.toLocalTime().toString().substring(0, 5);
    }

    private String decimalText(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
