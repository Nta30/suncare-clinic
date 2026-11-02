# 02 — ERD và quan hệ

Sơ đồ logic dùng Mermaid. Các bảng nối và quan hệ chỉ xuất hiện ở sơ đồ module để sơ đồ tổng thể đọc được. Danh sách cột đầy đủ ở tài liệu 03.

## Tổng thể

```mermaid
erDiagram
    Patient ||--o| Account : patient_login
    Staff ||--o| Account : staff_login
    Patient ||--o{ Appointment : books
    Staff ||--o{ WorkAssignment : works
    WorkAssignment ||--o{ OnlineSlot : offers
    OnlineSlot ||--o{ BookingHold : held_over_time
    BookingHold ||--o| Appointment : confirms
    Appointment ||--|| Encounter : creates_atomically
    Encounter ||--o{ ClinicalOrder : orders
    ClinicalOrder ||--o{ ClinicalQueueEntry : visits
    ClinicalOrder ||--o{ ResultVersion : results
    Encounter ||--o{ Diagnosis : diagnoses
    Encounter ||--o{ Prescription : prescribes
    Patient ||--o{ Invoice : billed
    Invoice ||--|{ InvoiceLine : contains
    ClinicalOrder o|--o| InvoiceLine : charged_once
    Invoice ||--o{ PaymentRequest : requests
    Invoice ||--o{ Payment : collects
    Payment ||--o{ PaymentAllocation : allocates
    InvoiceLine ||--o{ PaymentAllocation : receives

    Payment ||--o{ RefundRequest : refund_requested
    RefundRequest ||--o{ Refund : paid_back
```

Account–Patient và Account–Staff là **XOR**, không phải Account được liên kết đồng thời cả hai. Invoice DRAFT có thể chưa có line; khi ISSUED phải có ít nhất một line. ClinicalOrder có đúng một InvoiceLine khi hóa đơn được phát hành; trước đó có thể chưa có.

## Tổ chức và phân quyền

```mermaid
erDiagram
    Account ||--o{ AccountRole : has
    Role ||--o{ AccountRole : assigned
    Role ||--o{ RolePermission : grants
    Permission ||--o{ RolePermission : contains
    Staff ||--o{ StaffDepartment : membership
    Department ||--o{ StaffDepartment : employs
    Staff ||--o{ DepartmentHeadAssignment : leads
    Department ||--o{ DepartmentHeadAssignment : managed
    Staff ||--o{ StaffSpecialty : qualifies
    Specialty ||--o{ StaffSpecialty : field
    Department ||--o{ DepartmentSpecialty : supports
    Specialty ||--o{ DepartmentSpecialty : belongs
    Department ||--o{ Room : owns
    Room ||--o{ RoomService : supports
    Service ||--o{ RoomService : available
    Service ||--o{ ServicePrice : priced
    Staff o|--o{ ServicePrice : examination_price
```

## Phân lịch và đặt khám

```mermaid
erDiagram
    Department ||--o{ WorkSchedule : publishes
    WorkSchedule ||--o{ WorkAssignment : contains
    Staff ||--o{ WorkAssignment : assigned
    Room o|--o{ WorkAssignment : placed
    ShiftDefinition o|--o{ WorkAssignment : onsite_shift
    Specialty o|--o{ WorkAssignment : consultation_field
    WorkAssignment ||--o{ Appointment : onsite_capacity
    WorkAssignment ||--o{ OnlineSlot : online_slots
    OnlineSlot ||--o{ BookingHold : historical_holds
    OnlineSlot ||--o| SlotReservation : current_claim
    BookingHold ||--o| SlotReservation : owns_claim
    Patient ||--o{ BookingHold : requests
    BookingHold o|--o| Appointment : paid_confirmation
    OnlineSlot o|--o{ Appointment : historical_bookings
    Staff ||--o{ Appointment : assigned_doctor
    Appointment ||--o{ AppointmentEvent : history
    Appointment ||--|| Encounter : visit
```

WorkAssignment → Appointment chỉ dùng cho trực tiếp. Mỗi Appointment online thuộc đúng một slot; slot có thể có nhiều Appointment **lịch sử đã hủy**, tối đa một Appointment không hủy. SlotReservation giữ quyền chiếm độc quyền cho hold ACTIVE hoặc CONFIRMED. Hết hạn không xóa BookingHold, chỉ giải phóng reservation.

## Khám, CLS và kê đơn

```mermaid
erDiagram
    Patient ||--o{ PatientHistory : history
    Patient ||--o{ PatientAllergy : allergies
    Appointment ||--o| PrevisitResponse : questionnaire
    Encounter ||--o| EncounterHistorySnapshot : captures
    Encounter ||--o{ VitalSign : measurements
    Encounter ||--o{ Diagnosis : diagnoses
    Encounter ||--o{ EncounterEvent : history
    Encounter ||--o{ ClinicalOrder : requests
    Service ||--o{ ClinicalOrder : selected
    Department ||--o{ ClinicalOrder : performs
    Room o|--o{ ClinicalOrder : preferred_room
    ClinicalOrder ||--o{ ClinicalOrderEvent : history
    ClinicalOrder ||--o{ ClinicalQueueEntry : returns
    Room ||--o{ ClinicalQueueEntry : queue
    Staff o|--o{ ClinicalQueueEntry : accepts
    ClinicalOrder ||--o{ ResultVersion : versions
    ResultVersion o|--o| ResultVersion : corrects
    Service ||--o{ ServiceAnalyte : template
    ServiceAnalyte o|--o{ ResultValue : source
    ResultVersion ||--o{ ResultValue : measures
    ResultVersion ||--o{ ResultAttachment : attaches
    FileObject ||--o{ ResultAttachment : file
    ResultVersion ||--o{ ClinicalReview : reviewed
    Encounter ||--o{ ClinicalReview : assessment
    Encounter ||--o{ Prescription : prescription
    Prescription ||--|{ PrescriptionItem : drugs
    Drug ||--o{ PrescriptionItem : catalog
```

Draft Prescription có thể rỗng, ISSUED phải có line. Chuỗi đính chính tuyến tính: một published version có tối đa một successor; successor nháp không thay current published version. Snapshot ghi nhận tiền sử, thuốc, chỉ số và giá để không phụ thuộc danh mục tương lai.

## Thanh toán và vận hành

```mermaid
erDiagram
    Encounter o|--o{ Invoice : encounter_invoice
    BookingHold o|--o| Invoice : prepayment_invoice
    Invoice ||--o{ InvoiceLine : lines
    Service ||--o{ InvoiceLine : charged_service
    ServicePrice ||--o{ InvoiceLine : price_source
    Invoice ||--o{ PaymentRequest : qr_attempts
    PaymentRequest o|--o{ PaymentEvent : callbacks
    PaymentRequest o|--o{ Payment : actual_receipts
    Payment ||--o{ PaymentAllocation : applied
    InvoiceLine ||--o{ PaymentAllocation : paid

    Payment ||--o{ RefundRequest : source_money
    PaymentAllocation o|--o{ RefundRequest : allocated_money

    RefundRequest ||--o{ Refund : disbursements
    Encounter ||--o{ VideoSession : sessions
    VideoSession ||--o{ VideoParticipation : connections
    Account ||--o{ VideoParticipation : joins
    Account o|--o{ CommandReceipt : idempotent_commands
    FileObject o|--o{ PaymentRequest : qr_image
    FileObject o|--o{ Refund : evidence
```

Invoice dùng Encounter **hoặc** BookingHold, không cả hai. Hóa đơn online luôn giữ hold_id kể cả sau khi có Encounter; báo cáo truy ngược qua Appointment.hold_id. Không sửa FK hóa đơn sau phát hành.

Các FK actor tới Account và bảng SystemSetting/AuditEvent không vẽ hết để tránh che luồng nghiệp vụ. Chúng được định nghĩa ở tài liệu 03.
