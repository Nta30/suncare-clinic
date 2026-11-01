-- SUNCARE CLINIC - Video sessions and reconnect history
-- Migration: V10__create_video_sessions.sql
-- Requires: V6
-- SQL Server 2025; DB schema [dbo].
-- DO NOT EXECUTE DIRECTLY IN SSMS. Use the approved Flyway workflow only.
-- This file intentionally omits USE/GO; Flyway selects the database.
-- Cross-row/cross-table and state-transition guards require backend transactions.

-- VideoSession
CREATE TABLE dbo.[VideoSession] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    encounter_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_session_ref NVARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    opened_at DATETIME2(3) NULL,
    closed_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_VideoSession_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_VideoSession_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_VideoSession PRIMARY KEY ([id]),
    CONSTRAINT FK_VideoSession_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT CK_VideoSession_status CHECK (status IN ('CREATED','OPEN','CLOSED','FAILED')),
    CONSTRAINT CK_VideoSession_time CHECK (closed_at IS NULL OR (opened_at IS NOT NULL AND closed_at >= opened_at))
);

-- VideoParticipation
CREATE TABLE dbo.[VideoParticipation] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    session_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    joined_at DATETIME2(3) NOT NULL,
    left_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_VideoParticipation_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_VideoParticipation PRIMARY KEY ([id]),
    CONSTRAINT FK_VideoParticipation_session_id FOREIGN KEY ([session_id]) REFERENCES dbo.[VideoSession]([id]),
    CONSTRAINT FK_VideoParticipation_account_id FOREIGN KEY ([account_id]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_VideoParticipation_time CHECK (left_at IS NULL OR left_at >= joined_at)
);
