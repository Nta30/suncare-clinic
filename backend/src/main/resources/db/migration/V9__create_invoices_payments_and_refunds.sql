-- SUNCARE CLINIC - Invoice snapshots, payment events, allocations and refunds
-- Migration: V9__create_invoices_payments_and_refunds.sql
-- Requires: V4, V5, V6, V7
-- SQL Server 2025; DB schema [dbo].
-- DO NOT EXECUTE DIRECTLY IN SSMS. Use the approved Flyway workflow only.
-- This file intentionally omits USE/GO; Flyway selects the database.
-- Cross-row/cross-table and state-transition guards require backend transactions.

-- Invoice
CREATE TABLE dbo.[Invoice] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    patient_id BIGINT NOT NULL,
    encounter_id BIGINT NULL,
    hold_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    issued_by BIGINT NULL,
    issuer_type VARCHAR(16) NOT NULL,
    issued_at DATETIME2(3) NULL,
    patient_name_snapshot NVARCHAR(150) NOT NULL,
    patient_address_snapshot NVARCHAR(500) NULL,
    facility_name_snapshot NVARCHAR(200) NOT NULL,
    facility_address_snapshot NVARCHAR(500) NOT NULL,
    total_amount DECIMAL(19,0) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Invoice_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_Invoice_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_Invoice PRIMARY KEY ([id]),
    CONSTRAINT UQ_Invoice_code UNIQUE ([code]),
    CONSTRAINT FK_Invoice_patient_id FOREIGN KEY ([patient_id]) REFERENCES dbo.[Patient]([id]),
    CONSTRAINT FK_Invoice_encounter_id FOREIGN KEY ([encounter_id]) REFERENCES dbo.[Encounter]([id]),
    CONSTRAINT FK_Invoice_hold_id FOREIGN KEY ([hold_id]) REFERENCES dbo.[BookingHold]([id]),
    CONSTRAINT FK_Invoice_issued_by FOREIGN KEY ([issued_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_Invoice_source_xor CHECK ((encounter_id IS NOT NULL AND hold_id IS NULL) OR (encounter_id IS NULL AND hold_id IS NOT NULL)),
    CONSTRAINT CK_Invoice_status CHECK (status IN ('DRAFT','ISSUED','VOID')),
    CONSTRAINT CK_Invoice_issuer CHECK ((issuer_type = 'STAFF' AND issued_by IS NOT NULL) OR (issuer_type = 'SYSTEM' AND issued_by IS NULL)),
    CONSTRAINT CK_Invoice_currency CHECK (currency = 'VND'),
    CONSTRAINT CK_Invoice_total CHECK (total_amount >= 0),
    CONSTRAINT CK_Invoice_issued_date CHECK ((status = 'ISSUED' AND issued_at IS NOT NULL) OR (status <> 'ISSUED'))
);

CREATE UNIQUE INDEX UX_Invoice_hold_id
ON dbo.[Invoice]([hold_id])
WHERE hold_id IS NOT NULL;

-- InvoiceLine
CREATE TABLE dbo.[InvoiceLine] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    invoice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    order_id BIGINT NULL,
    line_type VARCHAR(16) NOT NULL,
    service_id BIGINT NOT NULL,
    price_id BIGINT NOT NULL,
    service_name NVARCHAR(200) NOT NULL,
    unit_name NVARCHAR(50) NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    unit_price DECIMAL(19,0) NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    status VARCHAR(32) NOT NULL,
    cancelled_at DATETIME2(3) NULL,
    cancelled_by BIGINT NULL,
    cancel_reason VARCHAR(32) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_InvoiceLine_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_InvoiceLine_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_InvoiceLine PRIMARY KEY ([id]),
    CONSTRAINT UQ_InvoiceLine_invoice_id_line_no UNIQUE ([invoice_id], [line_no]),
    CONSTRAINT FK_InvoiceLine_invoice_id FOREIGN KEY ([invoice_id]) REFERENCES dbo.[Invoice]([id]),
    CONSTRAINT FK_InvoiceLine_order_id FOREIGN KEY ([order_id]) REFERENCES dbo.[ClinicalOrder]([id]),
    CONSTRAINT FK_InvoiceLine_service_id FOREIGN KEY ([service_id]) REFERENCES dbo.[Service]([id]),
    CONSTRAINT FK_InvoiceLine_price_id FOREIGN KEY ([price_id]) REFERENCES dbo.[ServicePrice]([id]),
    CONSTRAINT FK_InvoiceLine_cancelled_by FOREIGN KEY ([cancelled_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_InvoiceLine_line CHECK (line_no > 0),
    CONSTRAINT CK_InvoiceLine_line_type CHECK ((line_type = 'EXAMINATION' AND order_id IS NULL) OR (line_type = 'CLS' AND order_id IS NOT NULL)),
    CONSTRAINT CK_InvoiceLine_quantity CHECK (quantity = 1),
    CONSTRAINT CK_InvoiceLine_money CHECK (unit_price > 0 AND amount = unit_price),
    CONSTRAINT CK_InvoiceLine_status CHECK (status IN ('ACTIVE','CANCELLED')),
    CONSTRAINT CK_InvoiceLine_cancel_reason CHECK (cancel_reason IS NULL OR cancel_reason IN ('APPOINTMENT_CANCELLED','ORDER_CANCELLED','ORDER_EXPIRED','BOOKING_EXPIRED')),
    CONSTRAINT CK_InvoiceLine_cancellation CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL) OR (status = 'ACTIVE' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL))
);

CREATE UNIQUE INDEX UX_InvoiceLine_order_id
ON dbo.[InvoiceLine]([order_id]) WHERE order_id IS NOT NULL;

-- PaymentRequest
CREATE TABLE dbo.[PaymentRequest] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    invoice_id BIGINT NOT NULL,
    provider VARCHAR(16) NOT NULL,
    merchant_request_key VARCHAR(100) NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME2(3) NOT NULL,
    provider_order_ref VARCHAR(150) NULL,
    qr_file_id BIGINT NULL,
    checkout_reference NVARCHAR(2000) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_PaymentRequest_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_PaymentRequest_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_PaymentRequest PRIMARY KEY ([id]),
    CONSTRAINT UQ_PaymentRequest_merchant_request_key UNIQUE ([merchant_request_key]),
    CONSTRAINT FK_PaymentRequest_invoice_id FOREIGN KEY ([invoice_id]) REFERENCES dbo.[Invoice]([id]),
    CONSTRAINT FK_PaymentRequest_qr_file_id FOREIGN KEY ([qr_file_id]) REFERENCES dbo.[FileObject]([id]),
    CONSTRAINT CK_PaymentRequest_provider CHECK (provider = 'MOMO'),
    CONSTRAINT CK_PaymentRequest_amount CHECK (amount > 0),
    CONSTRAINT CK_PaymentRequest_status CHECK (status IN ('CREATED','PENDING','SUCCEEDED','FAILED','EXPIRED')),
    CONSTRAINT CK_PaymentRequest_expires CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX UX_PaymentRequest_provider_order_ref
ON dbo.[PaymentRequest]([provider_order_ref])
WHERE provider_order_ref IS NOT NULL;

-- PaymentEvent: payment callback receipt; only controlled processing status updates, no raw secrets
CREATE TABLE dbo.[PaymentEvent] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    provider_event_key VARCHAR(200) NOT NULL,
    payment_request_id BIGINT NULL,
    received_at DATETIME2(3) NOT NULL,
    provider_transaction_ref VARCHAR(150) NULL,
    verification_status VARCHAR(16) NOT NULL,
    processing_status VARCHAR(16) NOT NULL,
    result_code VARCHAR(50) NULL,
    payload_hash CHAR(64) NOT NULL,
    redacted_payload NVARCHAR(MAX) NULL,
    processed_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_PaymentEvent_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_PaymentEvent_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_PaymentEvent PRIMARY KEY ([id]),
    CONSTRAINT UQ_PaymentEvent_provider_event_key UNIQUE ([provider_event_key]),
    CONSTRAINT FK_PaymentEvent_payment_request_id FOREIGN KEY ([payment_request_id]) REFERENCES dbo.[PaymentRequest]([id]),
    CONSTRAINT CK_PaymentEvent_provider CHECK (provider = 'MOMO'),
    CONSTRAINT CK_PaymentEvent_verification CHECK (verification_status IN ('VALID','INVALID','UNKNOWN')),
    CONSTRAINT CK_PaymentEvent_processing CHECK (processing_status IN ('RECEIVED','APPLIED','REJECTED','REVIEW'))
);

-- Payment
CREATE TABLE dbo.[Payment] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    invoice_id BIGINT NOT NULL,
    payment_request_id BIGINT NULL,
    method VARCHAR(16) NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    paid_at DATETIME2(3) NOT NULL,
    confirmed_by BIGINT NULL,
    provider_transaction_ref VARCHAR(150) NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Payment_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_Payment PRIMARY KEY ([id]),
    CONSTRAINT UQ_Payment_code UNIQUE ([code]),
    CONSTRAINT UQ_Payment_idempotency_key UNIQUE ([idempotency_key]),
    CONSTRAINT FK_Payment_invoice_id FOREIGN KEY ([invoice_id]) REFERENCES dbo.[Invoice]([id]),
    CONSTRAINT FK_Payment_payment_request_id FOREIGN KEY ([payment_request_id]) REFERENCES dbo.[PaymentRequest]([id]),
    CONSTRAINT FK_Payment_confirmed_by FOREIGN KEY ([confirmed_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_Payment_method CHECK (method IN ('MOMO','CASH')),
    CONSTRAINT CK_Payment_money CHECK (amount > 0 AND currency = 'VND'),
    CONSTRAINT CK_Payment_status CHECK (status = 'CONFIRMED'),
    CONSTRAINT CK_Payment_cash_confirmed CHECK (method <> 'CASH' OR confirmed_by IS NOT NULL)
);

CREATE UNIQUE INDEX UX_Payment_provider_transaction
ON dbo.[Payment]([method], [provider_transaction_ref])
WHERE provider_transaction_ref IS NOT NULL;

-- PaymentAllocation
CREATE TABLE dbo.[PaymentAllocation] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    payment_id BIGINT NOT NULL,
    invoice_line_id BIGINT NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_PaymentAllocation_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_PaymentAllocation PRIMARY KEY ([id]),
    CONSTRAINT UQ_PaymentAllocation_payment_id_invoice_line_id UNIQUE ([payment_id], [invoice_line_id]),
    CONSTRAINT FK_PaymentAllocation_payment_id FOREIGN KEY ([payment_id]) REFERENCES dbo.[Payment]([id]),
    CONSTRAINT FK_PaymentAllocation_invoice_line_id FOREIGN KEY ([invoice_line_id]) REFERENCES dbo.[InvoiceLine]([id]),
    CONSTRAINT CK_PaymentAllocation_amount CHECK (amount > 0)
);

-- RefundRequest
CREATE TABLE dbo.[RefundRequest] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    payment_id BIGINT NOT NULL,
    allocation_id BIGINT NULL,
    reason VARCHAR(32) NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_at DATETIME2(3) NOT NULL,
    requested_by BIGINT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_RefundRequest_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_RefundRequest_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION NOT NULL,
    CONSTRAINT PK_RefundRequest PRIMARY KEY ([id]),
    CONSTRAINT UQ_RefundRequest_code UNIQUE ([code]),
    CONSTRAINT UQ_RefundRequest_idempotency_key UNIQUE ([idempotency_key]),
    CONSTRAINT FK_RefundRequest_payment_id FOREIGN KEY ([payment_id]) REFERENCES dbo.[Payment]([id]),
    CONSTRAINT FK_RefundRequest_allocation_id FOREIGN KEY ([allocation_id]) REFERENCES dbo.[PaymentAllocation]([id]),
    CONSTRAINT FK_RefundRequest_requested_by FOREIGN KEY ([requested_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT CK_RefundRequest_amount CHECK (amount > 0),
    CONSTRAINT CK_RefundRequest_reason CHECK (reason IN ('LINE_CANCELLED','OVERPAYMENT','LATE_PAYMENT')),
    CONSTRAINT CK_RefundRequest_status CHECK (status IN ('REQUESTED','PROCESSING','COMPLETED')),
    CONSTRAINT CK_RefundRequest_line_allocation CHECK (reason <> 'LINE_CANCELLED' OR allocation_id IS NOT NULL)
);

-- Refund
CREATE TABLE dbo.[Refund] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    request_id BIGINT NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    refunded_at DATETIME2(3) NOT NULL,
    processed_by BIGINT NOT NULL,
    method VARCHAR(32) NOT NULL,
    external_reference NVARCHAR(150) NULL,
    evidence_file_id BIGINT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Refund_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_Refund PRIMARY KEY ([id]),
    CONSTRAINT UQ_Refund_code UNIQUE ([code]),
    CONSTRAINT UQ_Refund_idempotency_key UNIQUE ([idempotency_key]),
    CONSTRAINT FK_Refund_request_id FOREIGN KEY ([request_id]) REFERENCES dbo.[RefundRequest]([id]),
    CONSTRAINT FK_Refund_processed_by FOREIGN KEY ([processed_by]) REFERENCES dbo.[Account]([id]),
    CONSTRAINT FK_Refund_evidence_file_id FOREIGN KEY ([evidence_file_id]) REFERENCES dbo.[FileObject]([id]),
    CONSTRAINT CK_Refund_amount CHECK (amount > 0),
    CONSTRAINT CK_Refund_method CHECK (method IN ('CASH','MANUAL_TRANSFER'))
);
