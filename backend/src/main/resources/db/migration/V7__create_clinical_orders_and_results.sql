-- SUNCARE CLINIC - Clinical orders, queues, result versions and doctor reviews
-- Migration: V7__create_clinical_orders_and_results.sql
-- Requires: V4, V6
-- SQL Server 2025; DB schema [dbo].
-- DO NOT EXECUTE DIRECTLY IN SSMS. Use the approved Flyway workflow only.
-- This file intentionally omits USE/GO; Flyway selects the database.
-- Cross-row/cross-table and state-transition guards require backend transactions.

-- ClinicalOrder
CREATE TABLE dbo.[ClinicalOrder] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    encounter_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    performing_department_id BIGINT NOT NULL,
    requested_room_id BIGINT NULL,
    ordered_by BIGINT NOT NULL,
    indication NVARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    fully_paid_at DATETIME2(3) NULL,
    validity_days INT NULL,
    expires_at DATETIME2(3) NULL,
    started_at DATETIME2(3) NULL,
    completed_at DATETIME2(3) NULL,
    cancelled_at DATETIME2(3) NULL,
    cancel_reason VARCHAR(32) NULL,
    cancel_note NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ClinicalOrder_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_ClinicalOrder_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_ClinicalOrder PRIMARY KEY ([id]),
    CONSTRAINT UQ_ClinicalOrder_code UNIQUE ([code]),
    CONSTRAINT FK_ClinicalOrder_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT FK_ClinicalOrder_service_id FOREIGN KEY ([service_id]) REFERENCES dbo.[Service]([id]),
    CONSTRAINT FK_ClinicalOrder_performing_department_id FOREIGN KEY ([performing_department_id]) REFERENCES dbo.[Department]([id]),
    CONSTRAINT FK_ClinicalOrder_requested_room_id FOREIGN KEY ([requested_room_id]) REFERENCES dbo.[Room]([id]),
    CONSTRAINT FK_ClinicalOrder_ordered_by FOREIGN KEY ([ordered_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_ClinicalOrder_status CHECK (status IN ('PENDING_PAYMENT','READY','QUEUED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT CK_ClinicalOrder_cancel_reason CHECK (cancel_reason IS NULL OR cancel_reason IN ('PATIENT_REQUEST','EXPIRED','CLINICAL_DECISION')),
    CONSTRAINT CK_ClinicalOrder_validity CHECK (validity_days IS NULL OR validity_days > 0),
    CONSTRAINT CK_ClinicalOrder_paid_deadline CHECK (expires_at IS NULL OR (fully_paid_at IS NOT NULL AND validity_days IS NOT NULL AND expires_at > fully_paid_at)),
    CONSTRAINT CK_ClinicalOrder_completion CHECK (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at)),
    CONSTRAINT CK_ClinicalOrder_cancel_status CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancel_reason IS NULL))
);

-- ClinicalQueueEntry
CREATE TABLE dbo.[ClinicalQueueEntry] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    order_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    checked_in_by BIGINT NOT NULL,
    checked_in_at DATETIME2(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    accepted_by BIGINT NULL,
    accepted_at DATETIME2(3) NULL,
    closed_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ClinicalQueueEntry_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_ClinicalQueueEntry_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_ClinicalQueueEntry PRIMARY KEY ([id]),
    CONSTRAINT FK_ClinicalQueueEntry_order_id FOREIGN KEY ([order_id]) REFERENCES dbo.[ClinicalOrder]([id]),
    CONSTRAINT FK_ClinicalQueueEntry_room_id FOREIGN KEY ([room_id]) REFERENCES dbo.[Room]([id]),
    CONSTRAINT FK_ClinicalQueueEntry_checked_in_by FOREIGN KEY ([checked_in_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT FK_ClinicalQueueEntry_accepted_by FOREIGN KEY ([accepted_by]) REFERENCES dbo.[Staff]([id]),
    CONSTRAINT CK_ClinicalQueueEntry_status CHECK (status IN ('WAITING','ACCEPTED','STARTED','DEFERRED','EXPIRED','CANCELLED','DONE')),
    CONSTRAINT CK_ClinicalQueueEntry_accepted_time CHECK (accepted_at IS NULL OR accepted_at >= checked_in_at),
    CONSTRAINT CK_ClinicalQueueEntry_closed_time CHECK (closed_at IS NULL OR closed_at >= checked_in_at)
);

-- At most one active queue entry for each ClinicalOrder.
CREATE UNIQUE INDEX UX_ClinicalQueueEntry_active_order
ON dbo.[ClinicalQueueEntry]([order_id])
WHERE status IN ('WAITING','ACCEPTED','STARTED');

-- ClinicalOrderEvent
CREATE TABLE dbo.[ClinicalOrderEvent] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    actor_id BIGINT NULL,
    reason NVARCHAR(1000) NULL,
    occurred_at DATETIME2(3) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ClinicalOrderEvent_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ClinicalOrderEvent PRIMARY KEY ([id]),
    CONSTRAINT FK_ClinicalOrderEvent_order_id FOREIGN KEY ([order_id]) REFERENCES dbo.[ClinicalOrder]([id]),
    CONSTRAINT FK_ClinicalOrderEvent_actor_id FOREIGN KEY ([actor_id]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_ClinicalOrderEvent_to_status CHECK (to_status IN ('PENDING_PAYMENT','READY','QUEUED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT CK_ClinicalOrderEvent_from_status CHECK (from_status IS NULL OR from_status IN ('PENDING_PAYMENT','READY','QUEUED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED'))
);

-- ResultVersion
CREATE TABLE dbo.[ResultVersion] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    order_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    replaces_version_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    description NVARCHAR(MAX) NULL,
    conclusion NVARCHAR(MAX) NULL,
    correction_reason NVARCHAR(1000) NULL,
    authored_by BIGINT NOT NULL,
    confirmed_by BIGINT NULL,
    confirmed_at DATETIME2(3) NULL,
    published_by BIGINT NULL,
    published_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ResultVersion_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_ResultVersion_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_ResultVersion PRIMARY KEY ([id]),
    CONSTRAINT UQ_ResultVersion_order_id_version_no UNIQUE ([order_id], [version_no]),
    CONSTRAINT FK_ResultVersion_order_id FOREIGN KEY ([order_id]) REFERENCES dbo.[ClinicalOrder]([id]),
    CONSTRAINT FK_ResultVersion_replaces_version_id FOREIGN KEY ([replaces_version_id]) REFERENCES dbo.[ResultVersion]([id]),
    CONSTRAINT FK_ResultVersion_authored_by FOREIGN KEY ([authored_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT FK_ResultVersion_confirmed_by FOREIGN KEY ([confirmed_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT FK_ResultVersion_published_by FOREIGN KEY ([published_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_ResultVersion_status CHECK (status IN ('DRAFT','CONFIRMED','PUBLISHED')),
    CONSTRAINT CK_ResultVersion_version_number CHECK (version_no > 0),
    CONSTRAINT CK_ResultVersion_confirmation CHECK ((status = 'DRAFT') OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
    CONSTRAINT CK_ResultVersion_publication CHECK ((status <> 'PUBLISHED') OR (published_by IS NOT NULL AND published_at IS NOT NULL)),
    CONSTRAINT CK_ResultVersion_correction CHECK (replaces_version_id IS NULL OR correction_reason IS NOT NULL)
);

-- Prevent two successor versions from replacing the same published version.
CREATE UNIQUE INDEX UX_ResultVersion_replaces_version_id
ON dbo.[ResultVersion]([replaces_version_id])
WHERE replaces_version_id IS NOT NULL;

-- One currently editable (DRAFT or CONFIRMED) version per order.
CREATE UNIQUE INDEX UX_ResultVersion_editable_order
ON dbo.[ResultVersion]([order_id])
WHERE status IN ('DRAFT','CONFIRMED');

-- ServiceAnalyte
CREATE TABLE dbo.[ServiceAnalyte] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    service_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name NVARCHAR(150) NOT NULL,
    default_unit NVARCHAR(50) NULL,
    reference_text NVARCHAR(500) NULL,
    sort_order INT NOT NULL,
    is_active BIT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ServiceAnalyte_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_ServiceAnalyte_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_ServiceAnalyte PRIMARY KEY ([id]),
    CONSTRAINT UQ_ServiceAnalyte_service_id_code UNIQUE ([service_id], [code]),
    CONSTRAINT FK_ServiceAnalyte_service_id FOREIGN KEY ([service_id]) REFERENCES dbo.[Service]([id])
);

-- ResultValue
CREATE TABLE dbo.[ResultValue] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    result_version_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    analyte_id BIGINT NULL,
    analyte_name NVARCHAR(150) NOT NULL,
    numeric_value DECIMAL(18,6) NULL,
    text_value NVARCHAR(1000) NULL,
    unit NVARCHAR(50) NULL,
    reference_text NVARCHAR(500) NULL,
    abnormal_flag VARCHAR(16) NULL,
    sort_order INT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ResultValue_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_ResultValue_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_ResultValue PRIMARY KEY ([id]),
    CONSTRAINT UQ_ResultValue_result_version_id_line_no UNIQUE ([result_version_id], [line_no]),
    CONSTRAINT FK_ResultValue_result_version_id FOREIGN KEY ([result_version_id]) REFERENCES dbo.[ResultVersion]([id]),
    CONSTRAINT FK_ResultValue_analyte_id FOREIGN KEY ([analyte_id]) REFERENCES dbo.[ServiceAnalyte]([id]),
    CONSTRAINT CK_ResultValue_value_xor CHECK ((numeric_value IS NOT NULL AND text_value IS NULL) OR (numeric_value IS NULL AND text_value IS NOT NULL)),
    CONSTRAINT CK_ResultValue_flag CHECK (abnormal_flag IS NULL OR abnormal_flag IN ('NORMAL','LOW','HIGH','CRITICAL')),
    CONSTRAINT CK_ResultValue_line CHECK (line_no > 0)
);

-- ResultAttachment
CREATE TABLE dbo.[ResultAttachment] (
    result_version_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ResultAttachment_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ResultAttachment PRIMARY KEY ([result_version_id], [file_id]),
    CONSTRAINT FK_ResultAttachment_result_version_id FOREIGN KEY ([result_version_id]) REFERENCES dbo.[ResultVersion]([id]),
    CONSTRAINT FK_ResultAttachment_file_id FOREIGN KEY ([file_id]) REFERENCES dbo.[FileObject]([id])
);

-- ClinicalReview
CREATE TABLE dbo.[ClinicalReview] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    encounter_id BIGINT NOT NULL,
    result_version_id BIGINT NOT NULL,
    reviewed_by BIGINT NOT NULL,
    reviewed_at DATETIME2(3) NOT NULL,
    assessment NVARCHAR(2000) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ClinicalReview_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ClinicalReview PRIMARY KEY ([id]),
    CONSTRAINT UQ_ClinicalReview_encounter_id_result_version_id UNIQUE ([encounter_id], [result_version_id]),
    CONSTRAINT FK_ClinicalReview_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT FK_ClinicalReview_result_version_id FOREIGN KEY ([result_version_id]) REFERENCES dbo.[ResultVersion]([id]),
    CONSTRAINT FK_ClinicalReview_reviewed_by FOREIGN KEY ([reviewed_by]) REFERENCES dbo.[Account]([id])
);
