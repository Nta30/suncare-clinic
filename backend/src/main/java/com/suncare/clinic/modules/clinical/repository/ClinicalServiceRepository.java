package com.suncare.clinic.modules.clinical.repository;

import com.suncare.clinic.modules.clinical.dto.ClinicalServiceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceUpdateRequest;
import com.suncare.clinic.modules.clinical.dto.EffectivePriceResponse;
import com.suncare.clinic.modules.clinical.dto.RoomServiceMappingResponse;
import com.suncare.clinic.modules.clinical.dto.ConsultationPriceResponse;
import com.suncare.clinic.modules.clinical.dto.DoctorOptionResponse;
import com.suncare.clinic.modules.clinical.dto.ServicePriceResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ClinicalServiceRepository {
    private final JdbcTemplate jdbc;

    public ClinicalServiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ClinicalServiceResponse> listServices(String type, Boolean active) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.code, s.name, s.service_type, s.result_type, s.specialty_id,
                       sp.name AS specialty_name, s.is_active,
                       CONVERT(varchar(23), s.created_at, 126) + 'Z' AS created_at,
                       CONVERT(varchar(23), s.updated_at, 126) + 'Z' AS updated_at,
                       s.version,
                       (SELECT TOP 1 amount FROM dbo.ServicePrice pr
                        WHERE pr.service_id = s.id AND pr.doctor_id IS NULL AND pr.visit_mode IS NULL
                          AND pr.valid_from <= SYSUTCDATETIME() AND (pr.valid_to IS NULL OR pr.valid_to > SYSUTCDATETIME())
                        ORDER BY pr.valid_from DESC) AS current_price,
                       (SELECT COUNT(*) FROM dbo.RoomService rs
                        JOIN dbo.Room r ON r.id = rs.room_id
                        WHERE rs.service_id = s.id AND rs.is_active = 1 AND r.is_active = 1) AS supported_rooms_count
                FROM dbo.[Service] s
                LEFT JOIN dbo.Specialty sp ON sp.id = s.specialty_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            sql.append(" AND s.service_type = ?");
            params.add(type.trim().toUpperCase());
        }
        if (active != null) {
            sql.append(" AND s.is_active = ?");
            params.add(active ? 1 : 0);
        }
        sql.append(" ORDER BY s.code ASC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new ClinicalServiceResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("service_type"),
                rs.getString("result_type"),
                rs.getObject("specialty_id") != null ? rs.getLong("specialty_id") : null,
                rs.getString("specialty_name"),
                rs.getBoolean("is_active"),
                Base64.getEncoder().encodeToString(rs.getBytes("version")),
                rs.getBigDecimal("current_price"),
                rs.getInt("supported_rooms_count"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        ), params.toArray());
    }

    public ClinicalServiceResponse findServiceById(long id) {
        String sql = """
                SELECT s.id, s.code, s.name, s.service_type, s.result_type, s.specialty_id,
                       sp.name AS specialty_name, s.is_active,
                       CONVERT(varchar(23), s.created_at, 126) + 'Z' AS created_at,
                       CONVERT(varchar(23), s.updated_at, 126) + 'Z' AS updated_at,
                       s.version,
                       (SELECT TOP 1 amount FROM dbo.ServicePrice pr
                        WHERE pr.service_id = s.id AND pr.doctor_id IS NULL AND pr.visit_mode IS NULL
                          AND pr.valid_from <= SYSUTCDATETIME() AND (pr.valid_to IS NULL OR pr.valid_to > SYSUTCDATETIME())
                        ORDER BY pr.valid_from DESC) AS current_price,
                       (SELECT COUNT(*) FROM dbo.RoomService rs
                        JOIN dbo.Room r ON r.id = rs.room_id
                        WHERE rs.service_id = s.id AND rs.is_active = 1 AND r.is_active = 1) AS supported_rooms_count
                FROM dbo.[Service] s
                LEFT JOIN dbo.Specialty sp ON sp.id = s.specialty_id
                WHERE s.id = ?
                """;
        List<ClinicalServiceResponse> list = jdbc.query(sql, (rs, rowNum) -> new ClinicalServiceResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("service_type"),
                rs.getString("result_type"),
                rs.getObject("specialty_id") != null ? rs.getLong("specialty_id") : null,
                rs.getString("specialty_name"),
                rs.getBoolean("is_active"),
                Base64.getEncoder().encodeToString(rs.getBytes("version")),
                rs.getBigDecimal("current_price"),
                rs.getInt("supported_rooms_count"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        ), id);
        return list.isEmpty() ? null : list.getFirst();
    }

    public ServiceIdentity lockService(long id) {
        List<ServiceIdentity> list = jdbc.query("""
                SELECT id, code, name, service_type, result_type, specialty_id, is_active
                FROM dbo.[Service] WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ?
                """, (rs, rowNum) -> new ServiceIdentity(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("service_type"),
                rs.getString("result_type"),
                rs.getObject("specialty_id") != null ? rs.getLong("specialty_id") : null,
                rs.getBoolean("is_active")
        ), id);
        return list.isEmpty() ? null : list.getFirst();
    }

    public long createService(ClinicalServiceCreateRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.[Service] (code, name, service_type, result_type, specialty_id, is_active)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?)
                """, Long.class,
                request.code().trim().toUpperCase(),
                request.name().trim(),
                request.serviceType().trim().toUpperCase(),
                request.resultType() != null && !request.resultType().isBlank() ? request.resultType().trim().toUpperCase() : null,
                request.specialtyId(),
                request.isActive() == null || request.isActive());
    }

    public int updateService(long id, ClinicalServiceUpdateRequest request, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.[Service]
                SET name = ?, result_type = ?, specialty_id = ?, is_active = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ? AND version = ?
                """,
                request.name().trim(),
                request.resultType() != null && !request.resultType().isBlank() ? request.resultType().trim().toUpperCase() : null,
                request.specialtyId(),
                request.isActive(),
                id,
                version);
    }

    public boolean activeSpecialty(long specialtyId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Specialty WHERE id = ? AND is_active = 1", Integer.class, specialtyId);
        return count != null && count > 0;
    }

    public List<RoomServiceMappingResponse> listLabRoomsWithSupport(long serviceId) {
        return jdbc.query("""
                SELECT r.id AS room_id, r.code AS room_code, r.name AS room_name,
                       r.department_id, d.name AS department_name,
                       CASE WHEN rs.is_active = 1 THEN 1 ELSE 0 END AS is_supported,
                       CONVERT(varchar(23), rs.updated_at, 126) + 'Z' AS updated_at
                FROM dbo.Room r
                JOIN dbo.Department d ON d.id = r.department_id
                LEFT JOIN dbo.RoomService rs ON rs.room_id = r.id AND rs.service_id = ?
                WHERE r.room_type = 'LAB' AND r.is_active = 1
                ORDER BY d.name, r.name
                """, (rs, rowNum) -> new RoomServiceMappingResponse(
                rs.getLong("room_id"),
                rs.getString("room_code"),
                rs.getString("room_name"),
                rs.getLong("department_id"),
                rs.getString("department_name"),
                rs.getBoolean("is_supported"),
                rs.getString("updated_at")
        ), serviceId);
    }

    public RoomIdentity lockRoom(long roomId) {
        List<RoomIdentity> list = jdbc.query("""
                SELECT id, room_type, is_active FROM dbo.Room WITH (UPDLOCK, HOLDLOCK) WHERE id = ?
                """, (rs, rowNum) -> new RoomIdentity(
                rs.getLong("id"),
                rs.getString("room_type"),
                rs.getBoolean("is_active")
        ), roomId);
        return list.isEmpty() ? null : list.getFirst();
    }

    public void upsertRoomService(long roomId, long serviceId, boolean active) {
        jdbc.update("""
                MERGE dbo.RoomService WITH (HOLDLOCK) AS target
                USING (SELECT ? AS room_id, ? AS service_id) AS source
                ON target.room_id = source.room_id AND target.service_id = source.service_id
                WHEN MATCHED THEN
                    UPDATE SET is_active = ?, updated_at = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN
                    INSERT (room_id, service_id, is_active)
                    VALUES (source.room_id, source.service_id, ?);
                """, roomId, serviceId, active, active);
    }

    public List<ServicePriceResponse> listPrices(long serviceId) {
        return jdbc.query("""
                SELECT p.id, p.service_id, p.amount,
                       CONVERT(varchar(23), p.valid_from, 126) + 'Z' AS valid_from,
                       CONVERT(varchar(23), p.valid_to, 126) + 'Z' AS valid_to,
                       p.created_by,
                       COALESCE(s.full_name, a.login_name) AS created_by_name,
                       CONVERT(varchar(23), p.created_at, 126) + 'Z' AS created_at,
                       p.version,
                       CASE WHEN p.valid_from <= SYSUTCDATETIME() AND (p.valid_to IS NULL OR p.valid_to > SYSUTCDATETIME()) THEN 1 ELSE 0 END AS is_effective
                FROM dbo.ServicePrice p
                JOIN dbo.[Account] a ON a.id = p.created_by
                LEFT JOIN dbo.Staff s ON s.id = a.staff_id
                WHERE p.service_id = ? AND p.doctor_id IS NULL AND p.visit_mode IS NULL
                ORDER BY p.valid_from DESC, p.id DESC
                """, (rs, rowNum) -> new ServicePriceResponse(
                rs.getLong("id"),
                rs.getLong("service_id"),
                rs.getBigDecimal("amount"),
                rs.getString("valid_from"),
                rs.getString("valid_to"),
                rs.getLong("created_by"),
                rs.getString("created_by_name"),
                rs.getString("created_at"),
                Base64.getEncoder().encodeToString(rs.getBytes("version")),
                rs.getBoolean("is_effective")
        ), serviceId);
    }

    public PriceIdentity lockPrice(long priceId) {
        List<PriceIdentity> list = jdbc.query("""
                SELECT id, service_id, doctor_id, visit_mode, amount, valid_from, valid_to, version
                FROM dbo.ServicePrice WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ?
                """, (rs, rowNum) -> new PriceIdentity(
                rs.getLong("id"),
                rs.getLong("service_id"),
                rs.getObject("doctor_id") != null ? rs.getLong("doctor_id") : null,
                rs.getString("visit_mode"),
                rs.getBigDecimal("amount"),
                rs.getTimestamp("valid_from").toLocalDateTime(),
                rs.getTimestamp("valid_to") != null ? rs.getTimestamp("valid_to").toLocalDateTime() : null,
                rs.getBytes("version")
        ), priceId);
        return list.isEmpty() ? null : list.getFirst();
    }

    public boolean hasPriceOverlap(long serviceId, Long doctorId, String visitMode,
                                   LocalDateTime from, LocalDateTime to, Long excludedPriceId) {
        String sql = """
                SELECT COUNT(*) FROM dbo.ServicePrice
                WHERE service_id = ?
                  AND ((? IS NULL AND doctor_id IS NULL) OR doctor_id = ?)
                  AND ((? IS NULL AND visit_mode IS NULL) OR visit_mode = ?)
                  AND valid_from < ?
                  AND (valid_to IS NULL OR valid_to > ?)
                  AND (? IS NULL OR id <> ?)
                """;
        LocalDateTime effectiveEnd = to == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : to;
        Integer count = jdbc.queryForObject(sql, Integer.class,
                serviceId,
                doctorId, doctorId,
                visitMode, visitMode,
                effectiveEnd,
                from,
                excludedPriceId, excludedPriceId);
        return count != null && count > 0;
    }

    public long insertPrice(long serviceId, Long doctorId, String visitMode,
                            BigDecimal amount, LocalDateTime from, LocalDateTime to, long createdBy) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.ServicePrice (service_id, doctor_id, visit_mode, amount, valid_from, valid_to, created_by)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, Long.class,
                serviceId, doctorId, visitMode, amount, from, to, createdBy);
    }

    public int endPrice(long priceId, LocalDateTime validTo, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.ServicePrice
                SET valid_to = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ? AND version = ?
                """, validTo, priceId, version);
    }

    public EffectivePriceResponse findEffectivePrice(long serviceId, Long doctorId, String visitMode, LocalDateTime targetTime) {
        LocalDateTime time = targetTime != null ? targetTime : LocalDateTime.now(Clock.systemUTC());
        String sql = """
                SELECT TOP 1 id, service_id, amount,
                       CONVERT(varchar(23), valid_from, 126) + 'Z' AS valid_from,
                       CONVERT(varchar(23), valid_to, 126) + 'Z' AS valid_to
                FROM dbo.ServicePrice
                WHERE service_id = ?
                  AND ((? IS NULL AND doctor_id IS NULL) OR doctor_id = ?)
                  AND ((? IS NULL AND visit_mode IS NULL) OR visit_mode = ?)
                  AND valid_from <= ?
                  AND (valid_to IS NULL OR valid_to > ?)
                ORDER BY valid_from DESC, id DESC
                """;
        List<EffectivePriceResponse> list = jdbc.query(sql, (rs, rowNum) -> new EffectivePriceResponse(
                rs.getLong("id"),
                rs.getLong("service_id"),
                rs.getBigDecimal("amount"),
                rs.getString("valid_from"),
                rs.getString("valid_to")
        ), serviceId, doctorId, doctorId, visitMode, visitMode, time, time);
        return list.isEmpty() ? null : list.getFirst();
    }

    public List<ConsultationPriceResponse> listConsultationPrices(Long serviceId, Long doctorId, String visitMode, Boolean activeOnly) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id,
                       p.service_id,
                       s.code AS service_code,
                       s.name AS service_name,
                       p.doctor_id,
                       d.code AS doctor_code,
                       d.full_name AS doctor_name,
                       p.visit_mode,
                       p.amount,
                       CONVERT(varchar(23), p.valid_from, 126) + 'Z' AS valid_from,
                       CONVERT(varchar(23), p.valid_to, 126) + 'Z' AS valid_to,
                       p.created_by,
                       COALESCE(st.full_name, a.login_name) AS created_by_name,
                       CONVERT(varchar(23), p.created_at, 126) + 'Z' AS created_at,
                       p.version,
                       CASE WHEN p.valid_from <= SYSUTCDATETIME() AND (p.valid_to IS NULL OR p.valid_to > SYSUTCDATETIME()) THEN 1 ELSE 0 END AS is_effective
                FROM dbo.ServicePrice p
                JOIN dbo.[Service] s ON s.id = p.service_id
                JOIN dbo.Staff d ON d.id = p.doctor_id
                JOIN dbo.[Account] a ON a.id = p.created_by
                LEFT JOIN dbo.Staff st ON st.id = a.staff_id
                WHERE p.doctor_id IS NOT NULL AND p.visit_mode IS NOT NULL
                """);
        List<Object> params = new ArrayList<>();
        if (serviceId != null) {
            sql.append(" AND p.service_id = ?");
            params.add(serviceId);
        }
        if (doctorId != null) {
            sql.append(" AND p.doctor_id = ?");
            params.add(doctorId);
        }
        if (visitMode != null && !visitMode.isBlank()) {
            sql.append(" AND p.visit_mode = ?");
            params.add(visitMode.trim().toUpperCase());
        }
        if (activeOnly != null && activeOnly) {
            sql.append(" AND p.valid_from <= SYSUTCDATETIME() AND (p.valid_to IS NULL OR p.valid_to > SYSUTCDATETIME())");
        }
        sql.append(" ORDER BY p.valid_from DESC, p.id DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new ConsultationPriceResponse(
                rs.getLong("id"),
                rs.getLong("service_id"),
                rs.getString("service_code"),
                rs.getString("service_name"),
                rs.getLong("doctor_id"),
                rs.getString("doctor_code"),
                rs.getString("doctor_name"),
                rs.getString("visit_mode"),
                rs.getBigDecimal("amount"),
                rs.getString("valid_from"),
                rs.getString("valid_to"),
                rs.getLong("created_by"),
                rs.getString("created_by_name"),
                rs.getString("created_at"),
                Base64.getEncoder().encodeToString(rs.getBytes("version")),
                rs.getBoolean("is_effective")
        ), params.toArray());
    }

    public boolean isDoctorActive(long doctorId) {
        String sql = """
                SELECT COUNT(*)
                FROM dbo.Staff s
                JOIN dbo.[Account] a ON a.staff_id = s.id
                JOIN dbo.AccountRole ar ON ar.account_id = a.id
                JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE s.id = ? AND r.code = 'DOCTOR' AND s.is_active = 1 AND a.status = 'ACTIVE'
                """;
        Integer count = jdbc.queryForObject(sql, Integer.class, doctorId);
        return count != null && count > 0;
    }

    public List<DoctorOptionResponse> listActiveDoctors() {
        String sql = """
                SELECT DISTINCT s.id, s.code, s.full_name
                FROM dbo.Staff s
                JOIN dbo.[Account] a ON a.staff_id = s.id
                JOIN dbo.AccountRole ar ON ar.account_id = a.id
                JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE r.code = 'DOCTOR' AND s.is_active = 1 AND a.status = 'ACTIVE'
                ORDER BY s.full_name ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> new DoctorOptionResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("full_name")
        ));
    }

    public void audit(long actorId, String action, String entity, long entityId, String detail) {
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id,
                                            occurred_at, correlation_id, redacted_change)
                VALUES (?, 'USER', ?, ?, ?, SYSUTCDATETIME(), ?, ?)
                """, actorId, action, entity, entityId, UUID.randomUUID().toString(), detail);
    }

    public record ServiceIdentity(long id, String code, String name, String serviceType,
                                  String resultType, Long specialtyId, boolean isActive) { }
    public record RoomIdentity(long id, String roomType, boolean isActive) { }
    public record PriceIdentity(long id, long serviceId, Long doctorId, String visitMode,
                                BigDecimal amount, LocalDateTime validFrom, LocalDateTime validTo, byte[] version) { }
}
