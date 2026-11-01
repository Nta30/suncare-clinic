-- SUNCARE CLINIC - File metadata and operational foundations
-- Migration: V4__create_file_settings_and_audit.sql
-- Requires: V1, V2
-- SQL Server 2025; DB schema [dbo].
-- DO NOT EXECUTE DIRECTLY IN SSMS. Use the approved Flyway workflow only.
-- This file intentionally omits USE/GO; Flyway selects the database.
-- Cross-row/cross-table and state-transition guards require backend transactions.

-- FileObject
CREATE TABLE dbo.[FileObject] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    cloudinary_public_id NVARCHAR(255) NOT NULL,
    url NVARCHAR(2048) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    delivery_type NVARCHAR(30) NOT NULL,
    original_name NVARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) NULL,
    uploaded_by BIGINT NULL,
    access_class VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_FileObject_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_FileObject_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_FileObject PRIMARY KEY ([id]),
    CONSTRAINT UQ_FileObject_cloudinary_public_id UNIQUE ([cloudinary_public_id]),
    CONSTRAINT FK_FileObject_uploaded_by FOREIGN KEY ([uploaded_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_FileObject_resource_type CHECK (resource_type IN ('IMAGE','VIDEO','RAW')),
    CONSTRAINT CK_FileObject_access_class CHECK (access_class IN ('MEDICAL','PAYMENT')),
    CONSTRAINT CK_FileObject_status CHECK (status IN ('ACTIVE','QUARANTINED')),
    CONSTRAINT CK_FileObject_byte_size CHECK (byte_size >= 0)
);

-- SystemSetting
CREATE TABLE dbo.[SystemSetting] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    setting_value NVARCHAR(2000) NOT NULL,
    changed_by BIGINT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_SystemSetting_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_SystemSetting_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_SystemSetting PRIMARY KEY ([id]),
    CONSTRAINT UQ_SystemSetting_setting_key UNIQUE ([setting_key]),
    CONSTRAINT FK_SystemSetting_changed_by FOREIGN KEY ([changed_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_SystemSetting_known_key CHECK (setting_key IN ('CLS_VALIDITY_DAYS','ONLINE_HOLD_SECONDS')),
    CONSTRAINT CK_SystemSetting_positive_integer CHECK (TRY_CONVERT(INT, setting_value) IS NOT NULL AND TRY_CONVERT(INT, setting_value) > 0)
);

-- CommandReceipt
CREATE TABLE dbo.[CommandReceipt] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    account_id BIGINT NULL,
    command_type VARCHAR(60) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id BIGINT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_CommandReceipt_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_CommandReceipt PRIMARY KEY ([id]),
    CONSTRAINT UQ_CommandReceipt_command_type_idempotency_key UNIQUE ([command_type], [idempotency_key]),
    CONSTRAINT FK_CommandReceipt_account_id FOREIGN KEY ([account_id]) REFERENCES dbo.[Account]([id])
);

-- AuditEvent
CREATE TABLE dbo.[AuditEvent] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    actor_id BIGINT NULL,
    actor_type VARCHAR(16) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id BIGINT NOT NULL,
    occurred_at DATETIME2(3) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    reason NVARCHAR(1000) NULL,
    redacted_change NVARCHAR(MAX) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_AuditEvent_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_AuditEvent PRIMARY KEY ([id]),
    CONSTRAINT FK_AuditEvent_actor_id FOREIGN KEY ([actor_id]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_AuditEvent_actor CHECK ((actor_type = 'SYSTEM' AND actor_id IS NULL) OR (actor_type = 'USER' AND actor_id IS NOT NULL))
);
