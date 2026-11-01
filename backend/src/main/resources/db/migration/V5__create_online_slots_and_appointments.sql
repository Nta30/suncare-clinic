-- SUNCARE CLINIC - Online slots, booking and appointment history
-- Migration: V5__create_online_slots_and_appointments.sql
-- Requires: V1, V2, V3
-- SQL Server 2025; DB schema [dbo].
-- DO NOT EXECUTE DIRECTLY IN SSMS. Use the approved Flyway workflow only.
-- This file intentionally omits USE/GO; Flyway selects the database.
-- Cross-row/cross-table and state-transition guards require backend transactions.

-- OnlineSlot
CREATE TABLE dbo.[OnlineSlot] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    assignment_id BIGINT NOT NULL,
    starts_at DATETIME2(3) NOT NULL,
    ends_at DATETIME2(3) NOT NULL,
    is_open BIT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_OnlineSlot_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_OnlineSlot_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_OnlineSlot PRIMARY KEY ([id]),
    CONSTRAINT UQ_OnlineSlot_assignment_id_starts_at UNIQUE ([assignment_id], [starts_at]),
    CONSTRAINT FK_OnlineSlot_assignment_id FOREIGN KEY ([assignment_id]) REFERENCES dbo.[WorkAssignment]([id]),
    CONSTRAINT CK_OnlineSlot_twenty_minutes CHECK (ends_at = DATEADD(MINUTE, 20, starts_at))
);

-- BookingHold
CREATE TABLE dbo.[BookingHold] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    patient_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    requested_doctor_id BIGINT NULL,
    price_id BIGINT NOT NULL,
    quoted_amount DECIMAL(19,0) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME2(3) NOT NULL,
    confirmed_at DATETIME2(3) NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_BookingHold_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_BookingHold_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_BookingHold PRIMARY KEY ([id]),
    CONSTRAINT UQ_BookingHold_code UNIQUE ([code]),
    CONSTRAINT UQ_BookingHold_idempotency_key UNIQUE ([idempotency_key]),
    CONSTRAINT FK_BookingHold_patient_id FOREIGN KEY ([patient_id]) REFERENCES dbo.[Patient]([id]),
    CONSTRAINT FK_BookingHold_slot_id FOREIGN KEY ([slot_id]) REFERENCES dbo.[OnlineSlot]([id]),
    CONSTRAINT FK_BookingHold_requested_doctor_id FOREIGN KEY ([requested_doctor_id]) REFERENCES dbo.[Staff]([id]),
    CONSTRAINT FK_BookingHold_price_id FOREIGN KEY ([price_id]) REFERENCES dbo.[ServicePrice]([id]),
    CONSTRAINT CK_BookingHold_amount CHECK (quoted_amount > 0),
    CONSTRAINT CK_BookingHold_status CHECK (status IN ('ACTIVE','CONFIRMED','EXPIRED')),
    CONSTRAINT CK_BookingHold_expiration CHECK (expires_at > created_at),
    CONSTRAINT CK_BookingHold_confirmation CHECK ((status = 'CONFIRMED' AND confirmed_at IS NOT NULL) OR (status <> 'CONFIRMED' AND confirmed_at IS NULL))
);

-- SlotReservation: current ownership row, DELETE only as part of authorized cancellation/expiration transaction
CREATE TABLE dbo.[SlotReservation] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    slot_id BIGINT NOT NULL,
    hold_id BIGINT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_SlotReservation_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_SlotReservation PRIMARY KEY ([id]),
    CONSTRAINT UQ_SlotReservation_slot_id UNIQUE ([slot_id]),
    CONSTRAINT UQ_SlotReservation_hold_id UNIQUE ([hold_id]),
    CONSTRAINT FK_SlotReservation_slot_id FOREIGN KEY ([slot_id]) REFERENCES dbo.[OnlineSlot]([id]),
    CONSTRAINT FK_SlotReservation_hold_id FOREIGN KEY ([hold_id]) REFERENCES dbo.[BookingHold]([id])
);

-- Appointment
CREATE TABLE dbo.[Appointment] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    visit_mode VARCHAR(16) NOT NULL,
    onsite_assignment_id BIGINT NULL,
    online_slot_id BIGINT NULL,
    hold_id BIGINT NULL,
    price_id BIGINT NOT NULL,
    examination_amount DECIMAL(19,0) NOT NULL,
    status VARCHAR(32) NOT NULL,
    booked_at DATETIME2(3) NOT NULL,
    cancelled_at DATETIME2(3) NULL,
    cancel_reason NVARCHAR(500) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Appointment_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_Appointment_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_Appointment PRIMARY KEY ([id]),
    CONSTRAINT UQ_Appointment_code UNIQUE ([code]),
    CONSTRAINT FK_Appointment_patient_id FOREIGN KEY ([patient_id]) REFERENCES dbo.[Patient]([id]),
    CONSTRAINT FK_Appointment_doctor_id FOREIGN KEY ([doctor_id]) REFERENCES dbo.[Staff]([id]),
    CONSTRAINT FK_Appointment_department_id FOREIGN KEY ([department_id]) REFERENCES dbo.[Department]([id]),
    CONSTRAINT FK_Appointment_specialty_id FOREIGN KEY ([specialty_id]) REFERENCES dbo.[Specialty]([id]),
    CONSTRAINT FK_Appointment_onsite_assignment_id FOREIGN KEY ([onsite_assignment_id]) REFERENCES dbo.[WorkAssignment]([id]),
    CONSTRAINT FK_Appointment_online_slot_id FOREIGN KEY ([online_slot_id]) REFERENCES dbo.[OnlineSlot]([id]),
    CONSTRAINT FK_Appointment_hold_id FOREIGN KEY ([hold_id]) REFERENCES dbo.[BookingHold]([id]),
    CONSTRAINT FK_Appointment_price_id FOREIGN KEY ([price_id]) REFERENCES dbo.[ServicePrice]([id]),
    CONSTRAINT CK_Appointment_visit_source CHECK ((visit_mode = 'ONSITE' AND onsite_assignment_id IS NOT NULL AND online_slot_id IS NULL AND hold_id IS NULL) OR (visit_mode = 'ONLINE' AND onsite_assignment_id IS NULL AND online_slot_id IS NOT NULL AND hold_id IS NOT NULL)),
    CONSTRAINT CK_Appointment_status CHECK (status IN ('BOOKED','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT CK_Appointment_amount CHECK (examination_amount > 0),
    CONSTRAINT CK_Appointment_cancellation CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_at IS NULL))
);

-- One booking per hold, but cancelled appointments remain in history.
CREATE UNIQUE INDEX UX_Appointment_hold_id
ON dbo.[Appointment]([hold_id]) WHERE hold_id IS NOT NULL;

-- One non-cancelled appointment per online slot.
CREATE UNIQUE INDEX UX_Appointment_online_slot_active
ON dbo.[Appointment]([online_slot_id])
WHERE online_slot_id IS NOT NULL AND status <> 'CANCELLED';

-- AppointmentEvent
CREATE TABLE dbo.[AppointmentEvent] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    appointment_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor_id BIGINT NULL,
    reason NVARCHAR(500) NULL,
    occurred_at DATETIME2(3) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_AppointmentEvent_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_AppointmentEvent PRIMARY KEY ([id]),
    CONSTRAINT FK_AppointmentEvent_appointment_id FOREIGN KEY ([appointment_id]) REFERENCES dbo.[Appointment]([id]),
    CONSTRAINT FK_AppointmentEvent_actor_id FOREIGN KEY ([actor_id]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_AppointmentEvent_event_type CHECK (event_type IN ('BOOKED','CANCELLED','NO_SHOW','COMPLETED'))
);
