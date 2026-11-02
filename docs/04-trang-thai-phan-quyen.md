# 04 — Trạng thái và phân quyền

## Ma trận chuyển trạng thái

Mọi chuyển trạng thái kiểm tra lại dưới khóa, có event/audit trong cùng transaction. Không cho client gửi status tùy ý bằng API cập nhật chung.

| Đối tượng | Từ → đến | Chủ thể và điều kiện |
|---|---|---|
| WorkSchedule | DRAFT → PUBLISHED | Trưởng khoa đúng phạm vi; không xung đột, staff/phòng/chuyên khoa hợp lệ. Đã có booking thì không sửa assignment ảnh hưởng booking. |
| BookingHold | ACTIVE → CONFIRMED | SYSTEM: MoMo được xác minh, đúng tiền/tiền tệ/merchant/request, now < expires_at và còn quyền chiếm slot. |
| BookingHold | ACTIVE → EXPIRED | SYSTEM khi now >= expires_at; trả slot, hủy dòng phí khám của hóa đơn giữ chỗ chưa sử dụng. |
| Appointment | BOOKED → CANCELLED | Patient sở hữu, ONLINE, now <= slot.start −24h, Encounter PLANNED; giải phóng slot, hủy Encounter, hủy dòng phí khám và yêu cầu hoàn. |
| Appointment | BOOKED → NO_SHOW | Bác sĩ phụ trách sau thời điểm hẹn/ca kết thúc và chưa bắt đầu; lý do bắt buộc; không tự hoàn tiền. |
| Appointment | BOOKED → COMPLETED | SYSTEM cùng transaction hoàn tất Encounter. Không có chuyển đổi slot/bác sĩ. |
| Encounter | PLANNED → IN_PROGRESS | Bác sĩ phụ trách xác minh có mặt; online phí khám đã trả; trực tiếp chưa cần trả phí. Chụp tiền sử/dị ứng. |
| Encounter | IN_PROGRESS → WAITING_CLS | Bác sĩ khi còn chỉ định chưa kết thúc, đặc biệt bệnh nhân hoãn. |
| Encounter | WAITING_CLS → IN_PROGRESS | Bác sĩ tiếp tục đánh giá khi có kết quả hoặc quyết định chuyên môn; lab không tự bắt đầu khám. |
| Encounter | IN_PROGRESS → COMPLETED | Có FINAL diagnosis, mọi order COMPLETED và current published version đã review hoặc CANCELLED; không còn khoản phải thu; không còn đơn DRAFT. Không bắt buộc đơn. |
| Encounter | PLANNED → CANCELLED/NO_SHOW | Theo transaction Appointment tương ứng; không cho bệnh nhân hủy Encounter đang khám. |
| ClinicalOrder | PENDING_PAYMENT → READY | SYSTEM khi phân bổ đủ nguyên giá và chưa hủy; ghi fully_paid_at, validity_days, expires_at đúng một lần. |
| ClinicalOrder | READY → QUEUED | Lab đúng phạm vi xác nhận có mặt, chọn phòng hỗ trợ service, now < expires_at; tạo queue WAITING. |
| ClinicalOrder | QUEUED → ACCEPTED | Lab đang trực phòng đó nhận; queue WAITING → ACCEPTED. |
| ClinicalOrder | ACCEPTED → IN_PROGRESS | Nhân viên đã nhận, kiểm tra lại còn hạn và tiền; queue ACCEPTED → STARTED; ghi started_at. |
| ClinicalOrder | QUEUED/ACCEPTED → READY | Bệnh nhân hoãn trước bắt đầu, lab đóng queue DEFERRED; không đổi hạn. |
| ClinicalOrder | IN_PROGRESS → COMPLETED | Lab phát hành kết quả đầu tiên; queue STARTED → DONE. |
| ClinicalOrder | PENDING_PAYMENT/READY/QUEUED → CANCELLED | Bác sĩ hủy trước lab tiếp nhận, lý do bắt buộc; đóng queue, hủy dòng hóa đơn và hoàn nếu đã thu. |
| ClinicalOrder | READY/QUEUED/ACCEPTED → CANCELLED | SYSTEM hết hạn và started_at NULL; cancel_reason=EXPIRED, đóng queue EXPIRED, hủy dòng hóa đơn và yêu cầu hoàn. |
| ResultVersion | DRAFT → CONFIRMED → PUBLISHED | Lab có quyền và đang phụ trách; nội dung hợp lệ, ký actor/time. Không tự tạo order. |
| ResultVersion | Published cũ → thêm DRAFT mới | Lab tạo successor, lý do đính chính bắt buộc, version tăng; bản cũ bất biến. Chưa phát hành mới thì bản cũ vẫn hiện hành. |
| Prescription | DRAFT → ISSUED | Bác sĩ phụ trách; có ít nhất một thuốc, dose/frequency/route/duration/quantity hợp lệ. Sau phát hành bất biến. |
| Invoice | DRAFT → ISSUED | Bác sĩ phụ trách hoặc SYSTEM cho hold online; có line và tổng khớp, chốt snapshot. |
| Invoice | DRAFT → VOID | Người lập khi chưa phát hành. ISSUED không VOID; hủy từng InvoiceLine và giữ số tiền gốc, không giảm giá. |
| InvoiceLine | ACTIVE → CANCELLED | SYSTEM khi hold hết hạn/hủy online hợp lệ hoặc CLS bị hủy; bác sĩ trong transaction hủy CLS được phép. Ghi cancelled_at/by/reason và audit; không ACTIVE lại, không sửa amount, tạo yêu cầu hoàn nếu đã thu. |
| PaymentRequest | CREATED → PENDING → SUCCEEDED/FAILED/EXPIRED | SYSTEM xác minh kết quả; EXPIRED có thể được đối soát thành SUCCEEDED nếu thực thu muộn, nhưng không tự khôi phục booking. |
| RefundRequest | REQUESTED → PROCESSING → COMPLETED | Thu ngân xử lý thủ công; chỉ COMPLETED khi tổng Refund thực chi đạt số yêu cầu. Lỗi chi không tạo Refund giả, ghi audit rồi thử lại. |

ClinicalOrder hết hạn không có nghĩa kết quả bệnh lý hết giá trị; hạn ở đây chỉ là hạn thực hiện dịch vụ theo yêu cầu đồ án. Bản sửa kết quả không mở lại Order/Encounter đã hoàn tất. Tạo ClinicalReview mới để ghi bác sĩ đọc bản sửa.

## Quyền và phạm vi dữ liệu

Ký hiệu: “của mình” với bác sĩ là Appointment.doctor_id = staff của tài khoản; với bệnh nhân là patient_id. Head cần DepartmentHeadAssignment còn hiệu lực và Role tương ứng. Admin quản trị cấu hình không mặc nhiên được sửa hồ sơ y tế.

| Hành động | Patient | Doctor | Lab | Head | Cashier | Admin |
|---|---|---|---|---|---|---|
| Tự đăng ký/đổi thông tin cá nhân | Bản thân | Thông tin cho phép | Thông tin cho phép | Thông tin cho phép | Thông tin cho phép | Cấp/khóa tài khoản staff |
| Đặt/hủy online hợp lệ | Của mình | — | — | — | — | Không thay bệnh nhân |
| Đặt trực tiếp | Của mình | — | — | — | — | — |
| Xem hồ sơ y tế | Của mình, đã công bố | Lịch sử Patient có Encounter mình phụ trách | Tối thiểu cần cho order thuộc khoa/phòng trực | Tổng hợp thống kê, không mặc nhiên toàn bệnh án | Chỉ định danh và khoản thu | Không mặc nhiên nội dung bệnh án |
| Khám/chẩn đoán/kê đơn/review | — | Của mình | — | Chỉ nếu kiêm Doctor và được phân | — | — |
| Tạo/hủy CLS chuyên môn | — | Của mình, trước tiếp nhận | — | — | — | — |
| Check-in/hoãn/tiếp nhận/kết quả | — | Xem tiến độ | Đúng khoa, phòng, lịch trực | — | — | — |
| Lập/in hóa đơn | Xem/in của mình | Lượt khám mình phụ trách | — | — | Xem/in phục vụ thu | Xem phục vụ thống kê |
| Xác nhận tiền mặt | — | — | — | — | Có | — |
| Xác nhận MoMo | Không ai thao tác thủ công; chỉ hệ thống xác minh callback/đối soát | — | — | — | — | — |
| Xử lý hoàn tiền | Xem tiến độ của mình | — | — | — | Mặc định v1, cần xác nhận vận hành | Audit |
| Phân lịch/công bố | — | Xem của mình | Xem của mình | Trong khoa được giao | — | Quản trị danh mục, không tự có quyền trưởng khoa |
| Giá/danh mục/role | — | — | — | — | — | Có |
| CSV | — | Lượt khám mình | — | Lịch/lượt khám/CLS/workload khoa | Hóa đơn/thu/hoàn | Thống kê toàn cơ sở |

Permission gợi ý: `APPOINTMENT_CREATE_SELF`, `APPOINTMENT_CANCEL_SELF`, `ENCOUNTER_WRITE_ASSIGNED`, `RESULT_WRITE_ASSIGNED`, `INVOICE_ISSUE_ASSIGNED`, `CASH_RECEIVE`, `REFUND_PROCESS`, `SCHEDULE_PUBLISH_DEPARTMENT`, `REPORT_EXPORT_SELF`, `REPORT_EXPORT_DEPARTMENT`, `REPORT_EXPORT_BILLING`, `REPORT_EXPORT_ALL`, `ACCOUNT_MANAGE`, `CATALOG_MANAGE`.

Backend luôn ghép quyền hành động với điều kiện row-level, không chỉ ẩn nút UI. Khóa tài khoản hoặc thu hồi role có hiệu lực ở request tiếp theo. Tài khoản đa role dùng hợp quyền hành động nhưng không mở rộng vượt phạm vi dữ liệu của từng hành động. File và link video cũng kiểm tra chủ sở hữu/phân công; không suy quyền từ biết ID.
