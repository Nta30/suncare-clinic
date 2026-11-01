
# SUNCARE CLINIC — SQL SCHEMA PLAN

## 1. Thông tin chung

- Database: SuncareClinic
- DBMS: Microsoft SQL Server 2025
- Server: DESKTOP-4FOOJD8
- Backend: Spring Boot, Java 21, Maven
- Ngày lập: 2026-11-01
- Trạng thái: Thiết kế, chưa triển khai SQL

## 2. Tài liệu nguồn

1. 01-nghiep-vu.md
2. 03-tu-dien-du-lieu.md
3. 04-trang-thai-phan-quyen.md
4. docs/PROJECT_STATE.md

Tài liệu 03 là nguồn xác định bảng, cột,
kiểu dữ liệu và các quan hệ.

Không tự ý thêm bảng hoặc cột nghiệp vụ
ngoài thiết kế đã được chốt.

## 3. Danh sách bảng

### A. Tài khoản và tổ chức (14)

- Account
- Patient
- Staff
- Role
- Permission
- AccountRole
- RolePermission
- Department
- Specialty
- DepartmentSpecialty
- StaffDepartment
- StaffSpecialty
- DepartmentHeadAssignment
- Room

### B. Danh mục và lịch làm việc (11)

- Service
- RoomService
- ServicePrice
- ShiftDefinition
- WorkSchedule
- WorkAssignment
- OnlineSlot
- SlotReservation
- BookingHold
- Appointment
- AppointmentEvent

### C. Khám và hồ sơ (9)

- Encounter
- PatientHistory
- PatientAllergy
- EncounterHistorySnapshot
- VitalSign
- PrevisitResponse
- Diagnosis
- ClinicalReview
- EncounterEvent

### D. Chỉ định và kết quả CLS (7)

- ClinicalOrder
- ClinicalQueueEntry
- ClinicalOrderEvent
- ResultVersion
- ServiceAnalyte
- ResultValue
- ResultAttachment

### E. Đơn thuốc (3)

- Drug
- Prescription
- PrescriptionItem

### F. Hóa đơn và thanh toán (8)

- Invoice
- InvoiceLine
- PaymentRequest
- PaymentEvent
- Payment
- PaymentAllocation
- RefundRequest
- Refund

### G. Vận hành (6)

- VideoSession
- VideoParticipation
- FileObject
- SystemSetting
- CommandReceipt
- AuditEvent

Tổng cộng: 58 bảng nghiệp vụ.

Không tạo bảng EMR riêng để sao chép
dữ liệu Encounter.

Không tạo bảng Discount, CreditNote,
quản lý kho thuốc hoặc BHYT.

## 4. Quy ước SQL

### Khóa chính

- bigint IDENTITY cho các bảng thông thường.
- Bảng nối sử dụng khóa ghép khi tài liệu
  quy định rõ.
- ID không thay đổi hoặc tái sử dụng.

### Khóa ngoại

- FK sử dụng bigint khi tham chiếu ID.
- Tuân thủ quan hệ trong từ điển dữ liệu.
- Không sao chép mã định danh vào snapshot.
- Account tham chiếu Patient hoặc Staff,
  không lưu Account ID ngược trong hồ sơ.

### Kiểu dữ liệu

- Tiền VND: decimal(19,0)
- Số lượng: decimal(12,3)
- Số đo: decimal(18,6)
- Ngày: date
- Ngày giờ UTC: datetime2(3)
- Văn bản Unicode: nvarchar
- Status: varchar(32)
- Mã nghiệp vụ: varchar(40)
- Chống lost update: rowversion

### Ràng buộc

- PRIMARY KEY
- FOREIGN KEY
- UNIQUE
- Filtered UNIQUE INDEX khi được yêu cầu
- CHECK theo miền giá trị nghiệp vụ
- NOT NULL theo từ điển
- DEFAULT cho các trường thích hợp

Không dùng float để lưu tiền.

Không dùng ordinal enum để lưu trạng thái.

Không tự thêm ON DELETE CASCADE.
Chỉ sử dụng khi đã xác minh an toàn nghiệp vụ.

## 5. Các ràng buộc đặc biệt

### Account

Mỗi Account thuộc đúng một hồ sơ
Patient hoặc Staff.

Account.patient_id và Account.staff_id
phải thỏa mãn XOR theo account_type.

### Organization

Một nhân viên chỉ có một khoa chính
tại cùng thời điểm.

Các khoảng phân công không được giao nhau
nếu nghiệp vụ không cho phép.

### Scheduling

Không cho phép trùng phân công
bác sĩ hoặc phòng.

OnlineSlot có thời lượng 20 phút.

SlotReservation bảo vệ quyền chiếm slot.

BookingHold có thời hạn 5 phút.

Không đặt UNIQUE vô điều kiện trên
Appointment.online_slot_id.

### Clinical

Một Encounter thuộc một Appointment.

ClinicalOrder thuộc một Encounter
và một dịch vụ.

ResultVersion bảo toàn các bản đã phát hành.

ResultAttachment nối FileObject
với ResultVersion.

### Billing

Tiền sử dụng decimal(19,0).

InvoiceLine giữ nguyên số tiền gốc
sau khi hủy.

PaymentAllocation bảo toàn phân bổ tiền.

RefundRequest và Refund lưu riêng.

Không tạo bảng giảm giá.

Không ghi nhận thanh toán giả khi
giao dịch thất bại.

### Audit

AuditEvent lưu thông tin kiểm toán
không chứa mật khẩu hoặc token.

Các event lịch sử phải được bảo toàn.

## 6. Nguyên tắc thiết kế Migration

- Sử dụng SQL migration có quản lý phiên bản.
- Chỉ áp dụng lên database SuncareClinic.
- Migration phải có thứ tự phụ thuộc rõ ràng.
- Bảng được tham chiếu phải tồn tại trước
  khi tạo FOREIGN KEY tương ứng.
- Không chạy migration chưa được kiểm tra.
- Không tự động xóa bảng hoặc dữ liệu.
- Không sử dụng Hibernate tự tạo schema
  thay cho migration đã kiểm soát.
- Kiểm tra migration trên database rỗng.
- Lưu bằng chứng sau khi áp dụng thành công.

Thứ tự triển khai logic:

1. Hồ sơ, tài khoản và danh mục nền tảng.
2. Quan hệ tổ chức, dịch vụ và lịch làm việc.
3. BookingHold, Appointment và Encounter.
4. ClinicalOrder, kết quả và đơn thuốc.
5. Invoice, Payment và Refund.
6. Các thành phần vận hành, audit và ràng buộc
   còn phụ thuộc.

FileObject phải được tạo trước các bảng
cần tham chiếu đến nó.

Đây là thứ tự logic, chưa phải thứ tự
CREATE TABLE cuối cùng.

## 7. Những việc cần xác minh trước khi viết SQL

- Phiên bản Spring Boot chính xác.
- Công cụ migration và dependencies tương thích.
- Schema vật lý và cách đặt tên migration.
- Toàn bộ miền trạng thái theo tài liệu 04.
- Thứ tự tạo bảng theo FK thực tế.
- Các UNIQUE INDEX có điều kiện.
- Những ràng buộc cần transaction ở Backend.
- Chiến lược khởi tạo tài khoản quản trị.

Không tự suy đoán dữ liệu cấu hình
ca làm việc, giá dịch vụ hoặc tài khoản.

## 8. Trạng thái thực hiện

- [x] Đọc tài liệu nghiệp vụ.
- [x] Đọc từ điển dữ liệu.
- [x] Kiểm tra cấu trúc Backend.
- [x] Xác nhận API đã có.
- [x] Lập danh mục 58 bảng.
- [ ] Xác minh dependencies migration.
- [ ] Viết SQL migration.
- [ ] Kiểm tra cú pháp SQL.
- [ ] Áp dụng migration.
- [ ] Kiểm tra PK, FK, UNIQUE, CHECK.
- [ ] Kiểm tra dữ liệu và transaction.
- [ ] Cập nhật PROJECT_STATE.md.

Không đánh dấu các hạng mục chưa kiểm thử
là hoàn thành.
