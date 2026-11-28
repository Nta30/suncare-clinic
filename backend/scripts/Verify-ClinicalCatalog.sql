-- Read-only verification/export; run separately on each explicitly selected database.
SET NOCOUNT ON;
DECLARE @Now datetime2(3) = SYSUTCDATETIME();

SELECT DB_NAME() AS database_name,
    (SELECT COUNT(*) FROM dbo.[Service] WHERE service_type = 'CLS' AND is_active = 1) AS active_cls,
    (SELECT COUNT(*) FROM dbo.[Service] s WHERE s.service_type = 'CLS' AND s.is_active = 1
        AND NOT EXISTS (SELECT 1 FROM dbo.ServicePrice p WHERE p.service_id = s.id
            AND p.doctor_id IS NULL AND p.visit_mode IS NULL AND p.valid_from <= @Now
            AND (p.valid_to IS NULL OR @Now < p.valid_to))) AS missing_price,
    (SELECT COUNT(*) FROM dbo.[Service] s WHERE s.service_type = 'CLS' AND s.is_active = 1
        AND NOT EXISTS (SELECT 1 FROM dbo.RoomService rs JOIN dbo.Room r ON r.id = rs.room_id
            JOIN dbo.Department d ON d.id = r.department_id WHERE rs.service_id = s.id
            AND rs.is_active = 1 AND r.is_active = 1 AND r.room_type = 'LAB'
            AND d.is_active = 1 AND d.department_type = 'LAB')) AS missing_room,
    (SELECT COUNT(*) FROM dbo.ServicePrice a JOIN dbo.ServicePrice b
        ON a.service_id = b.service_id AND a.id < b.id JOIN dbo.[Service] s ON s.id = a.service_id
        WHERE s.service_type = 'CLS' AND (b.valid_to IS NULL OR a.valid_from < b.valid_to)
            AND (a.valid_to IS NULL OR b.valid_from < a.valid_to)) AS price_overlaps,
    (SELECT COUNT(*) FROM dbo.ServicePrice p JOIN dbo.[Service] s ON s.id = p.service_id
        WHERE s.service_type = 'CLS' AND (p.doctor_id IS NOT NULL OR p.visit_mode IS NOT NULL)) AS invalid_price_scope,
    (SELECT COUNT(*) FROM dbo.RoomService rs JOIN dbo.[Service] s ON s.id = rs.service_id
        JOIN dbo.Room r ON r.id = rs.room_id JOIN dbo.Department d ON d.id = r.department_id
        WHERE s.service_type = 'CLS' AND rs.is_active = 1
        AND (s.is_active = 0 OR r.is_active = 0 OR d.is_active = 0 OR r.room_type <> 'LAB'
            OR d.department_type <> 'LAB'
            OR (s.result_type = 'LAB' AND d.code <> 'XN')
            OR (s.result_type = 'IMAGING' AND d.code <> 'CDHA')
            OR r.code = 'CLS-XN-LM-01'
            OR (s.code LIKE 'CLS-XQ-%' AND r.code <> 'CLS-CDHA-XQ-01')
            OR (s.code LIKE 'CLS-SA-%' AND r.code <> 'CLS-CDHA-SA-01'))) AS invalid_room_mapping;

-- One row per service, including unsupported/unpriced services (NULLs are visible).
SELECT s.code AS service_code, s.name AS service_name, s.result_type,
    p.amount AS effective_price_vnd, support.departments, support.rooms
FROM dbo.[Service] s
OUTER APPLY (SELECT TOP (1) sp.amount FROM dbo.ServicePrice sp WHERE sp.service_id = s.id
    AND sp.doctor_id IS NULL AND sp.visit_mode IS NULL AND sp.valid_from <= @Now
    AND (sp.valid_to IS NULL OR @Now < sp.valid_to) ORDER BY sp.valid_from DESC, sp.id DESC) p
OUTER APPLY (
    SELECT STRING_AGG(CONVERT(nvarchar(max), d.code + N' — ' + d.name), N'; ')
        WITHIN GROUP (ORDER BY r.code) AS departments,
        STRING_AGG(CONVERT(nvarchar(max), r.code + N' — ' + r.name), N'; ')
        WITHIN GROUP (ORDER BY r.code) AS rooms
    FROM dbo.RoomService rs JOIN dbo.Room r ON r.id = rs.room_id
    JOIN dbo.Department d ON d.id = r.department_id
    WHERE rs.service_id = s.id AND rs.is_active = 1 AND r.is_active = 1
        AND r.room_type = 'LAB' AND d.is_active = 1 AND d.department_type = 'LAB'
) support
WHERE s.service_type = 'CLS' AND s.is_active = 1 ORDER BY s.code;

-- Relevant FK integrity, including possible previously untrusted constraints.
SELECT 'ServicePrice.Service' AS relationship, COUNT(*) AS orphan_count
FROM dbo.ServicePrice p LEFT JOIN dbo.[Service] s ON s.id = p.service_id WHERE s.id IS NULL
UNION ALL SELECT 'ServicePrice.Account', COUNT(*) FROM dbo.ServicePrice p
LEFT JOIN dbo.[Account] a ON a.id = p.created_by WHERE a.id IS NULL
UNION ALL SELECT 'Room.Department', COUNT(*) FROM dbo.Room r
LEFT JOIN dbo.Department d ON d.id = r.department_id WHERE d.id IS NULL
UNION ALL SELECT 'RoomService.Room', COUNT(*) FROM dbo.RoomService rs
LEFT JOIN dbo.Room r ON r.id = rs.room_id WHERE r.id IS NULL
UNION ALL SELECT 'RoomService.Service', COUNT(*) FROM dbo.RoomService rs
LEFT JOIN dbo.[Service] s ON s.id = rs.service_id WHERE s.id IS NULL;
