# 05 — Ràng buộc, giao dịch và chỉ mục

## Bảo vệ dữ liệu tại SQL Server

| Cơ chế | Áp dụng |
|---|---|
| PK/FK | Tất cả quan hệ trong từ điển; FK chứng từ/khám không ON DELETE CASCADE. Bảng nối có thể xóa có kiểm soát khi thu hồi quyền. |
| CHECK | Account XOR Patient/Staff theo type; Invoice XOR Encounter/Hold; Appointment XOR onsite/online; thời gian end>start; amount>0, quantity>0; numeric/text của ResultValue XOR; status nằm trong miền; mốc nullable nhất quán. |
| UNIQUE | Code; login_name chuẩn hóa; idempotency key; provider transaction; Encounter.appointment_id; InvoiceLine.order_id filtered; số phiên bản kết quả; line_no theo chứng từ. |
| Filtered UNIQUE | Account.patient_id/staff_id khác NULL; Invoice.hold_id khác NULL; một Appointment không CANCELLED/slot; một queue đang WAITING/ACCEPTED/STARTED/order; một result DRAFT hoặc CONFIRMED/order; một FINAL primary diagnosis/Encounter; một ISSUED prescription/Encounter. Các bộ trạng thái nhiều giá trị có thể cần cột active nullable được transaction duy trì nếu filter DDL không hỗ trợ biểu thức mong muốn. |
| Backend + transaction | Role/type khớp; phạm vi khoa; chống giao khoảng lịch/giá/phân công; sức chứa; đúng Patient/Encounter; tổng tiền; một phí khám/Encounter; một kết quả successor; chuyển trạng thái; điều kiện hoàn tất. |
| Bất biến | Không UPDATE/DELETE Payment, Allocation, Refund, Event; Invoice/line đã phát hành giữ nguyên nội dung và số tiền; chỉ cho chuyển dòng ACTIVE sang CANCELLED kèm metadata hủy và audit. Result/đơn đã phát hành chỉ đọc. Tài khoản ứng dụng không có quyền sửa ngoài lối ghi được kiểm soát. |

Không dùng CHECK để kiểm tra bảng khác hoặc ngày hiện tại; không dùng UNIQUE đơn thuần để chống giao khoảng. Chặn hard delete lịch sử, dùng is_active cho danh mục. FK nullable dùng filtered UNIQUE để tránh giới hạn NULL của UNIQUE SQL Server.

## Thứ tự khóa và nguyên tắc chung

SQL Server READ COMMITTED; dùng transaction ngắn và UPDLOCK/HOLDLOCK trên hàng tài nguyên, không chỉ đọc rồi kiểm tra ở Java. Khóa phạm vi có thể dùng `sp_getapplock` theo tài nguyên nghiệp vụ, transaction-owned. Khi nhiều tài nguyên cùng loại, khóa theo ID tăng dần.

Thứ tự logic: **phạm vi lịch/chuyên khoa-ngày → assignment/slot/hold → Appointment/Encounter → ClinicalOrder → Invoice/line → Payment/RefundRequest**. Mọi command phải gom đủ ID trước khi khóa và theo cùng thứ tự; không callback thanh toán khóa Invoice trước Order trong khi expiry làm ngược lại. Không giữ transaction trong lúc gọi MoMo, tạo PDF, upload hoặc xuất CSV.

- Retry deadlock hữu hạn với cùng idempotency key, không tạo nghiệp vụ lần hai.
- Lỗi giữa các bước rollback toàn bộ dữ liệu cục bộ. External API không rollback cùng DB; cần retry/đối soát dựa trên PaymentRequest/Event đã lưu.
- Không tin số tiền, doctor_id phân tự động, patient_id hoặc status do client gửi.
- Trước mỗi thao tác cần hạn, so sánh UTC hiện tại từ cùng nguồn DB; không dựa vào job đã kịp cập nhật status hay chưa.

## Transaction theo use case

### TX01 — Đăng ký và cấp tài khoản

Đăng ký công khai chỉ nhận dữ liệu Patient; server cố định account_type=PATIENT và role PATIENT. Tạo Patient, Account và AccountRole nguyên tử. Admin cấp Staff tương tự với role STAFF; kiểm tra quyền admin trước ghi. Không cho API cập nhật hồ sơ thay account_type hoặc liên kết sang nhóm khác.

### TX02 — Công bố và thay cấu hình lịch

Khóa staff/ngày và room/ngày liên quan; kiểm tra lịch giao nhau bằng `a.start < b.end AND b.start < a.end`. Room CONSULTATION không hai bác sĩ đồng thời; LAB cho nhiều nhân viên cùng phòng nhưng không một người hai nơi. Không giao assignment ONLINE/ONSITE/LAB của cùng staff. ONLINE chỉ sinh các slot 20 phút trọn vẹn trong khoảng làm; dư cuối khoảng không mở. Assignment ONSITE chiếm toàn ca và sức chứa >0. Kiểm tra vai trò/chuyên khoa/khoa/phòng và giá hiệu lực trước PUBLISHED.

Nếu đã có Appointment hoặc hold ACTIVE, chặn sửa khoảng giờ, mode, staff, specialty hoặc giảm capacity dưới lượt đã đặt. Không tự điều chuyển người bệnh. Thay danh mục chỉ ảnh hưởng lần tạo mới; bản cũ giữ ID/snapshot.

### TX03 — Đặt trực tiếp và phân bác sĩ

Khóa chuyên khoa/ngày/ca để serialize việc cân bằng; đọc assignment đã công bố thuộc ca, bác sĩ active/có giá và còn capacity. Khóa thêm assignment được chọn để bảo vệ sức chứa khi một assignment phục vụ nhiều chuyên khoa. Chỉ tính Appointment BOOKED/COMPLETED/NO_SHOW vào số lượt, không Encounter hoặc Invoice join. So sánh tỷ lệ bằng nhân chéo decimal tránh chia nguyên; hòa theo Staff.code. Tạo Appointment và Encounter PLANNED, event và audit cùng transaction. Retry request dùng CommandReceipt, không dùng Audit correlation thay khóa idempotency và không dựa vào double-click UI.

### TX04 — Giữ slot online

Khóa chuyên khoa/ngày/giờ khi tự phân. Bác sĩ được chọn phải có slot công bố, đủ chuyên khoa và giá. Nếu không chọn, đếm Appointment online không hủy trong ngày Việt Nam và chọn người ít nhất, hòa mã. Loại slot đã có SlotReservation hợp lệ; hold ACTIVE quá hạn được expire dưới khóa trước khi tái cấp.

Tạo BookingHold ACTIVE với giá snapshot, SlotReservation (UQ slot), Invoice phí khám ISSUED và line. Chưa có Appointment/Encounter. Commit rồi tạo yêu cầu MoMo từ PaymentRequest bền vững; retry dùng merchant_request_key không tạo yêu cầu thu khác. Nếu không tạo được QR vẫn giữ hạn gốc 5 phút; không tự kéo dài.

### TX05 — Callback/đối soát MoMo và thu tiền mặt

1. Xác minh theo tài liệu chính thức nhà cung cấp trước khi coi là thành công: chữ ký, merchant, request/order, currency, amount, trạng thái; không tin redirect frontend. Lưu event đã lược bí mật; invalid/unknown không tạo Payment.
2. Tra toàn bộ nguồn invoice/order/hold rồi khóa đúng thứ tự chung. Unique provider transaction và command key bảo vệ callback lặp, cả khi event key khác nhau cho cùng giao dịch.
3. Ghi Payment thực thu đúng một lần. Với invoice hợp lệ, phân bổ theo InvoiceLine.line_no, đủ dòng trước rồi đến dòng tiếp; chỉ phân bổ tối đa số dư hợp lệ. Phần tiền thừa không phân bổ, tạo RefundRequest OVERPAYMENT.
4. Online booking: chỉ CONFIRMED khi hold còn ACTIVE, now < expires_at, reservation vẫn thuộc hold và đủ giá. Tạo Appointment + Encounter, event, allocation và chuyển hold cùng transaction. Nếu tiền đến muộn: ghi Payment nhưng không allocate vào dòng phí khám đã hủy, không tạo lịch; tạo RefundRequest LATE_PAYMENT toàn khoản.
5. CLS: khi đủ tiền lần đầu ghi fully_paid_at, snapshot validity_days=10 và expires_at=paid_at+10 ngày; Order READY. Không tạo queue tự động.
6. Tiền mặt: CASHIER được cấp quyền, kiểm tra số dư và command key trong transaction. Không cho tiền mặt cho hold online; không thu vượt số dư. Thanh toán MoMo phát sinh sau khi tiền mặt đã thu đủ phải hoàn, không ghi doanh thu hai lần cho cùng khoản phải thu.

PaymentEvent xử lý lỗi có thể retry; event APPLIED không thực hiện lại. Không chuyển Payment sang FAILED khi giao dịch đã CONFIRMED; cần refund nếu tiền đã thu. Provider event key là khóa adapter nội bộ theo khả năng cổng, không khẳng định MoMo có trường cùng tên.

### TX06 — Hủy online

Khóa slot/hold, Appointment/Encounter rồi invoice/payment. Kiểm tra ONLINE, BOOKED, PLANNED và `now <= starts_at - 24h`. Đổi Appointment/Encounter CANCELLED, xóa reservation, ghi event. Dòng phí khám được chuyển CANCELLED; tạo RefundRequest theo từng allocation nguồn để hoàn đủ thực thu đúng một lần. Không sửa Payment hoặc số tiền/nội dung hóa đơn gốc; chỉ cập nhật metadata hủy của dòng phí khám. Yêu cầu hủy lặp trả kết quả cũ. Không có endpoint đổi lịch, không cho DELETE Appointment.

### TX07 — Lập hóa đơn và thu bổ sung

Bác sĩ phụ trách tạo Invoice draft cho cùng Patient/Encounter; chọn các Order chưa có line, cùng Encounter, đang có hiệu lực. Lấy giá CLS tại lúc lập chỉ định và giữ ổn định bằng draft line trong cùng transaction tạo Order; Invoice đầu có phí khám trực tiếp theo Appointment.examination_amount. Chỉ phát hành hóa đơn khi tổng và snapshot đầy đủ. Khi cần thu ngay, bác sĩ phát hành draft đang có; sau phát hành, CLS mới vào invoice draft bổ sung. InvoiceLine.order_id UQ chống thu cùng Order hai lần.

Trực tiếp chỉ một phí khám trên toàn bộ invoice của Encounter. Online kiểm tra phí từ hold invoice, không thêm line phí khám. Phát hành -> commit -> tạo QR request cho số dư. QR không là chứng nhận đã thu; bản in có thể cũ, giao diện thanh toán phải kiểm tra trạng thái request hiện hành.

### TX08 — Có mặt, hoãn và bắt đầu CLS

Khóa Encounter/Order. Kiểm tra READY, đủ tiền, dòng hóa đơn ACTIVE, chưa hủy, now < expires_at, phòng hỗ trợ service và nhân viên đang trực. Tạo ClinicalQueueEntry WAITING và Order QUEUED nguyên tử. Tiếp nhận đổi ACCEPTED; trước bắt đầu kiểm tra lại hạn dưới cùng khóa Order rồi ghi started_at/IN_PROGRESS. Bắt đầu chỉ khi đã nhận; nhân viên không được nhận thay staff khác.

Hoãn trước bắt đầu: đóng entry DEFERRED, trả Order READY, Encounter WAITING_CLS; không sửa mốc tiền/hạn. Quay lại mở entry mới. Mỗi Order chỉ một queue entry active. Queue DONE không được mở lại để thực hiện lần hai.

### TX09 — Hết hạn hoặc hủy CLS

Job lấy batch có fully_paid_at, started_at NULL, expires_at <= now, status READY/QUEUED/ACCEPTED. Khóa Encounter/Order rồi kiểm tra lại: nếu đã IN_PROGRESS thì bỏ qua. Hủy EXPIRED, đóng queue, chuyển InvoiceLine CANCELLED và tạo yêu cầu hoàn theo allocation. Unique idempotency key theo Order + nguồn allocation ngăn hoàn lặp. Không hoàn dòng phí khám hoặc order khác. Một giao dịch thất bại rollback cả hủy Order/hủy InvoiceLine/refund request.

Bác sĩ hủy chuyên môn trước tiếp nhận theo quy tắc tương tự; Order chưa trả tiền cũng hủy dòng InvoiceLine nếu đã phát hành. Draft chưa phát hành có thể bỏ line, ghi audit. Không hủy Order IN_PROGRESS bằng luồng thường. Encounter không tự COMPLETED khi job hủy.

### TX10 — Kết quả và hoàn tất khám

Phát hành result khóa Order và các version. Xác nhận cùng Order, nội dung có báo cáo hoặc chỉ số hợp lệ; bản IMAGING cần kết luận, LAB cần ít nhất một chỉ số hoặc báo cáo văn bản. Ghi actor/time, chuyển Order COMPLETED lần đầu và đóng queue. Sau PUBLISHED khóa sửa content/value/attachment; đính chính tạo successor duy nhất, lý do bắt buộc.

Review tham chiếu đúng result version/order/Encounter. Hoàn tất khóa Encounter, Orders, invoice liên quan (gồm hold invoice) và kiểm tra lại chẩn đoán FINAL, tất cả current published result đã review hoặc Order đã hủy, không draft prescription, mọi outstanding=0. Chuyển Encounter và Appointment COMPLETED nguyên tử. Nếu published correction đến sau hoàn tất, giữ trạng thái và ghi review bổ sung, không tự mở lại hoặc tự xóa chẩn đoán.

### TX11 — Hoàn tiền thủ công

Khóa RefundRequest và Payment nguồn. Tổng yêu cầu hoàn chưa được giải quyết không vượt số tiền còn có thể hoàn; LINE_CANCELLED còn bị giới hạn theo allocation của dòng đã hủy tương ứng. Tiền chi được ghi bằng Refund với bằng chứng/reference và command key; tổng chi không vượt request và tổng refund/payment không vượt Payment.amount. Hoàn thất bại không tạo dòng Refund thành công. COMPLETED khi đủ số tiền; mọi lần chi được audit. Không gọi API tự hoàn MoMo trong v1.

## Bổ sung kỹ thuật bắt buộc: CommandReceipt

Để request retry không tạo lại lịch/hóa đơn/hoàn tiền, dùng bảng `CommandReceipt`: `id bigint IDENTITY PK`, `account_id? FK Account`, `command_type varchar(60)`, `idempotency_key varchar(100)`, `request_hash char(64)`, `resource_type varchar(60)`, `resource_id bigint`, `created_at datetime2(3)`. UNIQUE(command_type,idempotency_key); key gắn identity phía server, không cho đọc kết quả command của tài khoản khác. Cùng key khác hash trả xung đột, không ghi nghiệp vụ. Ghi receipt cùng transaction thành công; lỗi rollback không để receipt thành công giả. Hold/payment/refund vẫn giữ key tại bảng nguồn để đối soát.

## Upload ảnh Cloudinary

- Patient.avatar_url và Staff.avatar_url lưu link HTTPS trả về sau upload thành công, public_id lưu để quản lý asset. NULL nghĩa chưa có ảnh, UI dùng ảnh mặc định; không lưu binary/base64 vào SQL Server.
- Nhân viên sửa ảnh bản thân, bệnh nhân sửa ảnh bản thân; admin được cập nhật ảnh staff. Ảnh kết quả chỉ lab được phân công thêm khi ResultVersion chưa phát hành. Kết quả đã phát hành giữ nguyên liên kết; đính chính dùng version mới.
- Upload ngoài transaction DB. Backend kiểm tra asset thuộc upload được cấp quyền, loại tệp/kích thước và URL thuộc cấu hình Cloudinary của dự án; không tin URL bất kỳ do client gửi. Sau thành công mới transaction ghi URL/public_id. Ghi DB thất bại thì ghi log để dọn asset mồ côi.
- Thay avatar: upload mới → cập nhật DB có kiểm tra version → dọn ảnh cũ sau commit nếu không còn tham chiếu. Không xóa asset y tế của version cũ khi đính chính. Cấu hình giới hạn dung lượng/định dạng và cloud được chốt khi triển khai.
- FileObject giữ metadata Cloudinary và URL gốc không chứa credential tạm thời. Backend kiểm tra quyền trước cấp URL đọc ảnh CLS; không đưa URL ảnh y tế vào CSV thống kê.

## Chỉ mục đề xuất

- Index FK dùng JOIN và kiểm tra quan hệ; không index mọi cột văn bản.
- WorkAssignment(staff_id,starts_at,ends_at), (work_date,mode,specialty_id,shift_id); OnlineSlot(assignment_id,starts_at).
- Appointment(onsite_assignment_id,status), (doctor_id,visit_mode,status), (patient_id,booked_at DESC); BookingHold(status,expires_at).
- ClinicalOrder(status,expires_at) INCLUDE(started_at,encounter_id); ClinicalOrder(encounter_id); ClinicalQueueEntry(room_id,status,checked_in_at).
- ResultVersion(order_id,status,version_no DESC); ClinicalReview(encounter_id,result_version_id).
- Invoice(patient_id,issued_at), Invoice(encounter_id), InvoiceLine(invoice_id); Allocation(invoice_line_id), Payment(invoice_id,paid_at), Refund(request_id,refunded_at), RefundRequest(status,requested_at).
- AuditEvent(entity_type,entity_id,occurred_at), CommandReceipt unique command key.

Kiểm tra execution plan với dữ liệu giả ở giai đoạn SQL rồi mới chốt INCLUDE/phân trang/index. Không có tuyên bố hiệu năng khi chưa chạy SQL Server.
