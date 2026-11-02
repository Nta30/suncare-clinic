# 01 — Nghiệp vụ và nhật ký quyết định

## Phạm vi

Một cơ sở, khám ngoại trú trực tiếp và online; tài khoản, khoa/phòng, phân lịch, đặt khám, khám, CLS, thu phí, kê đơn, EMR, dashboard. Không nội trú, BHYT, kho, bán/cấp phát thuốc, kế toán tổng hợp hoặc hóa đơn thuế. Hóa đơn trong tài liệu là chứng từ thu dịch vụ nội bộ.

## Quyết định đã chốt

| ID | Quyết định |
|---|---|
| D01 | Patient và Staff riêng; không Person. Một Account chỉ thuộc một loại hồ sơ. |
| D02 | Chỉ bệnh nhân tự đăng ký. Admin cấp tài khoản nhân viên; nhiều vai trò nội bộ được phép. |
| D03 | Trực tiếp chỉ chọn chuyên khoa, ngày, sáng/chiều; không chọn bác sĩ, không đổi/hủy lịch. |
| D04 | Phân trực tiếp theo tỷ lệ lượt đặt/sức chứa thấp nhất; hòa theo mã bác sĩ. |
| D05 | Online slot 20 phút; được chọn bác sĩ; tự phân theo số lịch online đã đặt trong ngày thấp nhất, hòa theo mã bác sĩ. |
| D06 | Online giữ chỗ 5 phút, thanh toán MoMo thành công mới tạo Appointment và Encounter. |
| D07 | Online chỉ hủy khi cách giờ hẹn ít nhất 24 giờ, hoàn toàn bộ phí khám; không đổi lịch. |
| D08 | Giá theo bác sĩ và hình thức; chốt trước khi xác nhận. |
| D09 | Trực tiếp khám ban đầu trước, bác sĩ lập hóa đơn phí khám và CLS; không CLS vẫn thu phí khám. |
| D10 | Bác sĩ lập/in hóa đơn có QR MoMo; MoMo tự xác nhận; thu ngân xác nhận tiền mặt. |
| D11 | Online không thu lại phí khám; CLS phát sinh có hóa đơn, thêm CLS sau thu thì hóa đơn bổ sung. |
| D12 | Một ClinicalOrder tương ứng một dịch vụ, được phân bổ tiền riêng, vào hàng đợi phòng khi lab xác nhận có mặt. |
| D13 | CLS đã trả đủ được thực hiện trong 10 ngày từ lúc trả đủ; cấu hình được, chốt trên chỉ định. Hoãn không gia hạn. |
| D14 | Chưa bắt đầu mà quá hạn: hủy và hoàn phần tiền tương ứng; đã bắt đầu trong hạn thì được tiếp tục. |
| D15 | Hoãn CLS giữ cùng Order và Encounter; Encounter chờ CLS. Quay lại không đặt khám mới, không thu lại. |
| D16 | Đơn thuốc không bắt buộc; hoàn tất cần chẩn đoán, kết quả đã được đánh giá hoặc chỉ định đã hủy và hết khoản phải thu. |
| D17 | Kết quả có chỉ số/báo cáo/tệp; bản phát hành không ghi đè, đính chính bằng phiên bản mới. |
| D18 | Hoàn tiền thủ công, lưu riêng yêu cầu và giao dịch hoàn. Chờ hoàn không chặn hoàn tất khám. |
| D19 | Một khoa chính/nhân viên tại một thời điểm; bác sĩ nhiều chuyên khoa; nhiều lab staff cùng phòng/ca. |
| D20 | EMR tổng hợp; CSV theo vai trò, video không ghi hình, bảng hỏi trước khám đơn giản. |
| D21 | Bệnh nhân và bác sĩ có ảnh; ảnh lưu Cloudinary, CSDL lưu URL. Staff dùng chung trường ảnh đại diện; ảnh CLS gắn với phiên bản kết quả qua FileObject/ResultAttachment. |
| D22 | ID và code nghiệp vụ không thay đổi/không tái sử dụng; dùng FK để truy mã, không lưu bản sao mã định danh trên chứng từ. Nội dung dễ thay đổi như tên, địa chỉ, giá và liều dùng vẫn chốt theo thời điểm phát hành. |
| D23 | Không có bảng giảm giá hoặc CreditNote. Dịch vụ bị hủy được đánh dấu trên InvoiceLine; hoàn tiền dùng RefundRequest và Refund. |

## Luồng chuẩn

**Trực tiếp:** lịch công bố → chọn chuyên khoa/ngày/ca → phân bác sĩ, giữ giá và tạo Appointment + Encounter nguyên tử → bác sĩ bắt đầu → chẩn đoán/chỉ định → bác sĩ lập hóa đơn → MoMo hoặc tiền mặt → lab xác nhận có mặt → xếp hàng → thực hiện → phát hành → bác sĩ đánh giá → kê đơn nếu cần → hoàn tất.

**Online:** chọn chuyên khoa/giờ, có thể chọn bác sĩ → xác định bác sĩ và báo giá → giữ chỗ 5 phút + hóa đơn phí khám → yêu cầu MoMo → giao dịch hợp lệ trong hạn → Appointment + Encounter → video → khám; CLS nếu có theo luồng thu bổ sung. Mất kết nối không tự kết thúc Encounter.

**Hoãn CLS:** chỉ định đã đủ tiền nhưng chưa bắt đầu → không vào hàng đợi, hoặc đóng lượt xếp hàng hiện tại → Encounter WAITING_CLS → quay lại trong hạn → lab xác nhận có mặt, mở lượt hàng đợi mới → tiếp tục. Hết hạn: hủy chỉ định, hủy dòng khoản phải thu trên InvoiceLine, tạo yêu cầu hoàn; bác sĩ quyết định kết thúc/tiếp tục khám.

## Mặc định kỹ thuật của bản thiết kế

| ID | Mặc định và lý do |
|---|---|
| T01 | Lưu UTC bằng datetime2(3); hiển thị/lọc ngày theo Asia/Saigon. 10 ngày = 240 giờ, biên hợp lệ now < expires_at; hủy online hợp lệ now <= appointment_start − 24 giờ. |
| T02 | Khung ca sáng/chiều cấu hình, không gán giờ bệnh viện giả định. Ca trực tiếp chiếm toàn bộ khoảng làm việc của bác sĩ; không xen slot online trong khoảng đó. |
| T03 | Nhân viên nghỉ/chuyển khoa không sửa khoa lịch sử của Encounter/Order. Chỉ đổi phân công tương lai chưa có đặt; điều phối khi lịch đã có bệnh nhân là điểm mở, không tự chuyển bệnh nhân. |
| T04 | Mỗi Account có đúng một Patient hoặc Staff. Hai hồ sơ có thể chứa thông tin cá nhân trùng nhưng không tự đồng bộ/gộp danh tính. |
| T05 | Chỉ định một lần, không quản lý mẫu, làm lại hoặc nhiều đợt cho cùng Order. Bản sửa kết quả là phiên bản, không phải lần thực hiện mới. |
| T06 | Một invoice chỉ một Patient, một tiền tệ VND, một lần khám hoặc một booking hold; không giảm giá/thuế/BHYT trong v1. Giá và số tiền nguyên đồng, decimal(19,0), không dùng float. |
| T07 | Có thể thu một phần bằng tiền mặt; QR luôn là số dư hóa đơn tại thời điểm phát hành. Khóa hóa đơn khi tạo/xác nhận thanh toán để không thu vượt; giao dịch online đến trễ gây thừa phải hoàn riêng. |
| T08 | Hóa đơn phí khám online do SYSTEM lập trước khi có Encounter; hóa đơn trực tiếp/bổ sung do bác sĩ phụ trách lập. |
| T09 | Appointment COMPLETED chỉ khi Encounter hoàn tất; bệnh nhân vắng mặt do bác sĩ ghi nhận, không tự hoàn tiền online. Số lịch online dùng cân bằng là BOOKED/COMPLETED/NO_SHOW, bỏ CANCELLED; hold chỉ dùng kiểm tra khả dụng. |
| T10 | Draft có thể sửa; tài liệu phát hành không sửa tiền/nội dung. Không có bảng giảm giá. Khi hủy, chuyển InvoiceLine sang CANCELLED, lưu thời điểm/người/lý do; không xóa hoặc sửa số tiền gốc. Chưa trả tiền vẫn được hủy CLS trước tiếp nhận. |

## Điểm mở cần xác nhận trước khi triển khai phần phụ thuộc

Không chặn thiết kế quan hệ; chưa tự đưa các chính sách này vào sản phẩm:

- Thời gian ca sáng/chiều, sức chứa và giá thực tế: dữ liệu cấu hình cần chủ dự án cung cấp.
- Cách xử lý bệnh nhân vắng mặt trực tiếp, bác sĩ nghỉ đột xuất, thay bác sĩ cho Encounter chờ lâu: không cho hệ thống tự hủy hoặc đổi lịch thay bệnh nhân.
- Ai thực hiện chi tiền hoàn thủ công, ai duyệt và hoàn qua kênh nào: bản v1 gán CASHIER xử lý, lưu ADMIN audit; cần xác nhận vận hành trước bật chức năng chi tiền.
- Nhà cung cấp video, thông tin merchant MoMo, thời hạn giữ QR hóa đơn thông thường, lưu trữ tệp và thời gian lưu dữ liệu: cấu hình khi triển khai. Không suy đoán giao thức/field chính thức của MoMo trong bản thiết kế.
- Kết quả đính chính sau khi Encounter đã hoàn tất: giữ Encounter đã hoàn tất, tạo review mới cho bác sĩ; cần xác nhận kênh thông báo và xử lý lâm sàng trước bật quy trình đính chính.
