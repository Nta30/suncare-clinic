-- P11.seed: DEMO prices/capabilities approved by the project owner, 2026-11-27.
-- Explicit local/dev import, not an automatic Flyway migration. No schema changes.
-- Execute with Import-ClinicalCatalog.ps1. @ExpectedDatabase is a bound parameter.
-- Existing names, IDs, activation states and price history are never overwritten.
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() <> @ExpectedDatabase
       OR DB_NAME() NOT IN ('SuncareClinicMigrationTest', 'SuncareClinic')
        THROW 51000, N'Unexpected database; seed aborted.', 1;

    -- Use a real existing active administrator. No account creation in this seed.
    DECLARE @Actor bigint;
    SELECT TOP (1) @Actor = a.id
    FROM dbo.[Account] a WITH (UPDLOCK, HOLDLOCK)
    JOIN dbo.AccountRole ar ON ar.account_id = a.id
    JOIN dbo.[Role] r ON r.id = ar.role_id
    WHERE a.status = 'ACTIVE' AND r.code = 'SYSTEM_ADMIN'
    ORDER BY a.id;
    IF @Actor IS NULL
        THROW 51001, N'No existing active SYSTEM_ADMIN for ServicePrice.created_by. No data written.', 1;

    DECLARE @Now datetime2(3) = SYSUTCDATETIME();
    DECLARE @Departments int = 0, @Rooms int = 0, @Services int = 0,
            @Prices int = 0, @Mappings int = 0;
    DECLARE @Catalog TABLE (
        code varchar(40) PRIMARY KEY, name nvarchar(200), result_type varchar(16),
        amount decimal(19,0), room_code varchar(40)
    );
    INSERT INTO @Catalog VALUES
    ('CLS-CTM', N'Tổng phân tích tế bào máu ngoại vi', 'LAB', 120000, 'CLS-XN-HH-01'),
    ('CLS-MAU-LANG', N'Tốc độ máu lắng', 'LAB', 60000, 'CLS-XN-HH-01'),
    ('CLS-PT', N'Xét nghiệm PT', 'LAB', 100000, 'CLS-XN-HH-01'),
    ('CLS-APTT', N'Xét nghiệm APTT', 'LAB', 120000, 'CLS-XN-HH-01'),
    ('CLS-GLUCOSE', N'Định lượng Glucose trong máu', 'LAB', 50000, 'CLS-XN-SH-01'),
    ('CLS-HBA1C', N'Định lượng HbA1c', 'LAB', 150000, 'CLS-XN-SH-01'),
    ('CLS-URE', N'Định lượng Ure trong máu', 'LAB', 50000, 'CLS-XN-SH-01'),
    ('CLS-CREATININE', N'Định lượng Creatinine trong máu', 'LAB', 60000, 'CLS-XN-SH-01'),
    ('CLS-AST', N'Đo hoạt độ AST (GOT) trong máu', 'LAB', 60000, 'CLS-XN-SH-01'),
    ('CLS-ALT', N'Đo hoạt độ ALT (GPT) trong máu', 'LAB', 60000, 'CLS-XN-SH-01'),
    ('CLS-CHOLESTEROL', N'Định lượng Cholesterol toàn phần', 'LAB', 70000, 'CLS-XN-SH-01'),
    ('CLS-TRIGLYCERID', N'Định lượng Triglycerid', 'LAB', 70000, 'CLS-XN-SH-01'),
    ('CLS-HDL-C', N'Định lượng HDL-C', 'LAB', 80000, 'CLS-XN-SH-01'),
    ('CLS-LDL-C', N'Định lượng LDL-C', 'LAB', 80000, 'CLS-XN-SH-01'),
    ('CLS-ACID-URIC', N'Định lượng Acid Uric trong máu', 'LAB', 60000, 'CLS-XN-SH-01'),
    ('CLS-CRP', N'Định lượng CRP trong máu', 'LAB', 150000, 'CLS-XN-SH-01'),
    ('CLS-DIEN-GIAI', N'Điện giải đồ (Na, K, Cl) máu', 'LAB', 150000, 'CLS-XN-SH-01'),
    ('CLS-GGT', N'Đo hoạt độ GGT trong máu', 'LAB', 80000, 'CLS-XN-SH-01'),
    ('CLS-FT4', N'Định lượng FT4', 'LAB', 120000, 'CLS-XN-PT-01'),
    ('CLS-TSH', N'Định lượng TSH', 'LAB', 120000, 'CLS-XN-PT-01'),
    ('CLS-HBSAG', N'Xét nghiệm HBsAg', 'LAB', 100000, 'CLS-XN-PT-01'),
    ('CLS-NUOC-TIEU', N'Tổng phân tích nước tiểu', 'LAB', 80000, 'CLS-XN-PT-01'),
    ('CLS-PROTEIN-NIEU', N'Định lượng Protein niệu', 'LAB', 60000, 'CLS-XN-PT-01'),
    ('CLS-XQ-NGUC', N'Chụp X-quang ngực thẳng', 'IMAGING', 150000, 'CLS-CDHA-XQ-01'),
    ('CLS-XQ-COT-SONG-TL', N'Chụp X-quang cột sống thắt lưng', 'IMAGING', 200000, 'CLS-CDHA-XQ-01'),
    ('CLS-XQ-KHOP-GOI', N'Chụp X-quang khớp gối', 'IMAGING', 180000, 'CLS-CDHA-XQ-01'),
    ('CLS-XQ-BAN-TAY', N'Chụp X-quang bàn tay', 'IMAGING', 120000, 'CLS-CDHA-XQ-01'),
    ('CLS-SA-BUNG', N'Siêu âm ổ bụng tổng quát', 'IMAGING', 200000, 'CLS-CDHA-SA-01'),
    ('CLS-SA-GIAP', N'Siêu âm tuyến giáp', 'IMAGING', 180000, 'CLS-CDHA-SA-01'),
    ('CLS-SA-TIM', N'Siêu âm tim', 'IMAGING', 350000, 'CLS-CDHA-SA-01'),
    ('CLS-SA-PHAN-MEM', N'Siêu âm phần mềm', 'IMAGING', 150000, 'CLS-CDHA-SA-01'),
    ('CLS-SA-SAN-KHOA', N'Siêu âm sản khoa', 'IMAGING', 250000, 'CLS-CDHA-SA-01');

    -- Lock catalogs during the short import, also protecting concurrent API inserts.
    DECLARE @LockCount bigint;
    SELECT @LockCount = COUNT_BIG(*) FROM dbo.[Service] WITH (TABLOCKX, HOLDLOCK);
    SELECT @LockCount = COUNT_BIG(*) FROM dbo.Department WITH (TABLOCKX, HOLDLOCK);
    SELECT @LockCount = COUNT_BIG(*) FROM dbo.Room WITH (TABLOCKX, HOLDLOCK);
    SELECT @LockCount = COUNT_BIG(*) FROM dbo.ServicePrice WITH (TABLOCKX, HOLDLOCK);
    SELECT @LockCount = COUNT_BIG(*) FROM dbo.RoomService WITH (TABLOCKX, HOLDLOCK);

    -- The original 17 were manually compared by code/name/meaning before authoring.
    -- Fail safely on subsequent catalog drift; never guess prices or duplicate aliases.
    IF EXISTS (SELECT 1 FROM dbo.[Service] s WHERE s.service_type = 'CLS'
        AND s.is_active = 1 AND NOT EXISTS (SELECT 1 FROM @Catalog c WHERE c.code = s.code))
        THROW 51002, N'Unreviewed active CLS service: review its meaning, price and rooms before importing.', 1;
    IF EXISTS (SELECT 1 FROM @Catalog c JOIN dbo.[Service] s ON s.code = c.code
        WHERE s.service_type <> 'CLS' OR ISNULL(s.result_type, '') <> c.result_type OR s.is_active <> 1)
        THROW 51003, N'Existing service has conflicting type or inactive state.', 1;
    IF EXISTS (SELECT 1 FROM @Catalog c JOIN dbo.[Service] s ON s.name = c.name WHERE s.code <> c.code)
        THROW 51004, N'Equivalent service name found under another code; reconcile manually.', 1;

    DECLARE @Dept TABLE (code varchar(40) PRIMARY KEY, name nvarchar(150));
    INSERT INTO @Dept VALUES ('XN', N'Khoa Xét nghiệm'), ('CDHA', N'Khoa Chẩn đoán hình ảnh');
    IF EXISTS (SELECT 1 FROM @Dept x JOIN dbo.Department d ON d.code = x.code
        WHERE d.department_type <> 'LAB' OR d.is_active <> 1)
        THROW 51005, N'Existing department conflicts with active LAB requirement.', 1;
    IF EXISTS (SELECT 1 FROM @Dept x JOIN dbo.Department d ON d.name = x.name WHERE d.code <> x.code)
        THROW 51006, N'Equivalent department exists under another code.', 1;
    INSERT INTO dbo.Department (code, name, department_type, is_active)
    SELECT x.code, x.name, 'LAB', 1 FROM @Dept x
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Department d WHERE d.code = x.code);
    SET @Departments = @@ROWCOUNT;

    DECLARE @Room TABLE (code varchar(40) PRIMARY KEY, name nvarchar(150), department_code varchar(40));
    INSERT INTO @Room VALUES
    ('CLS-XN-PT-01', N'Phòng phân tích xét nghiệm', 'XN'),
    ('CLS-XN-HH-01', N'Phòng xét nghiệm huyết học', 'XN'),
    ('CLS-XN-SH-01', N'Phòng xét nghiệm sinh hóa', 'XN'),
    ('CLS-CDHA-XQ-01', N'Phòng X-quang 01', 'CDHA'),
    ('CLS-CDHA-SA-01', N'Phòng siêu âm 01', 'CDHA');
    -- Sampling room CLS-XN-LM-01 is retained without assigning analytical services.
    -- PT is the DEMO general analysis/immune/endocrine/urine room; SA supports DEMO ultrasound.
    IF EXISTS (SELECT 1 FROM @Room x JOIN dbo.Room r ON r.code = x.code
        JOIN dbo.Department d ON d.id = r.department_id
        WHERE r.room_type <> 'LAB' OR r.is_active <> 1 OR d.code <> x.department_code)
        THROW 51007, N'Existing room has conflicting department/type or inactive state.', 1;
    IF EXISTS (SELECT 1 FROM @Room x JOIN dbo.Room r ON r.name = x.name WHERE r.code <> x.code)
        THROW 51008, N'Equivalent room exists under another code.', 1;
    INSERT INTO dbo.Room (code, name, department_id, room_type, is_active)
    SELECT x.code, x.name, d.id, 'LAB', 1 FROM @Room x
    JOIN dbo.Department d ON d.code = x.department_code
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Room r WHERE r.code = x.code);
    SET @Rooms = @@ROWCOUNT;

    INSERT INTO dbo.[Service] (code, name, service_type, result_type, is_active)
    SELECT c.code, c.name, 'CLS', c.result_type, 1 FROM @Catalog c
    WHERE NOT EXISTS (SELECT 1 FROM dbo.[Service] s WHERE s.code = c.code);
    SET @Services = @@ROWCOUNT;

    IF EXISTS (SELECT 1 FROM dbo.ServicePrice p JOIN dbo.[Service] s ON s.id = p.service_id
        WHERE s.service_type = 'CLS' AND (p.doctor_id IS NOT NULL OR p.visit_mode IS NOT NULL))
        THROW 51009, N'CLS price unexpectedly depends on doctor or visit mode.', 1;
    IF EXISTS (SELECT 1 FROM dbo.ServicePrice a JOIN dbo.ServicePrice b
        ON a.service_id = b.service_id AND a.id < b.id
        JOIN dbo.[Service] s ON s.id = a.service_id
        WHERE s.service_type = 'CLS'
        AND (b.valid_to IS NULL OR a.valid_from < b.valid_to)
        AND (a.valid_to IS NULL OR b.valid_from < a.valid_to))
        THROW 51010, N'Overlapping CLS prices already exist; no history overwritten.', 1;
    -- Preserve current/history/future prices. Fill only the gap from now to next price.
    INSERT INTO dbo.ServicePrice (service_id, amount, valid_from, valid_to, created_by)
    SELECT s.id, c.amount, @Now,
        (SELECT MIN(p.valid_from) FROM dbo.ServicePrice p WHERE p.service_id = s.id AND p.valid_from > @Now),
        @Actor
    FROM @Catalog c JOIN dbo.[Service] s ON s.code = c.code
    WHERE NOT EXISTS (SELECT 1 FROM dbo.ServicePrice p WHERE p.service_id = s.id
        AND p.valid_from <= @Now AND (p.valid_to IS NULL OR @Now < p.valid_to));
    SET @Prices = @@ROWCOUNT;

    DECLARE @Support TABLE (service_code varchar(40), room_code varchar(40), PRIMARY KEY (service_code, room_code));
    INSERT INTO @Support SELECT code, room_code FROM @Catalog;
    INSERT INTO @Support VALUES
    ('CLS-CTM', 'CLS-XN-PT-01'), ('CLS-GLUCOSE', 'CLS-XN-PT-01'),
    ('CLS-AST', 'CLS-XN-PT-01'), ('CLS-ALT', 'CLS-XN-PT-01');
    IF EXISTS (SELECT 1 FROM @Support x JOIN dbo.[Service] s ON s.code = x.service_code
        JOIN dbo.Room r ON r.code = x.room_code
        JOIN dbo.RoomService rs ON rs.service_id = s.id AND rs.room_id = r.id WHERE rs.is_active <> 1)
        THROW 51011, N'Expected mapping was deliberately disabled; not reactivated automatically.', 1;
    -- Reject unexpected active capabilities instead of silently preserving wrong-room mappings.
    IF EXISTS (SELECT 1 FROM dbo.RoomService rs JOIN dbo.[Service] s ON s.id = rs.service_id
        JOIN dbo.Room r ON r.id = rs.room_id WHERE rs.is_active = 1 AND s.service_type = 'CLS'
        AND NOT EXISTS (SELECT 1 FROM @Support x WHERE x.service_code = s.code AND x.room_code = r.code))
        THROW 51012, N'Unreviewed active room capability; check compatibility before seed.', 1;
    INSERT INTO dbo.RoomService (room_id, service_id, is_active)
    SELECT r.id, s.id, 1 FROM @Support x JOIN dbo.[Service] s ON s.code = x.service_code
    JOIN dbo.Room r ON r.code = x.room_code
    WHERE NOT EXISTS (SELECT 1 FROM dbo.RoomService rs WHERE rs.room_id = r.id AND rs.service_id = s.id);
    SET @Mappings = @@ROWCOUNT;

    IF EXISTS (SELECT 1 FROM dbo.[Service] s WHERE s.service_type = 'CLS' AND s.is_active = 1
        AND (NOT EXISTS (SELECT 1 FROM dbo.ServicePrice p WHERE p.service_id = s.id
            AND p.valid_from <= @Now AND (p.valid_to IS NULL OR @Now < p.valid_to))
        OR NOT EXISTS (SELECT 1 FROM dbo.RoomService rs JOIN dbo.Room r ON r.id = rs.room_id
            JOIN dbo.Department d ON d.id = r.department_id WHERE rs.service_id = s.id
            AND rs.is_active = 1 AND r.is_active = 1 AND r.room_type = 'LAB'
            AND d.is_active = 1 AND d.department_type = 'LAB')))
        THROW 51013, N'Active CLS service missing effective price or valid room.', 1;

    COMMIT TRANSACTION;
    SELECT DB_NAME() AS database_name, @Actor AS created_by,
        @Departments AS departments_inserted, @Rooms AS rooms_inserted,
        @Services AS services_inserted, @Prices AS prices_inserted, @Mappings AS mappings_inserted,
        2 - @Departments AS departments_reused, 5 - @Rooms AS performing_rooms_reused,
        32 - @Services AS services_reused;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
