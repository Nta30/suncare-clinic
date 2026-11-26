# 01 — Nghiệp vụ và nhật ký quyết định

## Phạm vi

Một cơ sở, khám ngoại trú trực tiếp và online; tài khoản, khoa/phòng, phân lịch, đặt khám, khám, CLS, thu phí, kê đơn, EMR, dashboard. Không nội trú, BHYT, kho, bán/cấp phát thuốc, kế toán tổng hợp hoặc hóa đơn thuế. Hóa đơn trong tài liệu là chứng từ thu dịch vụ nội bộ.

## Quyết định đã chốt

| ID | Quyết định |
|---|---|
| D01 | Patient và Staff riêng; không Person. Một Account chỉ thuộc một loại hồ sơ. |
| D02 | Chỉ bệnh nhân tự đăng ký. Admin cấp tài khoản nhân viên; nhiều vai trò nội bộ được phép. `DEPARTMENT_HEAD` là quyền bổ sung của `DOCTOR`, còn `DepartmentHeadAssignment` xác định khoa và nhiệm kỳ. |
| D03 | Cập nhật 2026-11-07: Trực tiếp chọn chuyên khoa, ngày, sáng/chiều; có thể chọn bác sĩ cụ thể. Nếu chọn, bác sĩ phải có lịch công bố đúng chuyên khoa/ngày/ca, còn sức chứa và giá khám hiệu lực. Nếu không chọn, áp dụng D04. Không đổi/hủy lịch trực tiếp. |
| D04 | Khi bệnh nhân không chọn bác sĩ, hệ thống phân trực tiếp theo tỷ lệ lượt đặt/sức chứa thấp nhất; hòa theo mã bác sĩ. |
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
| D24 | Quyết định ngày 2026-11-02: phòng khám ONLINE là tài nguyên được tạo, phân công và chống trùng lịch như phòng vật lý. Trưởng khoa phân lịch trong khoa mình phụ trách; màn hình chia calendar thứ Hai–thứ Bảy và bảng nhân viên có tìm kiếm/bộ lọc, chọn khoảng ngày của đợt phân ca. Mỗi đợt có bản nháp và lịch sử riêng; chọn đợt cũ phải nạp đúng phân công lên calendar. Cần migration mới cho Room và WorkAssignment trước khi triển khai lịch online; không sửa migration đã áp dụng. |
| D25 | Quyết định ngày 2026-11-04: lịch bác sĩ ONLINE theo ca sáng/chiều đã cấu hình, mỗi ca là một WorkAssignment riêng và slot 20 phút chỉ sinh trong giờ ca; chọn cả ngày dùng cả hai ca và không có slot giờ nghỉ trưa. Trưởng khoa có thể chọn nhiều ngày trong một thao tác phân công, lưu nguyên tử toàn bộ các ngày/ca đã chọn. |
| D26 | Quyết định ngày 2026-11-04: phân công nhanh bằng cách kéo hoặc chọn một vùng liền kề trên thời khóa biểu, có thể gồm nhiều ngày và cả ca sáng/chiều. Vùng chọn phải cùng một hình thức (ONSITE hoặc ONLINE), không được kéo qua hai hình thức. Một thao tác dùng chung bác sĩ, phòng, chuyên khoa và sức chứa nếu là ONSITE; mỗi tổ hợp ngày/ca tạo assignment riêng và lưu nguyên tử. |
| D27 | Quyết định ngày 2026-11-04: không có nút bật chọn nhiều. Bấm thường chọn một ô; Ctrl/⌘-bấm thêm hoặc bỏ một ô; giữ-kéo chọn một khối ca; Ctrl/⌘-kéo cộng khối vào lựa chọn hiện tại. Có thể chuyển tuần và tiếp tục chọn, các ô đã chọn vẫn được giữ. Một lần lưu chỉ nhận các ô cùng hình thức; API gửi danh sách chính xác cặp ngày/ca để không phân công nhầm ô nằm giữa. |

## Luồng chuẩn

Yêu cầu bổ sung ngày 2026-11-25 về dị ứng: một bệnh nhân có thể có nhiều chất gây dị ứng. Dùng danh mục Allergen có mã/tên thống nhất do người có quyền CATALOG_MANAGE quản lý; bác sĩ chọn chất thay vì nhập lại tên tùy ý. Mỗi chất có phản ứng/mức độ riêng, không ghi trùng cùng chất đang áp dụng cho một bệnh nhân. Nếu chưa có chất trong danh mục, bắt buộc ghi chú rõ chất nghi gây dị ứng; không tự thêm vào danh mục chung. Tên tự do cũ giữ nguyên và chờ bác sĩ đối chiếu, không tự gộp. Snapshot khi bắt đầu khám là bất biến, kể cả khi tên danh mục thay đổi.


**Trực tiếp:** lịch công bố → chọn chuyên khoa/ngày/ca, tùy chọn chọn bác sĩ → nếu đã chọn thì xác nhận bác sĩ còn lịch phù hợp; nếu bỏ qua thì hệ thống tự phân theo D04 → giữ giá và tạo Appointment + Encounter nguyên tử → bác sĩ bắt đầu → chẩn đoán/chỉ định → bác sĩ lập hóa đơn → MoMo hoặc tiền mặt → lab xác nhận có mặt → xếp hàng → thực hiện → phát hành → bác sĩ đánh giá → kê đơn nếu cần → hoàn tất.

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
| T07 | Quyết định Cashier ngày2026-11-26: tiền mặt phải thu toàn bộ khoản cần thanh toán hợp lệ của hóa đơn, không cho giao dịch thu mới một phần. Giữ replay khoản thu cũ; dữ liệu lịch sử thu một phần được thanh toán nốt toàn bộ phần hợp lệ còn lại, không sửa Payment cũ. QR luôn là số dư hóa đơn tại thời điểm phát hành. Khóa hóa đơn khi tạo/xác nhận thanh toán để không thu vượt; giao dịch online đến trễ gây thừa phải hoàn riêng. |
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

## Bổ sung ngày 2026-11-25 — tiền sử, lịch sử khám và mã chẩn đoán

- Tiền sử bệnh là thông tin nền: bệnh bản thân, gia đình, phẫu thuật; độc lập với danh sách những lần khám trước. Không tự biến mọi chẩn đoán cũ thành bệnh nền hiện tại.
- Bác sĩ có Encounter phụ trách được đọc các Encounter COMPLETED khác của cùng Patient, kể cả do bác sĩ khác khám. Chỉ đọc ghi chú, sinh hiệu, chẩn đoán, snapshot tại lần khám, đơn ISSUED và phiên bản kết quả PUBLISHED mới nhất của từng chỉ định; không trả draft hay tệp qua API này. Không mở quyền sửa lượt khám của bác sĩ khác. Danh sách phân trang 10 dòng, hoàn tất mới nhất trước. Đây là P41 một phần, chưa phải EMR bệnh nhân đầy đủ.
- Chẩn đoán mới chọn DiagnosisConcept trong danh mục ICD-10 Việt Nam theo phụ lục Thông tư 06/2026/TT-BYT, phiên bản có hiệu lực 2026-07-01. FINAL bắt buộc mã hợp lệ; PRELIMINARY được ghi nhận mô tả chưa có mã. Không cho tự nhập code/system/version. Server chốt mã, hệ mã, phiên bản và tên chính thức cùng mô tả lâm sàng riêng.
- Chặn mã ngừng dùng, mã có mã cụ thể hơn hoặc chỉ dùng nguyên nhân tử vong; mã cấm làm bệnh chính không được đánh dấu chính. Danh mục không thay bác sĩ lựa chọn chẩn đoán hay kiểm tra toàn bộ quy tắc chuyên môn. Mã cũ chưa rõ phiên bản giữ nguyên, không tự ánh xạ.
- Nguồn: [Cổng thông tin Chính phủ — Thông tư 06/2026/TT-BYT](https://chinhphu.vn/?classid=1&docid=217536&pageid=27160&typegroupid=6), phụ lục chính thức. Bộ dữ liệu lưu URL, SHA-256 và trang nguồn; không thu thập hàng loạt API trang tra cứu.