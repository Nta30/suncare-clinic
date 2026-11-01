-- SUNCARE CLINIC - Read and transaction lookup indexes
-- Migration: V11__add_query_indexes.sql
-- Requires: V4 through V10
-- SQL Server 2025; DB schema [dbo].
-- DO NOT EXECUTE DIRECTLY IN SSMS. Use the approved Flyway workflow only.
-- This file intentionally omits USE/GO; Flyway selects the database.
-- Cross-row/cross-table and state-transition guards require backend transactions.

CREATE INDEX IX_WorkAssignment_staff_time ON dbo.[WorkAssignment] ([staff_id], [starts_at], [ends_at]);

CREATE INDEX IX_WorkAssignment_day_mode_specialty_shift ON dbo.[WorkAssignment] ([work_date], [mode], [specialty_id], [shift_id]);

CREATE INDEX IX_Appointment_onsite_status ON dbo.[Appointment] ([onsite_assignment_id], [status]);

CREATE INDEX IX_Appointment_doctor_mode_status ON dbo.[Appointment] ([doctor_id], [visit_mode], [status]);

CREATE INDEX IX_Appointment_patient_booked ON dbo.[Appointment] ([patient_id], [booked_at] DESC);

CREATE INDEX IX_BookingHold_status_expires ON dbo.[BookingHold] ([status], [expires_at]);

CREATE INDEX IX_ClinicalOrder_status_expiry ON dbo.[ClinicalOrder] ([status], [expires_at]) INCLUDE ([started_at], [encounter_id]);

CREATE INDEX IX_ClinicalOrder_encounter ON dbo.[ClinicalOrder] ([encounter_id]);

CREATE INDEX IX_ClinicalQueueEntry_room_status_checkin ON dbo.[ClinicalQueueEntry] ([room_id], [status], [checked_in_at]);

CREATE INDEX IX_ResultVersion_order_status_version ON dbo.[ResultVersion] ([order_id], [status], [version_no] DESC);

CREATE INDEX IX_Invoice_patient_issued ON dbo.[Invoice] ([patient_id], [issued_at]);

CREATE INDEX IX_Invoice_encounter ON dbo.[Invoice] ([encounter_id]);

CREATE INDEX IX_PaymentAllocation_line ON dbo.[PaymentAllocation] ([invoice_line_id]);

CREATE INDEX IX_Payment_invoice_paid ON dbo.[Payment] ([invoice_id], [paid_at]);

CREATE INDEX IX_RefundRequest_status_date ON dbo.[RefundRequest] ([status], [requested_at]);

CREATE INDEX IX_Refund_request_refunded ON dbo.[Refund] ([request_id], [refunded_at]);

CREATE INDEX IX_AuditEvent_target_time ON dbo.[AuditEvent] ([entity_type], [entity_id], [occurred_at]);

CREATE INDEX IX_PatientHistory_patient ON dbo.[PatientHistory] ([patient_id]);

CREATE INDEX IX_PatientAllergy_patient ON dbo.[PatientAllergy] ([patient_id]);

CREATE INDEX IX_Diagnosis_encounter ON dbo.[Diagnosis] ([encounter_id]);

CREATE INDEX IX_PrescriptionItem_prescription ON dbo.[PrescriptionItem] ([prescription_id]);
