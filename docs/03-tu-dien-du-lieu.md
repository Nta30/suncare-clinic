# 03 — Từ điển dữ liệu

## Quy ước dùng chung

Mỗi bảng có `id bigint IDENTITY PK` trừ bảng nối có khóa ghép được ghi rõ. Tất cả cột dưới đây **NOT NULL**, trừ hậu tố `?` là NULL. `→ Table` là FK tới `Table.id`; `UQ` là duy nhất; các cột không ghi type của FK dùng bigint. ID không thay đổi; `code` dùng varchar(40), mã nghiệp vụ UQ, không sửa và không tái sử dụng. Không sao chép mã định danh vào cột snapshot ở bảng liên quan, dùng FK để truy xuất. Giữ snapshot nội dung có thể thay đổi để bảo toàn chứng từ. Status dùng varchar(32), miền giá trị tại tài liệu 04, CHECK ở SQL Server; không dùng ordinal enum.

Mọi bảng có `created_at datetime2(3)` UTC mặc định lúc tạo. Bảng có dữ liệu được cập nhật có `updated_at datetime2(3)`, `version rowversion` để chống cập nhật mất dữ liệu. Bảng snapshot/giao dịch/event bất biến không cần updated_at/version; chốt phân loại khi tạo migration. Người tạo/sửa quan trọng được ghi bằng FK Account hoặc AuditEvent; thao tác SYSTEM có actor NULL và actor_type=SYSTEM. Không lưu mật khẩu, token hoặc chữ ký bí mật trong audit.

Tiền `decimal(19,0)` VND; số lượng `decimal(12,3)`; số đo `decimal(18,6)`; thời gian `datetime2(3)` UTC; ngày sinh/ngày làm việc `date`; chuỗi hiển thị Unicode `nvarchar`. Điện thoại/email không phải định danh bệnh nhân duy nhất. Không unique tên, ngày sinh hay số điện thoại. CCCD không bắt buộc.

## A. Tài khoản và tổ chức

| Bảng | Cột nghiệp vụ và ràng buộc |
|---|---|
| Account | login_name nvarchar(100) UQ chuẩn hóa; password_hash varchar(255); account_type PATIENT/STAFF; patient_id? → Patient UQ filtered; staff_id? → Staff UQ filtered; status ACTIVE/LOCKED; last_login_at? datetime2(3). CHECK XOR phù hợp account_type. |
| Patient | code UQ; full_name nvarchar(150); avatar_url? nvarchar(2048); avatar_public_id? nvarchar(255); birth_date? date; sex varchar(16) UNKNOWN/MALE/FEMALE/OTHER; phone? varchar(20); email? nvarchar(254); address? nvarchar(500); identity_number? varchar(30); emergency_name? nvarchar(150); emergency_phone? varchar(20); is_active bit. Ảnh upload Cloudinary, DB lưu link và public_id để thay/xóa có kiểm soát. |
| Staff | code UQ; full_name nvarchar(150); avatar_url? nvarchar(2048); avatar_public_id? nvarchar(255); birth_date? date; sex varchar(16); phone? varchar(20); email? nvarchar(254); address? nvarchar(500); professional_license? nvarchar(100); qualification? nvarchar(150); is_active bit. Ảnh bác sĩ dùng các cột avatar; áp dụng chung cho staff. Vai trò đăng nhập không thay bằng qualification. |
| Role | code UQ: PATIENT, DOCTOR, LAB_STAFF, DEPARTMENT_HEAD, CASHIER, SYSTEM_ADMIN; name nvarchar(100); account_type PATIENT/STAFF. `DEPARTMENT_HEAD` là quyền bổ sung và chỉ cấp cùng `DOCTOR`; phân công trưởng khoa giữ khoa/phạm vi/thời hạn, không thay vai trò. Clinical Staff là khái niệm chung, không tự cấp quyền khám. |
| Permission | code UQ; description nvarchar(300). |
| AccountRole | account_id → Account; role_id → Role; PK(account_id,role_id); granted_by? → Account; granted_at datetime2(3). NULL khi bệnh nhân tự đăng ký hoặc bootstrap admin; admin gán role staff thì lưu ID admin. Role phải khớp account_type. V12 đổi cột thành nullable và giữ FK. |
| RolePermission | role_id → Role; permission_id → Permission; PK(role_id,permission_id). |
| Department | code UQ; name nvarchar(150); department_type CLINICAL/LAB; is_active bit. |
| Specialty | code UQ; name nvarchar(150); is_active bit. |
| DepartmentSpecialty | department_id → Department; specialty_id → Specialty; PK(department_id,specialty_id); is_active bit để ngừng liên kết mà không xóa lịch sử; updated_at, version (V14). |
| StaffDepartment | staff_id → Staff; department_id → Department; valid_from datetime2(3); valid_to? datetime2(3); is_primary bit mặc định 1. Không giao khoảng cho cùng staff. |
| StaffSpecialty | staff_id → Staff; specialty_id → Specialty; PK(staff_id,specialty_id); is_active bit. |
| DepartmentHeadAssignment | staff_id → Staff; department_id → Department; valid_from datetime2(3); valid_to? datetime2(3); assigned_by → Account. Chỉ gán Staff có role `DOCTOR` và `DEPARTMENT_HEAD`; xác định phạm vi quyền theo khoa/thời hạn, không suy từ Role đơn thuần. |
| Room | Hiện trạng V2: code UQ; department_id → Department; name nvarchar(150); room_type CONSULTATION/LAB; is_active bit. Quyết định D24 yêu cầu bổ sung loại ONLINE bằng migration mới; phòng ONLINE được quản lý như tài nguyên phòng, gắn khoa và chống giao lịch. Một phòng khám trực tiếp chỉ một bác sĩ tại một thời điểm; phòng lab được nhiều staff. |

Account tạo sau Patient/Staff trong cùng transaction, tránh FK vòng. Không lưu account_id ngược trong hai bảng hồ sơ. Hồ sơ có thể tồn tại chưa cấp tài khoản nhưng mọi Account phải có một hồ sơ. Với đăng ký bệnh nhân, tạo cả hai nguyên tử.

## B. Danh mục và lịch làm việc

| Bảng | Cột nghiệp vụ và ràng buộc |
|---|---|
| Service | code UQ; name nvarchar(200); service_type EXAMINATION/CLS; result_type? LAB/IMAGING; specialty_id? → Specialty; is_active bit. EXAMINATION không có result_type. |
| RoomService | room_id → Room; service_id → Service; PK(room_id,service_id); is_active bit. Chỉ dịch vụ CLS. |
| ServicePrice | service_id → Service; doctor_id? → Staff; visit_mode? ONSITE/ONLINE; amount decimal(19,0)>0; valid_from datetime2(3); valid_to? datetime2(3); created_by → Account. EXAMINATION bắt buộc doctor/mode; CLS không doctor/mode; cùng tổ hợp không giao thời gian hiệu lực. Chưa có dịch vụ miễn phí trong v1. |
| ShiftDefinition | code UQ MORNING/AFTERNOON; name nvarchar(100); local_start time(0); local_end time(0); is_active bit. End > start, cùng ngày. |
| WorkSchedule | department_id → Department; period_start date; period_end date; status DRAFT/PUBLISHED; published_at? datetime2(3); published_by? → Account. |
| WorkAssignment | schedule_id → WorkSchedule; staff_id → Staff; room_id? → Room; specialty_id? → Specialty; work_date date; shift_id? → ShiftDefinition; mode ONSITE/ONLINE/LAB; starts_at datetime2(3); ends_at datetime2(3); capacity? int. Hiện trạng V3: ONSITE bắt buộc room/shift/specialty/capacity>0; ONLINE bắt buộc specialty, `room_id IS NULL`, không shift/capacity; LAB bắt buộc room, không capacity. Theo D24, ONLINE phải tham chiếu Room loại ONLINE; sửa CHECK bằng migration mới trước khi ghi lịch online. |
| OnlineSlot | assignment_id → WorkAssignment; starts_at datetime2(3); ends_at datetime2(3); is_open bit; UQ(assignment_id,starts_at). Chính xác 20 phút, nằm trong assignment ONLINE. Không lưu cờ booked độc lập. |
| SlotReservation | slot_id → OnlineSlot; hold_id → BookingHold UQ; UQ(slot_id). Chỉ chứa quyền chiếm slot hiện hành của hold hoặc booking đã xác nhận. Hết hạn/hủy thì xóa row này trong transaction, giữ BookingHold để lưu lịch sử. |
| BookingHold | code UQ; patient_id → Patient; slot_id → OnlineSlot; requested_doctor_id? → Staff; price_id → ServicePrice; quoted_amount decimal(19,0); status ACTIVE/CONFIRMED/EXPIRED; expires_at datetime2(3); confirmed_at? datetime2(3); idempotency_key varchar(100) UQ. Hạn = created_at + 5 phút, không kéo dài bởi retry. |
| Appointment | code UQ; patient_id → Patient; doctor_id → Staff; department_id → Department; specialty_id → Specialty; visit_mode ONSITE/ONLINE; onsite_assignment_id? → WorkAssignment; online_slot_id? → OnlineSlot; hold_id? → BookingHold UQ filtered; price_id → ServicePrice; examination_amount decimal(19,0); status BOOKED/COMPLETED/CANCELLED/NO_SHOW; booked_at datetime2(3); cancelled_at? datetime2(3); cancel_reason? nvarchar(500). XOR: onsite có assignment, online có slot+hold. |
| AppointmentEvent | appointment_id → Appointment; event_type BOOKED/CANCELLED/NO_SHOW/COMPLETED; actor_id? → Account; reason? nvarchar(500); occurred_at datetime2(3). Append-only, không event đổi lịch. |

SlotReservation là bảng khóa chiếm tài nguyên, không là lịch sử. Appointment cũ CANCELLED vẫn tham chiếu OnlineSlot, vì vậy không đặt UQ vô điều kiện trên Appointment.online_slot_id. Filtered UQ slot khi status khác CANCELLED bảo vệ lịch đã xác nhận; SlotReservation bảo vệ cả hold. Khi thanh toán thành công giữ row reservation cho đến khi hủy lịch; hold chuyển CONFIRMED.

## C. Khám và hồ sơ

| Bảng | Cột nghiệp vụ và ràng buộc |
|---|---|
| Encounter | code UQ; appointment_id → Appointment UQ; status PLANNED/IN_PROGRESS/WAITING_CLS/COMPLETED/CANCELLED/NO_SHOW; started_at? datetime2(3); completed_at? datetime2(3); chief_complaint? nvarchar(2000); examination_note? nvarchar(max); treatment_plan? nvarchar(max); followup_note? nvarchar(2000). Patient/doctor/department lấy từ Appointment bất biến. |
| PatientHistory | patient_id → Patient; history_type PERSONAL/FAMILY/SURGERY; description nvarchar(2000); recorded_by → Account; recorded_at datetime2(3); is_active bit. |
| PatientAllergy | patient_id → Patient; substance nvarchar(200); reaction? nvarchar(1000); severity UNKNOWN/MILD/MODERATE/SEVERE; recorded_by → Account; is_active bit. |
| EncounterHistorySnapshot | encounter_id → Encounter; history_text nvarchar(max); allergy_text nvarchar(max); recorded_by → Account; recorded_at datetime2(3). UQ(encounter_id), chốt khi bắt đầu khám để sửa tiền sử sau không đổi hồ sơ cũ. |
| VitalSign | encounter_id → Encounter; measured_at datetime2(3); temperature_c? decimal(5,2); pulse_bpm? smallint; systolic_mmhg? smallint; diastolic_mmhg? smallint; respiratory_rate? smallint; spo2_percent? decimal(5,2); weight_kg? decimal(7,2); height_cm? decimal(6,2); recorded_by → Account. Ít nhất một số đo, CHECK dương và SpO2 ≤100. |
| PrevisitResponse | appointment_id → Appointment UQ; symptoms? nvarchar(2000); current_medications? nvarchar(2000); other_notes? nvarchar(2000); submitted_at datetime2(3); submitted_by → Account. Không xây bảng hỏi động. |
| Diagnosis | encounter_id → Encounter; code_system? varchar(30); diagnosis_code? varchar(30); description nvarchar(1000); diagnosis_type PRELIMINARY/FINAL; is_primary bit; recorded_by → Account. Filtered UQ primary FINAL/encounter; không yêu cầu mã ICD nếu không có bộ danh mục. |
| ClinicalReview | encounter_id → Encounter; result_version_id → ResultVersion; reviewed_by → Account; reviewed_at datetime2(3); assessment nvarchar(2000); UQ(encounter_id,result_version_id). Order của result phải cùng Encounter. Review bản cũ không thay review bản đính chính. |
| EncounterEvent | encounter_id → Encounter; from_status? varchar(32); to_status varchar(32); actor_id? → Account; reason? nvarchar(1000); occurred_at datetime2(3). Append-only. |

Không tạo bảng EMR sao chép. Patient → Appointment → Encounter là đường truy vấn hồ sơ. Chốt quyền xem theo tài liệu 04; tiền sử chỉ sửa bởi bác sĩ trong v1, bệnh nhân tự khai trong PrevisitResponse.

## D. Chỉ định và kết quả CLS

| Bảng | Cột nghiệp vụ và ràng buộc |
|---|---|
| ClinicalOrder | code UQ; encounter_id → Encounter; service_id → Service; performing_department_id → Department; requested_room_id? → Room; ordered_by → Account; indication nvarchar(2000); status PENDING_PAYMENT/READY/QUEUED/ACCEPTED/IN_PROGRESS/COMPLETED/CANCELLED; fully_paid_at? datetime2(3); validity_days? int; expires_at? datetime2(3); started_at? datetime2(3); completed_at? datetime2(3); cancelled_at? datetime2(3); cancel_reason? PATIENT_REQUEST/EXPIRED/CLINICAL_DECISION; cancel_note? nvarchar(1000). Mốc hạn chỉ ghi lần đầu trả đủ, không sửa. |
| ClinicalQueueEntry | order_id → ClinicalOrder; room_id → Room; checked_in_by → Account; checked_in_at datetime2(3); status WAITING/ACCEPTED/STARTED/DEFERRED/EXPIRED/CANCELLED/DONE; accepted_by? → Staff; accepted_at? datetime2(3); closed_at? datetime2(3). Filtered UQ(order_id) với WAITING/ACCEPTED/STARTED. Mỗi lần quay lại là row mới. |
| ClinicalOrderEvent | order_id → ClinicalOrder; from_status? varchar(32); to_status varchar(32); actor_id? → Account; reason? nvarchar(1000); occurred_at datetime2(3). |
| ResultVersion | order_id → ClinicalOrder; version_no int; replaces_version_id? → ResultVersion UQ filtered; status DRAFT/CONFIRMED/PUBLISHED; description? nvarchar(max); conclusion? nvarchar(max); correction_reason? nvarchar(1000); authored_by → Account; confirmed_by? → Account; confirmed_at? datetime2(3); published_by? → Account; published_at? datetime2(3); UQ(order_id,version_no). Bản mới phải cùng Order; một bản nháp hiện hành/order. |
| ServiceAnalyte | service_id → Service; code varchar(40); name nvarchar(150); default_unit? nvarchar(50); reference_text? nvarchar(500); sort_order int; is_active bit; UQ(service_id,code). Danh mục mẫu chỉ số, không tự suy diễn ngưỡng theo tuổi/giới. |
| ResultValue | result_version_id → ResultVersion; line_no int; analyte_id? → ServiceAnalyte; analyte_name nvarchar(150); numeric_value? decimal(18,6); text_value? nvarchar(1000); unit? nvarchar(50); reference_text? nvarchar(500); abnormal_flag? NORMAL/LOW/HIGH/CRITICAL; sort_order int; UQ(result_version_id,line_no). Một trong numeric/text, lưu tên/đơn vị/ngưỡng snapshot; mã chỉ số lấy qua analyte_id nếu thuộc danh mục. |
| ResultAttachment | result_version_id → ResultVersion; file_id → FileObject; PK(result_version_id,file_id). |

ClinicalOrder.status là trạng thái vận hành, không thay trạng thái thanh toán/hoàn. READY có thể chưa đến hoặc đã hoãn, phân biệt bằng lịch sử queue. EXPIRED thể hiện bằng CANCELLED + cancel_reason=EXPIRED. Order COMPLETED nghĩa là đã phát hành kết quả, không đồng nghĩa bác sĩ đã đánh giá. Bản kết quả hiện hành là phiên bản PUBLISHED có version_no lớn nhất; bản nháp đính chính không che bản đã phát hành.

## E. Đơn thuốc

| Bảng | Cột nghiệp vụ và ràng buộc |
|---|---|
| Drug | code UQ; name nvarchar(200); ingredient? nvarchar(300); strength? nvarchar(100); dosage_form? nvarchar(100); unit nvarchar(50); is_active bit. Không tồn kho, không giá bán. |
| Prescription | code UQ; encounter_id → Encounter; status DRAFT/ISSUED; prescribed_by → Account; issued_at? datetime2(3); instruction? nvarchar(2000). Một đơn phát hành/Encounter trong v1; không quy trình đổi đơn sau phát hành. |
| PrescriptionItem | prescription_id → Prescription; line_no int; drug_id → Drug; drug_name nvarchar(200); ingredient? nvarchar(300); strength? nvarchar(100); dosage_form? nvarchar(100); unit nvarchar(50); dose nvarchar(200); route nvarchar(100); frequency nvarchar(200); duration_days int; quantity decimal(12,3); instruction? nvarchar(1000); UQ(prescription_id,line_no). Snapshot đầy đủ, duration/quantity >0. |

## F. Hóa đơn, giao dịch và hoàn tiền

| Bảng | Cột nghiệp vụ và ràng buộc |
|---|---|
| Invoice | code UQ; patient_id → Patient; encounter_id? → Encounter; hold_id? → BookingHold; status DRAFT/ISSUED/VOID; issued_by? → Account; issuer_type STAFF/SYSTEM; issued_at? datetime2(3); patient_name_snapshot nvarchar(150); patient_address_snapshot? nvarchar(500); facility_name_snapshot nvarchar(200); facility_address_snapshot nvarchar(500); total_amount decimal(19,0); currency char(3) VND. XOR encounter/hold. Mã bệnh nhân truy qua patient_id, không snapshot mã định danh. Một hóa đơn có thể PAID theo số dư nhưng vẫn status ISSUED. |
| InvoiceLine | invoice_id → Invoice; line_no int; order_id? → ClinicalOrder UQ filtered; line_type EXAMINATION/CLS; service_id → Service; price_id → ServicePrice; service_name nvarchar(200); unit_name nvarchar(50); quantity decimal(12,3) =1; unit_price decimal(19,0); amount decimal(19,0); status ACTIVE/CANCELLED; cancelled_at? datetime2(3); cancelled_by? → Account; cancel_reason? APPOINTMENT_CANCELLED/ORDER_CANCELLED/ORDER_EXPIRED/BOOKING_EXPIRED; UQ(invoice_id,line_no). CLS bắt buộc order; EXAMINATION không order. Khi hủy chỉ đổi trạng thái và metadata, không sửa giá/nội dung gốc. |
| PaymentRequest | invoice_id → Invoice; provider MOMO; merchant_request_key varchar(100) UQ; amount decimal(19,0); status CREATED/PENDING/SUCCEEDED/FAILED/EXPIRED; expires_at datetime2(3); provider_order_ref? varchar(150) UQ filtered; qr_file_id? → FileObject; checkout_reference? nvarchar(2000). Không lưu private key; URL/token nhạy cảm phải mã hóa hoặc chỉ lưu reference. |
| PaymentEvent | provider MOMO; provider_event_key varchar(200) UQ; payment_request_id? → PaymentRequest; received_at datetime2(3); provider_transaction_ref? varchar(150); verification_status VALID/INVALID/UNKNOWN; processing_status RECEIVED/APPLIED/REJECTED/REVIEW; result_code? varchar(50); payload_hash char(64); redacted_payload? nvarchar(max); processed_at? datetime2(3). Chỉ callback đã kiểm tra mới làm phát sinh tiền. |
| Payment | code UQ; invoice_id → Invoice; payment_request_id? → PaymentRequest; method MOMO/CASH; amount decimal(19,0); currency char(3) VND; status CONFIRMED; paid_at datetime2(3); confirmed_by? → Account; provider_transaction_ref? varchar(150); idempotency_key varchar(100) UQ. Filtered UQ(method,provider_transaction_ref). Chỉ ghi tiền thực thu; thất bại nằm tại request/event. |
| PaymentAllocation | payment_id → Payment; invoice_line_id → InvoiceLine; amount decimal(19,0)>0; UQ(payment_id,invoice_line_id). Bất biến, cùng invoice. Tổng allocation ≤ Payment.amount; phần thu thừa/thu muộn không phân bổ và phải hoàn. |
| RefundRequest | code UQ; payment_id → Payment; allocation_id? → PaymentAllocation; reason LINE_CANCELLED/OVERPAYMENT/LATE_PAYMENT; amount decimal(19,0)>0; status REQUESTED/PROCESSING/COMPLETED; requested_at datetime2(3); requested_by? → Account; idempotency_key varchar(100) UQ. LINE_CANCELLED cần allocation thuộc dòng CANCELLED; thu muộn/thừa dùng payment không cần allocation. Không có bảng giảm giá hay chứng từ đảo khoản thu riêng. |
| Refund | code UQ; request_id → RefundRequest; amount decimal(19,0)>0; refunded_at datetime2(3); processed_by → Account; method CASH/MANUAL_TRANSFER; external_reference? nvarchar(150); evidence_file_id? → FileObject; idempotency_key varchar(100) UQ. Bất biến, nhiều lần chi một request được nhưng không vượt tổng yêu cầu. |

Không lưu trực tiếp refund_status trên Order. Suy từ request và tổng Refund. Phân biệt:

- Net charge = line.amount nếu line.status=ACTIVE, bằng 0 nếu CANCELLED. Tổng gốc hóa đơn luôn giữ nguyên; cancelled_amount là tổng amount của dòng đã hủy, không phải giảm giá.
- Net allocated = tổng PaymentAllocation.amount − tổng Refund.amount của request gắn allocation đó.
- Outstanding = max(0, net charge − net allocated). Dòng hủy có charge=0; số tiền chờ hoàn không trở thành khoản phải thu âm.
- Khi xác định Order được trả đủ lần đầu: tổng allocation đạt nguyên giá dòng ACTIVE; ghi fully_paid_at/expires_at trong transaction. Không dùng net allocated để tự kéo dài hạn sau hoàn tiền.
- Thu tiền thực tế = tổng Payment.amount; hoàn thực tế = tổng Refund.amount; thu thuần = thu − hoàn. Request chưa chi không được trừ doanh thu tiền mặt thực thu.
- Line phí khám duy nhất theo Encounter: online nguồn Invoice.hold_id → Appointment.hold_id → Encounter; trực tiếp Invoice.encounter_id. Backend và transaction bảo vệ không lập thêm phí khám ở hóa đơn bổ sung.

## G. Vận hành

| Bảng | Cột nghiệp vụ và ràng buộc |
|---|---|
| VideoSession | encounter_id → Encounter; provider varchar(50); external_session_ref nvarchar(200); status CREATED/OPEN/CLOSED/FAILED; opened_at? datetime2(3); closed_at? datetime2(3). Chỉ Encounter online; không lưu recording hoặc credential trong URL công khai. |
| VideoParticipation | session_id → VideoSession; account_id → Account; joined_at datetime2(3); left_at? datetime2(3). Mỗi lần kết nối lại là row mới. |
| FileObject | cloudinary_public_id nvarchar(255) UQ; url nvarchar(2048); resource_type IMAGE/VIDEO/RAW; delivery_type nvarchar(30); original_name nvarchar(255); media_type varchar(100); byte_size bigint; sha256? char(64); uploaded_by? → Account; access_class MEDICAL/PAYMENT; status ACTIVE/QUARANTINED. Ảnh CLS upload Cloudinary rồi lưu URL tại đây, nối ResultAttachment với ResultVersion. Không lưu binary trong SQL Server. Ảnh y tế dùng chế độ phân phối hạn chế, không public; URL tải có thời hạn sinh khi đã kiểm tra quyền, không lưu token tạm thời vào cột url. |
| SystemSetting | setting_key varchar(100) UQ; setting_value nvarchar(2000); changed_by → Account. Cho phép danh sách khóa rõ ràng: CLS_VALIDITY_DAYS=10, ONLINE_HOLD_SECONDS=300; không lưu secret. |
| CommandReceipt | account_id? → Account; command_type varchar(60); idempotency_key varchar(100); request_hash char(64); resource_type varchar(60); resource_id bigint; UQ(command_type,idempotency_key). Bất biến, cùng transaction nghiệp vụ; key được scope theo identity server. Generic resource_id không là FK. |
| AuditEvent | actor_id? → Account; actor_type USER/SYSTEM; action varchar(100); entity_type varchar(60); entity_id bigint; occurred_at datetime2(3); correlation_id varchar(100); reason? nvarchar(1000); redacted_change? nvarchar(max). Append-only, generic entity_id không là FK thay thế quan hệ nghiệp vụ. |

Các cột tổng hợp amount/status phải được cập nhật trong cùng transaction và có truy vấn đối soát. Rowversion là số phiên bản nhị phân SQL Server, không phải timestamp thời gian. Cấu hình hiệu lực được chốt trên nghiệp vụ nên sửa setting không đổi dữ liệu cũ.
