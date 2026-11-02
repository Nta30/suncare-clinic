-- V12: allow system/self role grants and seed the documented RBAC catalog.
-- V1-V11 have already been applied; keep their checksums unchanged.
ALTER TABLE dbo.AccountRole ALTER COLUMN granted_by BIGINT NULL;

INSERT INTO dbo.[Role] (code, name, account_type)
SELECT seed.code, seed.name, seed.account_type
FROM (VALUES
    ('PATIENT', N'Bệnh nhân', 'PATIENT'),
    ('DOCTOR', N'Bác sĩ', 'STAFF'),
    ('LAB_STAFF', N'Nhân viên cận lâm sàng', 'STAFF'),
    ('DEPARTMENT_HEAD', N'Trưởng khoa', 'STAFF'),
    ('CASHIER', N'Thu ngân', 'STAFF'),
    ('SYSTEM_ADMIN', N'Quản trị hệ thống', 'STAFF')
) AS seed(code, name, account_type)
WHERE NOT EXISTS (SELECT 1 FROM dbo.[Role] AS existing WHERE existing.code = seed.code);

INSERT INTO dbo.[Permission] (code, description)
SELECT seed.code, seed.description
FROM (VALUES
    ('APPOINTMENT_CREATE_SELF', N'Bệnh nhân tự đặt lịch'),
    ('APPOINTMENT_CANCEL_SELF', N'Bệnh nhân tự hủy lịch online'),
    ('ENCOUNTER_WRITE_ASSIGNED', N'Bác sĩ ghi lượt khám được phân'),
    ('RESULT_WRITE_ASSIGNED', N'Lab ghi kết quả được phân'),
    ('INVOICE_ISSUE_ASSIGNED', N'Bác sĩ phát hành hóa đơn lượt khám'),
    ('CASH_RECEIVE', N'Thu ngân xác nhận tiền mặt'),
    ('REFUND_PROCESS', N'Thu ngân xử lý hoàn tiền'),
    ('SCHEDULE_PUBLISH_DEPARTMENT', N'Trưởng khoa công bố lịch trong khoa'),
    ('REPORT_EXPORT_SELF', N'Bác sĩ xuất báo cáo của mình'),
    ('REPORT_EXPORT_DEPARTMENT', N'Trưởng khoa xuất báo cáo trong khoa'),
    ('REPORT_EXPORT_BILLING', N'Thu ngân xuất báo cáo thu hoàn'),
    ('REPORT_EXPORT_ALL', N'Admin xuất thống kê toàn cơ sở'),
    ('ACCOUNT_MANAGE', N'Admin quản lý tài khoản nhân viên'),
    ('CATALOG_MANAGE', N'Admin quản lý danh mục')
) AS seed(code, description)
WHERE NOT EXISTS (SELECT 1 FROM dbo.[Permission] AS existing WHERE existing.code = seed.code);

INSERT INTO dbo.RolePermission (role_id, permission_id)
SELECT role.id, permission.id
FROM (VALUES
    ('PATIENT', 'APPOINTMENT_CREATE_SELF'),
    ('PATIENT', 'APPOINTMENT_CANCEL_SELF'),
    ('DOCTOR', 'ENCOUNTER_WRITE_ASSIGNED'),
    ('DOCTOR', 'INVOICE_ISSUE_ASSIGNED'),
    ('DOCTOR', 'REPORT_EXPORT_SELF'),
    ('LAB_STAFF', 'RESULT_WRITE_ASSIGNED'),
    ('DEPARTMENT_HEAD', 'SCHEDULE_PUBLISH_DEPARTMENT'),
    ('DEPARTMENT_HEAD', 'REPORT_EXPORT_DEPARTMENT'),
    ('CASHIER', 'CASH_RECEIVE'),
    ('CASHIER', 'REFUND_PROCESS'),
    ('CASHIER', 'REPORT_EXPORT_BILLING'),
    ('SYSTEM_ADMIN', 'REPORT_EXPORT_ALL'),
    ('SYSTEM_ADMIN', 'ACCOUNT_MANAGE'),
    ('SYSTEM_ADMIN', 'CATALOG_MANAGE')
) AS seed(role_code, permission_code)
JOIN dbo.[Role] AS role ON role.code = seed.role_code
JOIN dbo.[Permission] AS permission ON permission.code = seed.permission_code
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.RolePermission AS existing
    WHERE existing.role_id = role.id AND existing.permission_id = permission.id
);
