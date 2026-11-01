-- SUNCARE CLINIC - Encounter records, symptoms, diagnosis and clinical history
-- Migration: V6__create_encounters_and_patient_history.sql
-- Requires: V5
-- SQL Server 2025; DB schema [dbo].
-- DO NOT EXECUTE DIRECTLY IN SSMS. Use the approved Flyway workflow only.
-- This file intentionally omits USE/GO; Flyway selects the database.
-- Cross-row/cross-table and state-transition guards require backend transactions.

-- Encounter
CREATE TABLE dbo.[Encounter] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    appointment_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME2(3) NULL,
    completed_at DATETIME2(3) NULL,
    chief_complaint NVARCHAR(2000) NULL,
    examination_note NVARCHAR(MAX) NULL,
    treatment_plan NVARCHAR(MAX) NULL,
    followup_note NVARCHAR(2000) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Encounter_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_Encounter_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_Encounter PRIMARY KEY ([id]),
    CONSTRAINT UQ_Encounter_code UNIQUE ([code]),
    CONSTRAINT UQ_Encounter_appointment_id UNIQUE ([appointment_id]),
    CONSTRAINT FK_Encounter_appointment_id FOREIGN KEY ([appointment_id]) REFERENCES dbo.[Appointment]([id]),
    CONSTRAINT CK_Encounter_status CHECK (status IN ('PLANNED','IN_PROGRESS','WAITING_CLS','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT CK_Encounter_time CHECK (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at))
);

-- PatientHistory
CREATE TABLE dbo.[PatientHistory] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    patient_id BIGINT NOT NULL,
    history_type VARCHAR(16) NOT NULL,
    description NVARCHAR(2000) NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME2(3) NOT NULL,
    is_active BIT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_PatientHistory_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_PatientHistory_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_PatientHistory PRIMARY KEY ([id]),
    CONSTRAINT FK_PatientHistory_patient_id FOREIGN KEY ([patient_id]) REFERENCES dbo.[Patient]([id]),
    CONSTRAINT FK_PatientHistory_recorded_by FOREIGN KEY ([recorded_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_PatientHistory_history_type CHECK (history_type IN ('PERSONAL','FAMILY','SURGERY'))
);

-- PatientAllergy
CREATE TABLE dbo.[PatientAllergy] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    patient_id BIGINT NOT NULL,
    substance NVARCHAR(200) NOT NULL,
    reaction NVARCHAR(1000) NULL,
    severity VARCHAR(16) NOT NULL,
    recorded_by BIGINT NOT NULL,
    is_active BIT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_PatientAllergy_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_PatientAllergy_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_PatientAllergy PRIMARY KEY ([id]),
    CONSTRAINT FK_PatientAllergy_patient_id FOREIGN KEY ([patient_id]) REFERENCES dbo.[Patient]([id]),
    CONSTRAINT FK_PatientAllergy_recorded_by FOREIGN KEY ([recorded_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_PatientAllergy_severity CHECK (severity IN ('UNKNOWN','MILD','MODERATE','SEVERE'))
);

-- EncounterHistorySnapshot
CREATE TABLE dbo.[EncounterHistorySnapshot] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    encounter_id BIGINT NOT NULL,
    history_text NVARCHAR(MAX) NOT NULL,
    allergy_text NVARCHAR(MAX) NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME2(3) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_EncounterHistorySnapshot_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_EncounterHistorySnapshot PRIMARY KEY ([id]),
    CONSTRAINT UQ_EncounterHistorySnapshot_encounter_id UNIQUE ([encounter_id]),
    CONSTRAINT FK_EncounterHistorySnapshot_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT FK_EncounterHistorySnapshot_recorded_by FOREIGN KEY ([recorded_by]) REFERENCES dbo.[Account]([id])
);

-- VitalSign
CREATE TABLE dbo.[VitalSign] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    encounter_id BIGINT NOT NULL,
    measured_at DATETIME2(3) NOT NULL,
    temperature_c DECIMAL(5,2) NULL,
    pulse_bpm SMALLINT NULL,
    systolic_mmhg SMALLINT NULL,
    diastolic_mmhg SMALLINT NULL,
    respiratory_rate SMALLINT NULL,
    spo2_percent DECIMAL(5,2) NULL,
    weight_kg DECIMAL(7,2) NULL,
    height_cm DECIMAL(6,2) NULL,
    recorded_by BIGINT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_VitalSign_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_VitalSign PRIMARY KEY ([id]),
    CONSTRAINT FK_VitalSign_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT FK_VitalSign_recorded_by FOREIGN KEY ([recorded_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_VitalSign_at_least_one CHECK (temperature_c IS NOT NULL OR pulse_bpm IS NOT NULL OR systolic_mmhg IS NOT NULL OR diastolic_mmhg IS NOT NULL OR respiratory_rate IS NOT NULL OR spo2_percent IS NOT NULL OR weight_kg IS NOT NULL OR height_cm IS NOT NULL),
    CONSTRAINT CK_VitalSign_positive CHECK ((temperature_c IS NULL OR temperature_c > 0) AND (pulse_bpm IS NULL OR pulse_bpm > 0) AND (systolic_mmhg IS NULL OR systolic_mmhg > 0) AND (diastolic_mmhg IS NULL OR diastolic_mmhg > 0) AND (respiratory_rate IS NULL OR respiratory_rate > 0) AND (weight_kg IS NULL OR weight_kg > 0) AND (height_cm IS NULL OR height_cm > 0)),
    CONSTRAINT CK_VitalSign_spo2 CHECK (spo2_percent IS NULL OR (spo2_percent > 0 AND spo2_percent <= 100))
);

-- PrevisitResponse
CREATE TABLE dbo.[PrevisitResponse] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    appointment_id BIGINT NOT NULL,
    symptoms NVARCHAR(2000) NULL,
    current_medications NVARCHAR(2000) NULL,
    other_notes NVARCHAR(2000) NULL,
    submitted_at DATETIME2(3) NOT NULL,
    submitted_by BIGINT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_PrevisitResponse_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_PrevisitResponse_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_PrevisitResponse PRIMARY KEY ([id]),
    CONSTRAINT UQ_PrevisitResponse_appointment_id UNIQUE ([appointment_id]),
    CONSTRAINT FK_PrevisitResponse_appointment_id FOREIGN KEY ([appointment_id]) REFERENCES dbo.[Appointment]([id]),
    CONSTRAINT FK_PrevisitResponse_submitted_by FOREIGN KEY ([submitted_by]) REFERENCES dbo.[Account]([id])
);

-- Diagnosis
CREATE TABLE dbo.[Diagnosis] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    encounter_id BIGINT NOT NULL,
    code_system VARCHAR(30) NULL,
    diagnosis_code VARCHAR(30) NULL,
    description NVARCHAR(1000) NOT NULL,
    diagnosis_type VARCHAR(16) NOT NULL,
    is_primary BIT NOT NULL,
    recorded_by BIGINT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Diagnosis_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_Diagnosis_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_Diagnosis PRIMARY KEY ([id]),
    CONSTRAINT FK_Diagnosis_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT FK_Diagnosis_recorded_by FOREIGN KEY ([recorded_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_Diagnosis_type CHECK (diagnosis_type IN ('PRELIMINARY','FINAL'))
);

-- Only one primary FINAL diagnosis per encounter.
CREATE UNIQUE INDEX UX_Diagnosis_primary_final
ON dbo.[Diagnosis]([encounter_id])
WHERE is_primary = 1 AND diagnosis_type = 'FINAL';

-- EncounterEvent
CREATE TABLE dbo.[EncounterEvent] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    encounter_id BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    actor_id BIGINT NULL,
    reason NVARCHAR(1000) NULL,
    occurred_at DATETIME2(3) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_EncounterEvent_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_EncounterEvent PRIMARY KEY ([id]),
    CONSTRAINT FK_EncounterEvent_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT FK_EncounterEvent_actor_id FOREIGN KEY ([actor_id]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_EncounterEvent_to_status CHECK (to_status IN ('PLANNED','IN_PROGRESS','WAITING_CLS','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT CK_EncounterEvent_from_status CHECK (from_status IS NULL OR from_status IN ('PLANNED','IN_PROGRESS','WAITING_CLS','COMPLETED','CANCELLED','NO_SHOW'))
);
