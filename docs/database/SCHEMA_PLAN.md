
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

Tổng cộng thiết kế V1–V11: 58 bảng nghiệp vụ. V13 thêm AuthToken; V17 thêm Allergen và liên kết PatientAllergy → Allergen: hiện 60 bảng nghiệp vụ (không tính flyway_schema_history và sysdiagrams của SSMS). Allergen có mã/tên duy nhất, ngừng áp dụng thay xóa; PatientAllergy giữ tên lúc ghi nhận, hỗ trợ nhiều chất và ghi chú bắt buộc khi chưa có trong danh mục. Migration không tự đối chiếu tên tự do cũ hoặc sửa snapshot Encounter đã chốt.

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

## Bổ sung V18 — DiagnosisConcept (2026-11-25)

Nguồn dữ liệu: phụ lục `06-byt-kem.pdf` tại `https://datafiles.chinhphu.vn/cpp/files/vbpq/2026/4/06-byt-kem.pdf`, 1.271 trang, SHA256 `8639f5eeb77b571363dc841923095895d2498748f9bc6620f50710a6da9159e2`. JSON giữ 15.844 dòng, trong đó 12.878 dòng được phép chọn theo các cột nguồn đã trích. Đây không phải bằng chứng đối chiếu thủ công toàn bộ mã/quy tắc ICD.

Ngoại lệ nguồn: dòng 15.750, trang 1.258 in mã `U13/9` nhưng tên chứa `U13.9`. Giữ nguyên mã in trong nguồn, lưu `sourceIssue` trong JSON và khóa cả `selectable`/`primaryAllowed`; không tự sửa hoặc tự ánh xạ mã này. Mã không đủ chi tiết/chỉ dùng thống kê tử vong và cờ không được làm bệnh chính được lấy từ các cột tương ứng của phụ lục, không suy từ tên bệnh.

Tái tạo từ thư mục `backend/`: chạy `python scripts/prepare_icd_catalog.py` (cần `pypdf`), đọc lại toàn bộ PDF; script không đọc cache `target/icd-source/rows.json`. So sánh JSON mới với bản đã rà trước khi import, đặc biệt A99, X60 và ngoại lệ U13/9. Thử `powershell -File scripts/Import-DiagnosisCatalog.ps1 -Database SuncareClinicMigrationTest -Rollback` trước khi nhập thật. Import chạy transaction SERIALIZABLE, từ chối xung đột thay vì ghi đè, không tự bật mã inactive; mỗi dòng mới có audit SYSTEM kèm URL/hash nguồn. Không sửa V18 đã áp dụng.

Thêm 1 bảng DiagnosisConcept sau V17 Allergen, tổng dự kiến 61 bảng nghiệp vụ (58 nền + AuthToken + Allergen + DiagnosisConcept; không tính flyway_schema_history/sysdiagrams). Thêm FK nullable và snapshot phiên bản/tên trên Diagnosis; không tự ánh xạ legacy. Dữ liệu ICD chính thức nằm ở scripts/data/icd10-vn-2026.json, import riêng có audit bằng Import-DiagnosisCatalog.ps1; không nhúng dữ liệu vào migration. Kiểm chứng hiện hành ghi tại PROGRESS.md; danh sách 58 bảng ở phần nền phía trên là mốc ban đầu.

## Bổ sung V19 — Hỏi bệnh theo lượt khám (2026-11-26)

Theo chấp thuận của chủ dự án, bổ sung tối thiểu `Encounter.history_of_present_illness` (NVARCHAR(MAX), API tối đa 20.000 ký tự), bảng `Symptom` (id, name NVARCHAR(200), normalized_name tính toán, is_active, created_at) và `EncounterSymptom` (encounter_id, symptom_id nullable, name_snapshot, normalized_name, display_order). Tổng sau V19: 63 bảng nghiệp vụ; V1–V18 giữ nguyên.

FK triệu chứng nullable phân biệt nội dung tự nhập theo lượt khám với danh mục dùng chung. Unique tên chuẩn hóa và thứ tự trong từng Encounter; ứng dụng chống trùng không phân biệt hoa/thường/dấu tiếng Việt/khoảng trắng. Tên danh mục được chốt từ server, giữ snapshot đã ghi nhận khi danh mục đổi tên/ngừng hoạt động. Không seed triệu chứng giả, không tạo API ghi danh mục; dữ liệu danh mục cần nguồn được duyệt riêng.

`GET /api/doctor/symptoms?search=` chỉ DOCTOR, từ khóa tối đa 100 ký tự, rỗng trả [], tối đa 20 gợi ý active, tìm không phân biệt dấu và xử lý đ/d. `PUT /api/doctor/encounters/{id}/clinical/notes` hiện có nhận thêm `historyOfPresentIllness` và `symptoms: [{symptomId, name}]` (tối đa 50, tên tối đa 200). Bỏ qua/null field mới giữ dữ liệu cũ để tương thích; chuỗi rỗng/danh sách [] xóa nội dung tương ứng. Không ánh xạ sang examination_note hay PrevisitResponse.

Thay thế danh sách triệu chứng, cập nhật ghi chú và audit ENCOUNTER_NOTES_UPDATED trong cùng transaction, khóa Appointment → Encounter và kiểm tra rowversion hiện có. Giữ phân quyền bác sĩ phụ trách/trạng thái IN_PROGRESS, không đổi vòng đời Encounter/Invoice/Prescription. V19 đã kiểm chứng trên SuncareClinicMigrationTest; chưa áp dụng DB chính trong phiên này. Bằng chứng kiểm thử và giới hạn ghi tại PROGRESS.md.

## Bổ sung V20 — seed triệu chứng cơ năng ngoại trú (2026-11-26)

V20__seed_outpatient_symptom_catalog.sql bổ sung 250 tên triệu chứng/lời than phiền thường dùng, Unicode N'...', bao phủ toàn thân20, hô hấp20, tim mạch18, tiêu hóa34, thần kinh22, cơ xương khớp22, tiết niệu20, da liễu16, tai mũi họng24, mắt18, nội tiết12, sản phụ khoa24. Nhóm chỉ là comment để rà nội dung; không thêm bảng/cột phân nhóm. Đây là danh mục ứng dụng tự biên soạn theo phạm vi chủ dự án, không phải bộ mã triệu chứng chính thức hoặc công cụ chẩn đoán. Không chứa tên bệnh/chẩn đoán, giá trị xét nghiệm hay dấu hiệu khám thực thể; mục như sốt, nước tiểu đục, tiết dịch được hiểu là người bệnh khai báo, không thay kết quả khám.

Tham khảo cách phân biệt triệu chứng với khám: [MSD chuyên gia – Khó thở](https://www.msdmanuals.com/vi/professional/rối-loạn-chức-năng-hô-hấp/triệu-chứng-của-các-bệnh-lý-hô-hấp/khó-thở), [MSD chuyên gia – Đánh giá bệnh nhân có bệnh lý tiêu hóa](https://www.msdmanuals.com/vi/professional/rối-loạn-tiêu-hóa/tiếp-cận-bệnh-nhân-có-bệnh-lý-tiêu-hóa/đánh-giá-bệnh-nhân-có-bệnh-lý-tiêu-hóa). Không nhập tên bệnh từ các bảng chẩn đoán phân biệt trong tài liệu.

V19 suy ra normalized_name NVARCHAR(4000)/8.000byte do REPLACE, gây cảnh báo vượt1.700byte của nonclustered index. V20 drop/recreate computed column/index trong transaction migration, giữ nguyên biểu thức LOWER/REPLACE đ→d/collation Latin1_General_100_CI_AI nhưng CONVERT NVARCHAR(200), giới hạn400byte, không cắt tên hợp lệ vì name cũng NVARCHAR(200). Không chỉnh V19, không đổi name/id/activity/created_at hoặc snapshot EncounterSymptom. [Microsoft – giới hạn index](https://learn.microsoft.com/en-us/sql/t-sql/statements/create-index-transact-sql?view=sql-server-ver17).

CTE VALUES + INSERT WHERE NOT EXISTS (UPDLOCK,HOLDLOCK) so sánh bằng normalized_name/cùng collation để bỏ qua mục đã có, kể cả khác dấu/hoa thường; không tự kích hoạt lại mục inactive. Danh mục được rà trùng bằng chuẩn bỏ dấu/đ/hoa thường và unique index SQL. Đổi cách diễn đạt “Đau cổ” thành “Đau vùng cổ” để không trùng khóa không dấu với “Đau cơ”. Searchable multi-select/API và cơ chế Enter tự nhập giữ nguyên; triệu chứng ngoài danh mục chỉ thuộc lượt khám. Tổng bảng vẫn63.

### Chạy có chủ đích trên DB chính

V20 phải được kiểm chứng migrationtest trước. Từ backend/, dùng Java21 và JDBC native auth DLL đã cấu hình, chạy PowerShell:

```powershell
$env:JAVA_HOME='C:/Users/ADMIN/.jdks/openjdk-21.0.2'
$env:Path="$env:JAVA_HOME/bin;D:/Nam_4/Project_1/jdbc-auth/13.4.0;"+$env:Path
.\mvnw.cmd '-Dspring-boot.run.profiles=migrationmain' '-Dspring-boot.run.arguments=--server.port=8082' spring-boot:run
```

Kiểm tra profile migrationmain trỏ đúng SuncareClinic trước khi chạy;8082 để không chiếm API8080 đang mở. Flyway áp dụng mọi version còn thiếu theo thứ tự (main phiên trước ởV18 nên chạy cảV19 rồiV20), không reset/clean/baseline/repair. Đợi log migrate thành công và ứng dụng khởi động, Ctrl+C dừng phiên migration rồi khởi động API theo cấu hình thường (Flyway mặc định tắt). Không chạy đồng thời nhiều instance đang migrate. Không tự sửa version/checksum trong flyway_schema_history.

Sau đó truy vấn dbo.flyway_schema_history: V20 success=1; sys.columns normalized_name max_length=400; dbo.Symptom có các mục Ho/Sốt/Đau bụng, không nhóm normalized_name trùng. Tổng250 là kết quả DB thử ban đầu rỗng, không ép số dòng chính bằng250 nếu có dữ liệu trước đó. API doctor search ho/sot/đau/dau bung phải trả kết quả thật; giữ giới hạn20 hiện có. Kết quả thực chạy ở PROGRESS.md; lệnh trên là hướng dẫn, không phải bằng chứng đã migrate main.

Hậu kiểm phiênV20: cả test và main hiệnV20,250 triệu chứng, normalized_name400byte,0 khóa trùng, checksum V19/V20 khớp. Agent chỉ chạy Maven profile migrationtest để kiểm thử; main được tiến trình migrationmain+DevTools đã có áp dụng lúc resource mới xuất hiện. Không chạy lại lệnh migrationmain nếu đãV20; chuyển ứng dụng về profile thường sau migration để tránh tự áp dụng version mới trong lúc phát triển. Chi tiết thời điểm và bằng chứng tại PROGRESS.md.
