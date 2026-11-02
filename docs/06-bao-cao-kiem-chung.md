# 06 — Hóa đơn, CSV và kiểm chứng

## Hóa đơn và QR

Bản in/PDF/HTML nội bộ lấy từ nội dung hóa đơn đã chốt, không lấy tên/giá hiện tại của danh mục. Thể hiện: cơ sở, mã hóa đơn, ngày phát hành theo giờ Việt Nam, mã/tên bệnh nhân, bác sĩ/người lập (hoặc SYSTEM), mã lần khám nếu đã có, từng dịch vụ/số lượng/đơn giá/thành tiền, tổng gốc, giá trị dịch vụ đã hủy, đã thu, đã hoàn, số dư cần trả, trạng thái và QR còn hiệu lực.

- Bác sĩ lập/in hóa đơn của Encounter mình phụ trách; bệnh nhân xem bản của mình, thu ngân in để thu tiền mặt.
- Online booking chưa có bác sĩ thực hiện thao tác lập hóa đơn: SYSTEM lập chứng từ phí khám gắn BookingHold, lưu bác sĩ đã phân qua hold/slot.
- QR gắn PaymentRequest cụ thể, amount cố định. Hết hạn hoặc số dư thay đổi thì hiển thị trạng thái và yêu cầu tạo QR mới, không sửa nội dung request cũ.
- Không đưa bệnh án/chẩn đoán vào QR. Không xem ảnh chụp giao dịch hoặc redirect trình duyệt là xác nhận đã thu.
- Hóa đơn bổ sung chỉ có dịch vụ mới; các bản in ghi tham chiếu Encounter chung. Hóa đơn có dòng đã hủy vẫn xem được và có dấu trạng thái, không biến mất.
- Không gọi chứng từ này là hóa đơn thuế/hóa đơn điện tử hợp pháp.

## Định nghĩa CSV

Định dạng UTF-8 BOM để đọc tiếng Việt bằng Excel, dấu phẩy, quote theo CSV chuẩn, ngày ISO 8601 có offset +07:00, số tiền nguyên đồng không dấu phân cách nghìn. Ô văn bản bắt đầu bằng `=`, `+`, `-`, `@` hoặc ký tự điều khiển được escape an toàn khi xuất để tránh công thức bảng tính; dữ liệu nguồn không bị thay đổi. Không xuất password, token, raw callback, liên kết video hoặc nội dung bệnh án vào CSV thống kê mặc định.

Khoảng lọc ngày là [00:00 ngày bắt đầu, 00:00 ngày sau ngày kết thúc) Asia/Saigon chuyển sang UTC. Bộ lọc được kiểm tra quyền phía server; không tin department_id client. Phân trang theo khóa ổn định; export dài dùng snapshot đọc nhất quán, ghi metadata thời điểm chốt và người xuất trong audit. Không dùng NOLOCK cho số liệu tài chính.

| Báo cáo / grain | Cột tối thiểu | Ngày lọc / quyền |
|---|---|---|
| Lượt khám / 1 Appointment | appointment_code, encounter_code, patient_code, doctor_code, department_code, specialty_code, mode, service_date, shift hoặc slot_start, appointment_status, encounter_status, started_at, completed_at | Ngày lịch khám; Doctor của mình, Head trong khoa, Admin toàn cơ sở |
| Lịch / 1 WorkAssignment | staff_code, department_code, room_code, work_date, mode, shift, starts_at, ends_at, capacity, booked_count | work_date; Head/Admin |
| CLS / 1 ClinicalOrder | order_code, encounter_code, department_thực_hiện, service_code, room, status, ordered_at, fully_paid_at, expires_at, started_at, completed_at, cancel_reason | Chọn rõ ngày chỉ định/trả đủ/thực hiện; Head/Admin |
| CLS hoãn, sắp hết hạn / 1 Order | order_code, patient_code, expires_at, remaining_hours, last_checkin_at, last_queue_status, allocated_amount | As-of time; chỉ started_at NULL, còn hiệu lực; sắp hết hạn ≤24h là mặc định báo cáo; Head/Admin |
| Hóa đơn / 1 Invoice | invoice_code, patient_code, encounter_code nếu có, hold_code nếu có, issued_at, issuer, gross_amount, cancelled_amount, net_charge, allocated_amount, refunded_amount, outstanding | issued_at; Cashier/Admin |
| Thu tiền / 1 Payment | payment_code, invoice_code, method, paid_at, amount, provider_transaction_ref, cashier_code nếu CASH | paid_at; Cashier/Admin |
| Hoàn tiền / 1 Refund | refund_code, request_code, payment_code, refunded_at, method, amount, processed_by, reference | refunded_at; Cashier/Admin |
| Chờ hoàn / 1 RefundRequest | request_code, payment_code, reason, requested_at, requested_amount, refunded_amount, remaining_amount, status | requested_at/as-of; Cashier/Admin |
| Workload / 1 staff + ngày + mode | staff_code, department_code, work_date, assignment_count, assigned_minutes, appointment_count, completed_encounter_count, performed_order_count | Ngày lịch/ngày hoàn thành đúng cột; Head/Admin |

Khoa trong báo cáo lượt khám là Appointment.department_id; khoa CLS là ClinicalOrder.performing_department_id. Không dùng khoa hiện tại của Staff để phân loại dữ liệu quá khứ. Báo cáo bệnh nhân thống kê đếm DISTINCT patient_id trong phạm vi, không xuất thông tin liên hệ mặc định.

## Đối soát và tránh cộng trùng

- Tổng hợp từng bảng con về grain cha **trước khi JOIN**. Một Encounter có 3 Orders và 2 Invoices vẫn là một lượt khám, không 6.
- Tách tổng gốc hóa đơn, dòng hủy, allocations, payment và refund thành các tập tổng hợp riêng. Thu thực tế tính từ Payment; không dùng Invoice.total_amount làm tiền đã thu.
- Hóa đơn hold gắn Encounter qua Appointment.hold_id, không UNION hai lần khi Encounter đã được tạo.
- Workload ca là số assignment/giờ phân công; workload khám là số Encounter hoàn tất; workload lab là Order hoàn tất theo staff đã STARTED, không đếm số result versions.
- Một refund của thu muộn không có allocation vẫn tính vào tiền đã hoàn thực tế, nhưng không gắn sai CLS.
- Báo cáo sắp hết hạn không gồm Order đã IN_PROGRESS; cảnh báo không làm thay đổi hạn hay trạng thái.

## Bộ tình huống kiểm chứng

Đây là đặc tả kiểm thử để thực hiện ở giai đoạn migration/backend; **chưa phải kết quả chạy thử SQL Server**.

| ID | Chuẩn bị / thao tác | Kết quả và bất biến cần kiểm tra |
|---|---|---|
| AT01 | Đăng ký gửi role DOCTOR hoặc staff_id | Server từ chối/không nhận field, chỉ tạo Patient + PATIENT role. |
| AT02 | Gán cả patient_id và staff_id vào Account | CHECK chặn; tài khoản không có hồ sơ cũng bị chặn. |
| AT03 | Staff kiêm Doctor/Head | Dùng được hai nhóm hành động trong đúng phạm vi, không xem khoa khác. |
| AT04 | Hai request giành chỗ cuối ca trực tiếp | Một thành công, một báo hết chỗ; đúng một Appointment+Encounter mới. |
| AT05 | Hai bác sĩ có tỷ lệ 1/2 và 2/4 | Hòa tỷ lệ, chọn mã nhỏ hơn; nhiều request đồng thời không phá capacity. |
| AT06 | Trực tiếp gửi doctor_id hoặc giờ tùy chọn | Server không cho chọn; tự phân đúng ca/chuyên khoa. |
| AT07 | Online tự phân hai bác sĩ có lịch khác nhau | Chọn người rảnh ít lịch nhất ngày; không chọn người hết slot dù ít lịch hơn. |
| AT08 | Cùng staff phân ONSITE và ONLINE giao thời gian | Công bố bị chặn; hai khoảng liền kề được phép. |
| AT09 | Hai bệnh nhân giữ cùng slot | Chỉ một SlotReservation; người còn lại nhận hết chỗ. |
| AT10 | Chưa thanh toán hold | Không có Appointment/Encounter; hold vẫn có invoice để đối soát. |
| AT11 | Thanh toán hợp lệ trước phút thứ 5 | Một Payment, Appointment, Encounter; reservation vẫn chiếm slot. |
| AT12 | Callback lặp với cùng hoặc khác event key, cùng provider transaction | Không ghi Payment/Allocation/booking lần hai. |
| AT13 | Callback giả/sai tiền/sai merchant | Không cấp lịch, event bị REJECTED/REVIEW, lưu dấu vết an toàn. |
| AT14 | Callback tại đúng expires_at, hoặc sau khi slot tái cấp | Không cấp booking cũ; ghi thực thu đã xác minh và yêu cầu hoàn, không lấy slot người mới. |
| AT15 | App lỗi giữa Payment và tạo Encounter | Transaction rollback; xử lý lại callback/đối soát tạo đúng một bộ dữ liệu. |
| AT16 | Hủy online ở t−24h, t−24h−1ms, t−24h+1ms | Hai trường hợp đầu được phép, cuối bị chặn; hoàn đúng phí, giải phóng slot. |
| AT17 | Hủy trực tiếp hoặc đổi lịch mọi hình thức | Bị từ chối; DB không đổi slot/assignment. |
| AT18 | Trực tiếp khám không CLS | Bác sĩ lập invoice phí khám; thu đủ rồi được hoàn tất, không cần đơn thuốc. |
| AT19 | Online đã trả phí, phát sinh 2 CLS | Invoice mới chỉ có CLS; không line phí khám lần hai. |
| AT20 | Invoice đã trả, thêm CLS | Tạo invoice bổ sung và QR riêng; invoice cũ bất biến. |
| AT21 | A=150.000, B=300.000; thu 300.000 theo line_no A rồi B | A được 150.000 READY, B mới 150.000 chưa READY; không đánh dấu cả hai đã đủ. |
| AT22 | MoMo và tiền mặt đồng thời trả cùng hóa đơn | Không allocate vượt charge; MoMo thực thu thừa có request hoàn. |
| AT23 | Order vừa trả đủ nhưng chưa đến lab | READY, có hạn 10 ngày, chưa có queue. |
| AT24 | Lab xác nhận có mặt trong hạn | Tạo một active queue ở phòng hỗ trợ service, không tạo lịch khám mới. |
| AT25 | Hoãn sau xếp hàng/tiếp nhận nhưng chưa STARTED, quay lại nhiều lần | Queue cũ DEFERRED, row mới khi quay lại; cùng Order/Encounter/tiền/hạn, không thu lại. |
| AT26 | Cấu hình đổi từ 10 sang 7 ngày | Order cũ giữ 10 ngày; Order trả đủ sau thay đổi dùng 7. |
| AT27 | Bắt đầu ở expires_at−1ms, kết quả sau hạn | Cho bắt đầu, job không hủy; phát hành bình thường. |
| AT28 | Bắt đầu tại expires_at hoặc sau hạn | Chặn bắt đầu, job/command hủy và tạo yêu cầu hoàn. |
| AT29 | Job expire và lab START đồng thời | Một hướng thắng khóa; không trạng thái vừa STARTED vừa CANCELLED/refund. |
| AT30 | Một trong ba CLS hết hạn; phí khám và hai CLS kia đã dùng | Chỉ hủy dòng thu/hoàn CLS hết hạn, không hoàn toàn invoice. |
| AT31 | Job expire chạy lại 3 lần | Một lần hủy dòng và tập refund request duy nhất; không nhân đôi hoàn. |
| AT32 | Một Order trả bởi hai Payment, bị hủy | RefundRequest theo từng allocation nguồn, tổng bằng tiền CLS thực trả. |
| AT33 | Hoàn tiền thất bại rồi retry | Không tạo Refund thành công giả; retry cùng command không chi hai lần; tổng không vượt Payment. |
| AT34 | Bác sĩ Complete khi Order chưa xong/chưa review/còn nợ | Bị chặn; khi Order và dòng thu đã hủy, còn chờ hoàn thì không chặn. |
| AT35 | Phát hành result rồi sửa trực tiếp | Chặn; đính chính tạo version mới, giữ cũ; review gắn đúng version. |
| AT36 | Result của Order A gắn review Encounter B | Bị chặn transaction; không lộ kết quả qua sửa ID. |
| AT37 | Patient đọc draft result/đơn hoặc chẩn đoán chưa Complete | Không trả dữ liệu chưa công bố; không xem file người khác. |
| AT38 | Sửa giá/tên thuốc/chuyển khoa sau khám | Hóa đơn, đơn, khoa lịch sử không đổi. |
| AT39 | 1 Encounter, 3 Orders, 2 Invoices | Report lượt khám=1, tổng tiền bằng dữ liệu nguồn; không nhân 3×2. |
| AT40 | Giao dịch UTC sát 17:00 (00:00 Việt Nam) | Vào đúng ngày địa phương, khoảng ngày không trùng/thiếu. |
| AT41 | Export Head gửi ID khoa khác, Doctor gửi ID bác sĩ khác | Bị từ chối hoặc lọc theo quyền server; audit ai xuất. |
| AT42 | CSV có tên tiếng Việt, dấu phẩy, xuống dòng, =formula | Đọc đúng UTF-8/quoting; công thức không chạy. |
| AT43 | In hóa đơn sau sửa danh mục hoặc hết hạn QR | Nội dung snapshot giữ nguyên, QR cũ không được trình bày như còn hiệu lực. |
| AT44 | Video ngắt rồi kết nối lại | Thêm participation, Encounter không tự Complete. |
| AT45 | Retry cùng command key nhưng payload khác | Trả xung đột, không ghi bản thứ hai hoặc lộ kết quả của tài khoản khác. |
| AT46 | Upload/thay avatar Patient và Doctor | DB chỉ lưu URL/public_id; ảnh thuộc đúng tài khoản, DB lỗi không làm mất ảnh cũ. |
| AT47 | Lab đính kèm nhiều ảnh Cloudinary, phát hành rồi đính chính | Ảnh gắn đúng ResultVersion; không sửa/xóa asset của bản phát hành cũ, người không có quyền không đọc được ảnh. |
| AT48 | Truy mã Patient/Service từ invoice sau cập nhật thông tin | Mã lấy qua FK, không cột mã snapshot; tên/giá tại thời điểm phát hành vẫn giữ nguyên. |
| AT49 | Hủy một InvoiceLine đã trả và một dòng chưa trả | Dòng CANCELLED không còn khoản phải thu, giữ amount gốc; chỉ hoàn tiền đã thu, không cần bảng giảm giá. |

## Điều kiện bàn giao sang SQL

Chốt các điểm mở tại tài liệu 01, đặc biệt giờ ca, quyền chi hoàn và xử lý nghỉ đột xuất. Sau đó tạo migration theo phụ thuộc FK: danh mục/hồ sơ → account/RBAC → giá/lịch → booking/khám → CLS → invoice/payment/refund → result/prescription/video/audit. Với FK actor, tạo sau Account và seed tài khoản admin qua bước bootstrap có kiểm soát.

Tạo dữ liệu giả gồm ít nhất 2 khoa khám, 1 khoa CLS, 3 bác sĩ, 2 lab staff, 1 cashier, các ca/slot, dịch vụ xét nghiệm và hình ảnh. Thực hiện AT01–AT49 trên SQL Server thật và kiểm thử cạnh tranh bằng hai kết nối độc lập; không thay bằng kiểm thử in-memory. Đối chiếu số dư và lịch sử trước/sau rollback. Chưa tuyên bố bộ CSDL đã vận hành được khi mới có tài liệu.
