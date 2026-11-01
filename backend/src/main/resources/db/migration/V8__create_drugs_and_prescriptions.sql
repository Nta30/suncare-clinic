-- SUNCARE CLINIC - Drug catalogue, prescriptions and immutable issued snapshots
-- Migration: V8__create_drugs_and_prescriptions.sql
-- Requires: V6
-- SQL Server 2025; DB schema [dbo].
-- DO NOT EXECUTE DIRECTLY IN SSMS. Use the approved Flyway workflow only.
-- This file intentionally omits USE/GO; Flyway selects the database.
-- Cross-row/cross-table and state-transition guards require backend transactions.

-- Drug
CREATE TABLE dbo.[Drug] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name NVARCHAR(200) NOT NULL,
    ingredient NVARCHAR(300) NULL,
    strength NVARCHAR(100) NULL,
    dosage_form NVARCHAR(100) NULL,
    unit NVARCHAR(50) NOT NULL,
    is_active BIT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Drug_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_Drug_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_Drug PRIMARY KEY ([id]),
    CONSTRAINT UQ_Drug_code UNIQUE ([code])
);

-- Prescription
CREATE TABLE dbo.[Prescription] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    encounter_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    prescribed_by BIGINT NOT NULL,
    issued_at DATETIME2(3) NULL,
    instruction NVARCHAR(2000) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Prescription_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_Prescription_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_Prescription PRIMARY KEY ([id]),
    CONSTRAINT UQ_Prescription_code UNIQUE ([code]),
    CONSTRAINT FK_Prescription_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT FK_Prescription_prescribed_by FOREIGN KEY ([prescribed_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_Prescription_status CHECK (status IN ('DRAFT','ISSUED')),
    CONSTRAINT CK_Prescription_issued_at CHECK ((status = 'ISSUED' AND issued_at IS NOT NULL) OR (status = 'DRAFT' AND issued_at IS NULL))
);

CREATE UNIQUE INDEX UX_Prescription_issued_encounter
ON dbo.[Prescription]([encounter_id])
WHERE status = 'ISSUED';

-- PrescriptionItem
CREATE TABLE dbo.[PrescriptionItem] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    prescription_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    drug_id BIGINT NOT NULL,
    drug_name NVARCHAR(200) NOT NULL,
    ingredient NVARCHAR(300) NULL,
    strength NVARCHAR(100) NULL,
    dosage_form NVARCHAR(100) NULL,
    unit NVARCHAR(50) NOT NULL,
    dose NVARCHAR(200) NOT NULL,
    route NVARCHAR(100) NOT NULL,
    frequency NVARCHAR(200) NOT NULL,
    duration_days INT NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    instruction NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_PrescriptionItem_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_PrescriptionItem_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_PrescriptionItem PRIMARY KEY ([id]),
    CONSTRAINT UQ_PrescriptionItem_prescription_id_line_no UNIQUE ([prescription_id], [line_no]),
    CONSTRAINT FK_PrescriptionItem_prescription_id FOREIGN KEY ([prescription_id]) REFERENCES dbo.[Prescription]([id]),
    CONSTRAINT FK_PrescriptionItem_drug_id FOREIGN KEY ([drug_id]) REFERENCES dbo.[Drug]([id]),
    CONSTRAINT CK_PrescriptionItem_line CHECK (line_no > 0),
    CONSTRAINT CK_PrescriptionItem_duration CHECK (duration_days > 0),
    CONSTRAINT CK_PrescriptionItem_quantity CHECK (quantity > 0)
);
