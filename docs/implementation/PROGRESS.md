# Tiến độ triển khai Suncare Clinic

Cập nhật: 2026-11-07

Việc hiện tại: P15.c — kiểm tra khoảng ngày và luồng P15/P16 trên browser
Trạng thái: IN_PROGRESS (lọc khoảng ngày trên DB thử đạt; browser host không khả dụng)

### P15 — tra cứu lịch khám trực tiếp (2026-11-07)

- Thêm `GET /api/appointments/onsite/options` và `/availability`, chỉ cho tài khoản PATIENT. Bộ lọc trả chuyên khoa đang hoạt động/liên kết với khoa khám và ca sáng/chiều đang hoạt động; truy vấn tra cứu chỉ lấy WorkAssignment ONSITE thuộc lịch PUBLISHED, còn sức chứa, bác sĩ và phòng đang hoạt động, đủ phân khoa/chuyên khoa, và có giá EXAMINATION hiệu lực phủ trọn ca.
- API nhận tùy chọn chuyên khoa/ngày đơn hoặc khoảng ngày/ca/tên bác sĩ; chỉ các tiêu chí gửi lên mới lọc. Kết quả là lịch tương lai theo từng bác sĩ, chuyên khoa và ca, kèm sức chứa còn lại và khoảng giá hiệu lực. Đếm Appointment BOOKED/COMPLETED/NO_SHOW vào sức chứa; chỉ lịch PUBLISHED, giá hiệu lực, ngày từ hôm nay trở đi và ngày Thứ Hai–Thứ Bảy được hiển thị. Đây chỉ là tra cứu, chưa tạo Appointment.
- Màn hình PATIENT theo flow: chọn hình thức khám rồi tới chuyên khoa, ngày, ca và danh sách bác sĩ. Mỗi thẻ bác sĩ có thể mở rộng lịch riêng; bên trong chọn thứ/ngày rồi xem ca sáng/chiều. Có ô tìm tên bác sĩ; chọn một buổi sẽ gắn đúng bác sĩ, hoặc có thể chuyển sang tự phân khi đặt. P16 nối lựa chọn vào giao dịch tạo Appointment. P15 chỉ mở nhánh trực tiếp; online đang khóa vì thuộc P31–P34. Bố cục toàn chiều ngang, căn trái: bộ lọc bên trái, danh sách bác sĩ ở vùng chính và tóm tắt bên phải; màn nhỏ xếp dọc. Trường trống không giới hạn kết quả; tra cứu bắt đầu khi có ít nhất một tiêu chí.
- Kiểm tra trên `SuncareClinicMigrationTest` schema V16: `OnsiteAppointmentSearchIT` 4/4 PASS, gồm bộ lọc khoảng ngày hai biên, khoảng đi qua Chủ Nhật, khoảng ngược/ngày quá khứ, quyền PATIENT, sức chứa/giá; fixture được dọn trong test. `OnsiteAppointmentBookingIT` 6/6 PASS. `npm run lint`, `npm run build`, `git diff --check` PASS theo lượt P16.d. Không ghi DB chính.
- CUA inventory phiên này không có browser, báo `The browser access host is unavailable` và `Computer Use native pipe is unavailable`; local Vite trả 200 và `/api/health` trả `UP`. Chưa xác minh trực quan responsive hoặc luồng tìm lịch trên browser. P15 giữ `IN_PROGRESS` tới khi có bằng chứng UI thực tế.
- Việc tiếp theo duy nhất: P15.c — kiểm tra preset/khoảng ngày và luồng P15/P16 trên browser khi host kết nối lại; không gửi booking lên DB chính.

### P15.b — bộ lọc khoảng ngày khám (2026-11-07)

- Thay bộ lọc một ngày bằng trường ngày bắt đầu/kết thúc mở popover. Có lối tắt `Ngày mai`, `7 ngày tới`, `30 ngày tới`, chọn ngày bằng lịch native của trình duyệt, áp dụng hoặc xóa khoảng; UI kiểm tra thứ tự và không cho chọn ngày quá khứ.
- Mở rộng API tra cứu với `dateFrom`/`dateTo`; truy vấn SQL lọc hai biên bao gồm cả ngày đầu/cuối và vẫn giữ tham số `date` đơn cũ cho tương thích. Khoảng ngày có thể đi qua Chủ Nhật; kết quả vẫn chỉ có lịch ONSITE đã công bố còn chỗ.
- Kiểm tra: `mvnw.cmd -DskipTests compile`, `npm run lint`, `npm run build`, `git diff --check` PASS. Chưa chạy `OnsiteAppointmentSearchIT` cho nhánh khoảng ngày và chưa kiểm tra browser; CUA báo browser host/native pipe không khả dụng. Không ghi DB.
- Việc tiếp theo duy nhất: P15.c — kiểm tra date range và responsive trên browser khi host khả dụng; API đã được kiểm tra trên DB thử.

### P16.a — API đặt lịch khám trực tiếp (2026-11-07)

- Thêm `POST /api/appointments/onsite/bookings`; request lấy Patient từ token, nhận chuyên khoa/ngày/ca và `doctorId` tùy chọn, không nhận giá hoặc `patientId`. Bác sĩ được gửi phải có lịch ONSITE đã công bố đúng ca, còn chỗ, đủ phân khoa/chuyên khoa và có giá khám hiệu lực phủ trọn ca. Khi bỏ `doctorId`, API chọn tỷ lệ lượt đặt/sức chứa thấp nhất, hòa theo `Staff.code`.
- Dùng khóa SQL Server theo khóa idempotency và chuyên khoa/ngày/ca; đọc lại sức chứa sau khóa. `CommandReceipt` giữ hash của payload và tài khoản thực hiện; retry cùng yêu cầu trả lại kết quả, dùng lại khóa với payload/tài khoản khác nhận 409. Appointment, Encounter PLANNED, hai event, audit và receipt ghi trong cùng transaction.
- `OnsiteAppointmentBookingIT`: 6/6 PASS trên `SuncareClinicMigrationTest`, schema V16. Bao gồm chọn bác sĩ/giá, tự phân và hòa theo mã, ngày không hợp lệ/sai quyền, retry/xung đột khóa, rollback khi bước Encounter lỗi, hai request giành chỗ cuối chỉ tạo một Appointment + Encounter. Truy vấn sau test xác nhận còn 0 tài khoản, bệnh nhân, khoa, chuyên khoa và Appointment fixture P16. Không ghi DB chính.
- P16.a backend hoàn tất; P15 vẫn IN_PROGRESS do chưa có bằng chứng browser.

### P16.b — UI đặt lịch khám trực tiếp (2026-11-07)

- Nối màn hình tìm lịch với `POST /api/appointments/onsite/bookings`. Người bệnh phải chọn một ca cụ thể; có thể giữ bác sĩ của ca đó hoặc chuyển sang hệ thống tự phân trong cùng chuyên khoa/ngày/ca. Tự phân bị khóa khi đang lọc theo tên bác sĩ để lựa chọn và ước tính không lệch khỏi tập lịch API sẽ dùng.
- Bố cục lịch trong từng bác sĩ: danh sách ban đầu chỉ có thẻ bác sĩ gọn; bấm `Xem lịch` mới mở ngày của bác sĩ đó. Chọn thứ/ngày trong thẻ mở rộng để xem riêng ca sáng/chiều, giờ, sức chứa và giá. Bấm lại buổi đang chọn hoặc nút `Bỏ chọn ca` để gỡ lựa chọn; đổi ngày trong cùng bác sĩ cũng bỏ ca cũ. API chỉ có ca sáng/chiều, không tự tạo giờ khám nhỏ hơn.
- Nới cột lịch ở giữa trên desktop và cho ngày tự xuống hàng ở breakpoint XL để tránh thanh cuộn ngang; màn hình hẹp vẫn vuốt ngang qua các ngày.
- Thêm bước xác nhận tóm tắt ca/bác sĩ/phí dự kiến, trạng thái đang gửi/lỗi/thành công và hiển thị mã Appointment, Encounter, bác sĩ được phân cùng phí đã chốt. Mỗi payload giữ `Idempotency-Key` ổn định khi thử lại; đổi ca/bác sĩ/bộ lọc tạo request mới. Có thể tìm lịch khác sau lỗi hoặc sau đặt thành công.
- Kiểm tra trong `frontend/`: `npm run lint`, `npm run build` và `git diff --check` PASS. Chưa gửi booking thật và không ghi DB chính.
- CUA inventory báo `The browser access host is unavailable` và `Computer Use native pipe is unavailable`; chưa quan sát responsive hoặc xác minh luồng với backend/DB thử. P16 giữ `IN_PROGRESS` tới khi kiểm tra UI thực tế.
- Việc tiếp theo duy nhất: kiểm tra P15/P16 trên browser với backend/DB thử: tìm lịch, chọn ca, đặt với bác sĩ cụ thể và tự phân, lỗi/thành công, cùng viewport 320/375/768/1280 px; không đặt lịch lên DB chính.

### P16.c — tóm tắt lịch đặt và triệu chứng tự khai (2026-11-07)

- Thay nội dung bên phải từ tóm tắt bộ lọc thành tóm tắt buổi đặt: phòng, khoa, chuyên khoa, bác sĩ, ngày/ca và phí. Sau khi đặt, API trả thông tin thực tế của bác sĩ/phòng/khoa được phân, nên chế độ tự phân không hiện nhầm phòng của bác sĩ ban đầu.
- Trả thêm tên khoa và phòng trong availability/booking response từ dữ liệu WorkAssignment hiện có; không thêm migration.
- Làm lựa chọn bác sĩ rõ hơn bằng hai thẻ lớn có trạng thái chọn: ưu tiên bác sĩ của ca hoặc để hệ thống cân bằng lượt/sức chứa trong cùng chuyên khoa/ngày/ca. Tự phân vẫn khóa khi đang lọc theo tên bác sĩ.
- Thêm ô triệu chứng/lý do khám tối đa 2.000 ký tự. Backend chuẩn hóa chuỗi, đưa nội dung vào hash idempotency và ghi `PrevisitResponse.symptoms` trong transaction đặt lịch cùng Appointment/Encounter/event; bỏ qua nếu để trống. Không dùng `Encounter.chief_complaint` vốn là nội dung lâm sàng của bác sĩ và không cần đổi schema.
- Kiểm tra sau sửa: `mvnw.cmd -DskipTests compile`, `npm run lint`, `npm run build`, `git diff --check` PASS. Không chạy test suite hoặc tạo booking; chưa kiểm tra trực quan trên browser trong lượt này.
- Điểm kiểm tra browser/DB thử còn lại được bàn giao ở P16.e sau điều chỉnh bố cục.

### P16.d — bỏ tiêu đề thừa và cuộn riêng cột lịch (2026-11-07)

- Bỏ khối đầu trang gồm “Lịch khám / Trực tiếp”, “Tìm lịch khám” và câu mô tả; giữ tiêu đề danh sách lịch trong cột giữa.
- Ở màn hình rộng, giới hạn màn tìm lịch trong chiều cao cửa sổ và tạo vùng cuộn dọc độc lập cho các cột. Cột giữa cuộn danh sách lịch; cột phải cuộn tóm tắt và nhập triệu chứng. Cột bộ lọc cũng có cuộn riêng để không bị cắt nếu nội dung cao. Màn hình hẹp tiếp tục cuộn trang theo chiều dọc.
- Đưa bảng chọn khoảng ngày ra portal cố định theo nút mở để menu không bị cắt bởi vùng cuộn của cột bộ lọc; vị trí cập nhật khi cuộn hoặc đổi kích thước cửa sổ.
- Kiểm tra: `npm run lint`, `npm run build`, `git diff --check` PASS. Chưa xác minh cuộn thực tế hoặc responsive trên browser; không chạy test suite.
- Việc tiếp theo duy nhất: P16.e — kiểm tra giao diện và cuộn từng cột trên browser ở 320/375/768/1280 px, đồng thời xác minh luồng đặt trên DB thử; không ghi DB chính.

### P15.c — kiểm tra lọc khoảng ngày trên DB thử (2026-11-07)

- Bổ sung kiểm tra tích hợp cho `dateFrom`/`dateTo`: hai biên đều bao gồm, khoảng bắt đầu Chủ Nhật đi qua ngày Chủ Nhật tới Thứ Tư chỉ trả các ca Thứ Hai/Thứ Tư có lịch, khoảng ngược trả `INVALID_DATE_RANGE`, ngày bắt đầu quá khứ trả `INVALID_WORK_DATE`.
- Trên `SuncareClinicMigrationTest` schema V16, `OnsiteAppointmentSearchIT` 4/4 PASS. `OnsiteAppointmentBookingIT` 6/6 PASS trong cùng phiên; truy vấn sau kiểm tra xác nhận 0 fixture P15/P16 ở WorkSchedule, WorkAssignment, ServicePrice và các bảng Patient/Department/Specialty/Staff/Account/Service/Room/Appointment; riêng P16 CommandReceipt cũng còn 0 dòng. Không ghi DB chính.
- Vite local trả 200 và `/api/health` trả `UP`. CUA không liệt kê browser; mở tab tới `http://localhost:5173` trả `The browser access host is unavailable`. Chưa kiểm tra preset/date picker, cuộn cột hoặc responsive 320/375/768/1280 px; chưa gửi booking qua UI.
- P15/P16 tiếp tục `IN_PROGRESS` đến khi xác minh trực quan. Việc tiếp theo duy nhất: kiểm tra UI P15/P16 trên browser với backend/DB thử và viewport 320/375/768/1280 px; tuyệt đối không tạo booking trên DB chính.

### P14 — công bố lịch và hỗ trợ phòng trực tuyến (2026-11-07)

- Hoàn thiện API công bố lịch DRAFT bằng transaction: kiểm tra lại nhân sự, khoa/phòng/chuyên khoa/ca/giá và xung đột; chuyển trạng thái PUBLISHED, khóa chỉnh sửa và sinh slot trực tuyến 20 phút. API/UI phân công nhận nhiều ô ngày/ca trong một lần; ONSITE và ONLINE được phân biệt rõ.
- Thêm V15 cho phòng ONLINE và V16 cho ràng buộc phân công ONLINE theo ca. Mở rộng API/UI quản lý phòng để tạo/lọc phòng ONLINE thuộc khoa CLINICAL; phòng CONSULTATION vẫn thuộc CLINICAL và LAB thuộc LAB.
- Kiểm tra trên `SuncareClinicMigrationTest`: Flyway xác nhận schema V16; `WorkScheduleIT` 2/2, `WorkSchedulePublishIT` 5/5 và `RoomManagementIT` 2/2 PASS. Các test gồm công bố/sinh slot, từ chối xung đột và dữ liệu không hợp lệ, phòng ONLINE đúng/sai loại khoa. Query sau test còn 0 fixture P09. `npm run lint`, `npm run build`, `git diff --check` PASS. Không ghi DB chính.
- Chưa xác minh thao tác giao diện và responsive vì CUA báo `The browser access host is unavailable`; chưa kiểm tra trực quan chọn nhiều ca, publish, hay phòng ONLINE. P14 tiếp tục `IN_PROGRESS` cho tới khi có kiểm tra browser.
- Việc tiếp theo duy nhất: khi browser host hoạt động, kiểm tra P14 trên DB thử: chọn nhiều ca, tạo phân công ONSITE/ONLINE, công bố và xác nhận slot; đồng thời kiểm tra form phòng ONLINE. Giữ P13 chưa DONE cho tới khi đo 320/375/768/1280 px và thử mở form rồi Hủy.

### P13 — thử tiếp browser responsive (2026-11-07)

- Xác nhận giao diện Vite tại `http://localhost:5173` trả 200, API `http://localhost:8080/api/health` trả `UP`, và health qua proxy Vite cũng trả 200. Không khởi động/dừng dịch vụ.
- CUA không liệt kê browser nào và báo `The browser access host is unavailable`; thử tạo tab riêng đến giao diện local cũng trả cùng lỗi. Vì vậy chưa quan sát viewport 320/375/768/1280 px, chưa bấm ô lịch, mở form hoặc Hủy.
- Không sửa code, không đăng nhập/ghi dữ liệu, không chạy kiểm thử tự động. P13 vẫn `IN_PROGRESS`; không dùng health/API và kiểm tra tĩnh thay cho bằng chứng responsive.
- Việc tiếp theo duy nhất: khi browser host hoạt động, mở tab riêng tới `http://localhost:5173`, đăng nhập tài khoản trưởng khoa thử nghiệm, kiểm tra 320/375/768/1280 px và thao tác mở ô → mở form → Hủy; không gửi form phân công.

### P14 — công bố lịch và tạo slot ONLINE (2026-11-04)

- V15 mở loại phòng ONLINE; V16 yêu cầu WorkAssignment ONLINE gắn ca sáng/chiều cấu hình và giữ V1–V15 bất biến. API `POST /api/head/schedules/{scheduleId}/publish` công bố trong transaction, kiểm tra phạm vi/nhiệm kỳ, role/chuyên khoa/khoa/phòng/ca/giá, giao lịch bác sĩ/phòng; sinh slot 20 phút chỉ trong từng ca và ghi audit. Retry publish trả lịch hiện hành; lịch đã công bố không cho sửa/gỡ phân công.
- Theo D25–D27, UI nhóm hàng sáng/chiều theo ONSITE và ONLINE; bấm chọn một ô, Ctrl/⌘-bấm thêm/bỏ, kéo chọn vùng, Ctrl/⌘-kéo cộng vùng. Selection giữ nguyên khi chuyển tuần; mỗi request gửi đúng từng cặp ngày/ca trong cùng một mode, không tạo tích chéo. API lưu danh sách ô cùng transaction. Chọn cả ngày ONLINE vẫn sinh slot theo từng ca, không có slot giờ nghỉ trưa. Danh sách bác sĩ và phòng lọc các phân công chồng giờ của đợt đang mở trong những ô đã chọn; backend tiếp tục chặn xung đột với lịch đã công bố. Cùng bác sĩ/phòng chỉ nhận một phân công tại một thời điểm, bất kể ONSITE/ONLINE.
- Kiểm tra trước khi đổi request sang `workSlots`: `$env:JAVA_TOOL_OPTIONS='-Djava.library.path=D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0'; .\mvnw.cmd '-Dtest=WorkSchedulePublishIT,WorkScheduleIT' test -q` PASS 7/7 trên `SuncareClinicMigrationTest` (publish/idempotency, slot theo đủ ca, xung đột, giá sai chuyên khoa, nhiều ngày rollback). Sau thay đổi `workSlots`, backend `mvnw -DskipTests compile` PASS; frontend sau lọc bác sĩ/phòng: `npm run lint`, `npm run build`, `git diff --check` PASS. Chưa chạy lại integration test. Flyway V15/V16 đã áp dụng trên DB thử; CHECK constraint đang bật/được tin cậy; sau test còn 0 fixture P14. Không ghi DB chính.
- Browser validation chưa chạy được: CUA báo `The browser access host is unavailable`; còn kiểm tra responsive 320/375/768/1280 px, chọn nhiều ngày, bật cả ngày và xác nhận publish. P14 giữ IN_PROGRESS.
- Bàn giao tại thời điểm 2026-11-04: kiểm thử API `workSlots`, rồi kiểm tra chọn ô qua 2–3 tuần, phân công và công bố trên browser khi host khả dụng.

### P13 — kiểm chứng tiếp (2026-11-04)

- Sửa fixture `WorkScheduleIT` theo quy tắc P07.d: trưởng khoa có cả role `DOCTOR`; thêm tài khoản `LAB_STAFF` riêng để giữ ca kiểm tra từ chối phân công người không phải bác sĩ.
- Danh sách đợt phân lịch được phân trang 5 đợt/trang, có nút trang nhanh và Trước/Sau; tìm kiếm/lọc đưa về trang đầu. Bảng không còn render toàn bộ lịch trong một vùng cuộn dọc, và sau khi tạo đợt mới màn hình mở đợt đó ở trang đầu.
- Cột `Hình thức · ca` được cố định 12rem trong bảng bố cục cố định để nội dung bác sĩ không ép hẹp cột nhãn; bảng vẫn cuộn ngang riêng khi màn hình nhỏ.
- Thời khóa biểu đặt hai hàng ONSITE cạnh nhau rồi hai hàng ONLINE. Bấm thường chọn một ô; Ctrl/⌘-bấm thêm/bỏ; giữ-kéo chọn khối ngày/ca; Ctrl/⌘-kéo cộng khối. Selection qua 2–3 tuần được giữ khi chuyển tuần; API nhận `workSlots` chính xác để không mở rộng ô rời rạc thành tích ngày × ca. Không có nút bật chọn nhiều; form liệt kê các ô đã chọn.
- Sau bổ sung click/Ctrl-click/drag và API `workSlots`, trong `frontend/`, `npm run lint` và `npm run build` PASS; backend `mvnw -DskipTests compile` PASS; `git diff --check` PASS. Chưa chạy integration tests cho `workSlots`/multi-shift ONSITE và chưa thử trực quan responsive vì browser host unavailable.
- Kiểm tra: trong `backend/`, `.\mvnw.cmd '-Dtest=WorkScheduleIT' test -q` PASS 1/1 trên `SuncareClinicMigrationTest`, schema V14; sau test, SELECT xác nhận còn 0 fixture `Staff`, `Account`, `Department` mã P13. Trong `frontend/`, `npm run lint` và `npm run build` PASS; `git diff --check` PASS. Không ghi DB chính.
- Kiểm tra browser bị chặn bởi `The browser access host is unavailable` khi lấy inventory và mở tab in-app. Vite đã khởi động riêng rồi dừng; chưa xác minh 320/375/768/1280 px, chọn ô ca, mở form và Hủy.
- P13 vẫn IN_PROGRESS cho tới khi kiểm tra responsive/tương tác trên browser. P14 đã được bắt đầu riêng theo yêu cầu ngày 2026-11-04; không xem đây là bằng chứng hoàn tất P13.

### P07.d — ràng buộc quyền trưởng khoa với vai trò bác sĩ (2026-11-04)

- `DEPARTMENT_HEAD` là quyền bổ sung của bác sĩ; `DepartmentHeadAssignment` vẫn giữ khoa và nhiệm kỳ. API tạo/gán role từ chối trưởng khoa nếu thiếu `DOCTOR`, API phân công xác nhận cả hai role. Không cho thu hồi `DOCTOR` khi còn role trưởng khoa hoặc nhiệm kỳ chưa kết thúc; các thao tác role/phân công cùng khóa Staff để tránh race.
- `/api/auth/me` không trả role/quyền trưởng khoa cho dữ liệu cũ thiếu role bác sĩ; tài khoản đó vẫn cần admin sửa bằng cách thêm đúng role bác sĩ hoặc thu hồi quyền trưởng khoa.
- UI tự thêm role bác sĩ khi chọn quyền trưởng khoa, ngăn gỡ bác sĩ trước quyền trưởng khoa, và giải thích phân công quyết định khoa/thời hạn. Cập nhật `AGENTS.md` cùng docs 01/03/04/05/06; thay TX01 dùng nhầm “role STAFF” bằng account type `STAFF`.
- Kiểm tra: `StaffManagementIT` 13/13 và `StaffAssignmentIT` 1/1 PASS trên `SuncareClinicMigrationTest` (schema V14, Flyway không chạy migration); `npm run lint`, `npm run build` và `git diff --check` PASS. Kiểm tra SQL sau test còn 0 fixture P07/P10. Không ghi DB chính; chưa kiểm tra trực tiếp UI/responsive trên browser.
- Bàn giao tại thời điểm 2026-11-04: P13 — kiểm tra responsive 320/375/768/1280 px và tương tác bảng/form phân công trên browser riêng; không lưu phân công lên DB chính.

### Bổ sung khoa/chuyên khoa vào profile và quản lý nhân viên (2026-11-02)

- Profile nhân viên lấy khoa đang có hiệu lực và chuyên khoa đang hoạt động từ API; mục thông tin chuyên môn hiển thị rõ trạng thái chưa được phân/gán nếu chưa có dữ liệu.
- API danh sách nhân viên quản trị trả thêm khoa/chuyên khoa hiện hành; bảng, tìm kiếm, thẻ màn hình nhỏ và chi tiết nhân viên cùng hiển thị các trường này. Đây là thông tin đọc, không đổi luồng phân công.
- Kiểm tra: `PersonalProfileIT` 3/3 và `StaffManagementIT` 10/10 PASS trên `SuncareClinicMigrationTest`; `npm run lint`, `npm run build`, `git diff --check` PASS. Không ghi dữ liệu lên DB chính; chưa kiểm tra giao diện trực tiếp trên browser.

### P13 — viết lại luồng phân lịch sau lỗi 403 (2026-11-02)

- Thay luồng UI P13 cũ: nạp độc lập danh sách đợt, quyền/khoa, chi tiết đợt và options; lỗi một API không xóa dữ liệu API khác. Lọc vai trò/chuyên khoa theo mã ID ổn định; báo lỗi quyền, lỗi tải dữ liệu và trạng thái chưa có nhiệm kỳ riêng.
- Viết lại lịch tuần thành thời khóa biểu toàn chiều ngang: hai hàng sáng/chiều, mỗi ô ngày/ca bấm được; bảng chi tiết bác sĩ mở dưới lịch, có tìm/lọc, thêm phân công và nút Hủy khi nhập dở. Lịch đã công bố chỉ xem; bảng cuộn ngang trong vùng riêng trên màn hình hẹp.
- Backend dùng `SCHEDULE_PUBLISH_DEPARTMENT` thống nhất giữa security route và service; trả vai trò/chuyên khoa dạng cấu trúc. Trưởng khoa hiện tại được xem lịch trong khoa mình; tạo/sửa vẫn yêu cầu nhiệm kỳ bao trọn thời gian đợt.
- Kiểm tra: `WorkScheduleIT` 1/1 PASS trên `SuncareClinicMigrationTest`, gồm quyền trưởng khoa/bác sĩ/ẩn danh, đọc danh sách đợt, chặn xem khoa khác, tạo DRAFT, options có mã vai trò/chuyên khoa, phân công hợp lệ, từ chối sửa khi nhiệm kỳ chưa có hiệu lực và các trường hợp sai khoa/phòng/chuyên khoa/ca/ngày/sức chứa/trùng. Sau test, query DB thử thấy 0 fixture Staff/Account/Department/WorkSchedule mã P13. `npm run lint`, `npm run build`, `git diff --check` PASS. Không đổi migration hoặc ghi DB chính.
- DB chính (chỉ SELECT) xác nhận `demo_head` ACTIVE, role `DEPARTMENT_HEAD`, permission `SCHEDULE_PUBLISH_DEPARTMENT`, nhiệm kỳ khoa lâm sàng `NGOAI` hiệu lực đến 2026-12-31 17:00 UTC. API thật gọi đúng `GET /api/head/schedules` đã trả 200; nguyên nhân 403 trên giao diện được xác nhận sau đó là URL frontend bị nối thành `/api/head/schedulesundefined`, không phải thiếu quyền.
- Khởi động lại bằng `.\mvnw.cmd '-Dspring-boot.run.profiles=local' spring-boot:run` với Native Authentication trỏ tới thư mục `D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0`. PID mới 28972; `/api/health` trực tiếp ở 8080 và qua proxy Vite 5173 đều UP.
- Smoke test API với đăng nhập `demo_head`: login 200; `/api/auth/me` có permission phân lịch; `GET /api/head/schedules` 200 (0 đợt); options 200 (1 khoa, có `NGOAI`); options chi tiết 200 (2 nhân viên, 2 phòng, 2 chuyên khoa, 2 ca). Logout 204; số token đang hoạt động trước/sau vẫn là 6. Không tạo/sửa lịch trên DB chính.
- API smoke trước khi sửa URL, trên PID 8768: login `demo_head` 200; `/api/auth/me` 200 có `SCHEDULE_PUBLISH_DEPARTMENT`; gọi đúng danh sách đợt 200 (0 đợt); options 200 có khoa `NGOAI`; logout thành công. Không tạo/sửa dữ liệu lịch.
- Sửa nguyên nhân 403: `listHeadSchedules()` gọi `request()` không có `path`, tạo URL `/api/head/schedulesundefined`; Spring Security áp dụng `denyAll()` và trả 403. `request` nay mặc định `path = ''`, nên gọi đúng `/api/head/schedules`.
- Sửa xử lý lỗi P13: với HTTP 403, frontend kiểm tra lại `/api/auth/me` bằng token đúng tab và phân biệt thiếu quyền với trường hợp `/me` có quyền nhưng API P13 vẫn từ chối; màn hình hiện tài khoản, endpoint, HTTP status và nút đăng nhập lại. HTTP 401 tự xóa token cũ, chuyển về form đăng nhập và giải thích phiên đã hết hạn. Không còn hiển thị “chưa có đợt” khi API danh sách tải lỗi.
- Sau khi chủ dự án bật lại backend, PID 57144 hiện giữ cổng 8080; health trực tiếp và qua proxy 5173 đều UP. Smoke test sau sửa, qua proxy: login 200; `/api/auth/me` 200 có quyền; `/api/head/schedules` 200; `/api/head/schedules/options` 200; đường dẫn lỗi cũ `/api/head/schedulesundefined` trả 403 như dự kiến. Logout thành công; không tạo/sửa lịch trên DB chính. Giữ nguyên tiến trình PID 57144.
- Bổ sung giới hạn ngày tạo đợt theo nhiệm kỳ đang chọn: tính từ `validFrom`/`validTo` UTC sang ngày Saigon và bắt buộc đợt bao trọn ngày; `validFrom` 02/11/2026 17:41 làm ngày bắt đầu sớm nhất là 03/11, `validTo` 01/01/2027 00:00 làm ngày kết thúc muộn nhất là 31/12. Form khóa nút tạo nếu nhiệm kỳ không bao trọn ngày nào; server vẫn là nguồn kiểm tra cuối.
- Sau khi đổi giao diện thời khóa biểu: `npm run lint`, `npm run build`, `git diff --check` PASS. Ảnh chụp browser hiện có ở desktop 1536×864 cho thấy trang dùng chiều ngang đầy đủ, đủ 6 ngày và hai hàng sáng/chiều; thấy đợt DRAFT và ô ca được chọn, không còn thông báo 403. Không thao tác trên tab Edge đang chia sẻ Meet và không ghi DB chính. Chưa kiểm tra 320/375/768/1280 px hoặc trực quan phần bảng chi tiết/form Hủy bên dưới màn hình; P13 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: kiểm tra responsive 320/375/768/1280 px và bấm một ô để xem bảng phân công, thử mở form rồi Hủy trong browser riêng; không lưu phân công lên DB chính. `JwtTokenService` tự sinh khóa JWT mới sau mỗi lần khởi động nếu thiếu `SUNCARE_JWT_SECRET`, nên phiên cũ cần đăng nhập lại. Chỉ ONSITE theo V3; ONLINE thuộc migration D24 và publish thuộc P14.

### P13 — phiên bản đầu trước khi viết lại (2026-11-02)

- Thêm `/api/head/schedules`: trưởng khoa có thể tạo đợt DRAFT, xem lịch sử trong khoa đang phụ trách, tải WorkAssignment, thêm và gỡ phân công khi bản còn DRAFT.
- Backend kiểm tra role và nhiệm kỳ trưởng khoa bao phủ toàn kỳ, phòng CONSULTATION cùng khoa, bác sĩ đang thuộc khoa và có chuyên khoa phù hợp, ca đang hoạt động, ngày Thứ Hai–Thứ Bảy, sức chứa dương và giao lịch bác sĩ/phòng trong đợt hoặc lịch PUBLISHED. Lịch làm việc chuyển từ giờ Saigon sang UTC khi lưu. Audit cùng transaction.
- UI có lịch tuần Thứ Hai–Thứ Bảy, chọn đợt trong lịch sử, bảng nhân viên có tìm kiếm/lọc vai trò/chuyên khoa và form phân công theo ngày, ca, chuyên khoa, phòng, sức chứa. Đợt DRAFT sửa được; lịch đã công bố chỉ xem.
- Giới hạn: chỉ ONSITE theo cấu trúc V3 hiện tại; phòng ONLINE chưa bật vì cần migration D24. Chưa có thao tác publish (P14).
- Kiểm tra: backend compile PASS; `WorkScheduleIT` 1/1 PASS trên `SuncareClinicMigrationTest`, xác nhận anonymous 401, bác sĩ 403, trưởng khoa tạo đợt/phân công, chặn sai role, bác sĩ ngoài khoa, phòng khoa khác, chuyên khoa/ca không hợp lệ, ngày ngoài đợt, Chủ Nhật, sức chứa 0 và phân công trùng; đọc lịch và gỡ assignment. Fixture được dọn qua `@AfterEach`. Không sửa V1–V14 hoặc DB chính.
- Lượt rà soát tiếp theo 2026-11-02: sửa lỗi sau khi tạo/làm mới đợt lịch, options theo khoa không còn bị reset về danh sách rỗng; bổ sung kiểm tra API âm cho role, phạm vi khoa/phòng, specialty, shift, ngày và capacity.
- Kiểm tra phiên này: `WorkScheduleIT` 1/1 PASS trên `SuncareClinicMigrationTest`; `npm run lint`, `npm run build`, `git diff --check` PASS. Truy vấn DB thử sau test xác nhận còn 0 Staff/Department/Account/Room/WorkSchedule có mã P13. Không ghi DB chính. Backend/Vite thử đã dừng; cổng 8080/5173 của người dùng không bị động tới.
- Còn lại: chưa kiểm tra tương tác/responsive thực tế trên browser. Cầu nối báo `The browser access host is unavailable`; Edge hiện có cửa sổ đang chia sẻ Meet nên không dùng cửa sổ đó. P13 giữ IN_PROGRESS đến khi kiểm tra UI 320/375/768/1280 px và luồng thực tế. ONLINE cần migration D24 và publish thuộc P14.

### Rà soát thay đổi Antigravity (2026-11-02)

- Tại thời điểm rà soát trước khi bắt đầu P13, phần Antigravity mới trong working tree là P12 (ca làm việc và giá khám); chưa có mã lịch P13. Trạng thái hiện tại cập nhật ở mục P13 phía trên.
- Gọn UI P12: bỏ hai đoạn giải thích quy tắc dài dưới tiêu đề, bỏ hướng dẫn lặp lại hành động trong empty state và bỏ wrapper thừa quanh tiêu đề. `npm run lint`/`npm run build` PASS; backend không đổi trong lượt rà này.
- Cần xử lý trước khi dùng vận hành: P12 vẫn có thể khởi tạo giờ mẫu cố định 07:30–11:30 và 13:30–17:30 dù giờ thật chưa được chốt. P13.a chỉ nhận ca đang hoạt động; không xem giờ khởi tạo mẫu là chính sách thật.

### Sửa tiếp P10 — kết thúc phân công (2026-11-02)

- Hộp thoại kết thúc nhiệm kỳ/phân khoa thay `prompt`, hiển thị khoảng hiệu lực hiện tại và kiểm tra mốc kết thúc phải sau ngày bắt đầu, trước ngày kết thúc cũ. Các lỗi giữ nguyên trong hộp thoại để sửa.
- Nếu kết thúc phân khoa bị nhiệm kỳ trưởng khoa chặn, API trả tên nhân viên cùng thời gian bắt đầu/kết thúc theo giờ Việt Nam; chỉ báo các nhiệm kỳ thật sự giao với mốc kết thúc phân khoa.
- Kiểm tra: `StaffAssignmentIT` 1/1 PASS trên `SuncareClinicMigrationTest`; xác nhận có đủ mốc thời gian xung đột và ngày kết thúc trước ngày bắt đầu bị chặn với lỗi dễ hiểu. `npm run lint`, `npm run build`, `git diff --check` PASS. Không ghi DB chính; chưa chạy lại browser.

### Điều chỉnh UI P08 theo yêu cầu (2026-11-02)

- Màn hình khoa/chuyên khoa chuyển từ các thẻ dài sang bảng có tìm mã/tên và lọc trạng thái. Khoa khám mở hàng chi tiết để xem/bật/ngừng liên kết chuyên khoa; form thêm/sửa nằm trong hộp thoại, có đóng bằng Escape, giữ focus trong hộp thoại và trả focus về nút mở.
- `npm run lint` và `npm run build` PASS sau sửa. Chưa kiểm tra tương tác và kích thước màn hình trên browser vì cầu nối browser phiên trước báo không khả dụng; bảng có vùng cuộn ngang riêng trên màn hình hẹp. Không thay API/DB. P08 vẫn DONE theo phạm vi nghiệp vụ đã chốt; đây là cải tiến cách trình bày.

### P12 — Giá khám theo bác sĩ/hình thức và ShiftDefinition (2026-11-02)

- **Ca làm việc (ShiftDefinition)**:
  - Thêm DTO (`ShiftDefinitionResponse`, `ShiftDefinitionCreateRequest`, `ShiftDefinitionUpdateRequest`), Repository, Service và Controller (`/api/admin/scheduling/shifts`) trong module `scheduling`.
  - Hỗ trợ xem danh sách, khởi tạo ca chuẩn (MORNING "Ca sáng" 07:30 - 11:30, AFTERNOON "Ca chiều" 13:30 - 17:30) khi bảng trống, cập nhật tên/giờ/trạng thái với khóa lạc quan (`version`), kiểm tra `local_end > local_start` và ghi `AuditEvent` cùng transaction.
  - Phân quyền: Yêu cầu quyền `CATALOG_MANAGE` ở SecurityConfig và Service.

- **Giá khám theo bác sĩ & hình thức (Consultation Pricing)**:
  - Sử dụng bảng `dbo.ServicePrice` theo schema V3: dịch vụ `EXAMINATION` yêu cầu cả `doctor_id` và `visit_mode` (ONSITE / ONLINE).
  - Thêm DTO (`ConsultationPriceResponse`, `ConsultationPriceCreateRequest`, `DoctorOptionResponse`).
  - Mở rộng `ClinicalServiceRepository`, `ClinicalServiceManagementService`, `ClinicalServiceController`:
    - `GET /api/admin/services/doctors`: lấy danh sách bác sĩ đang hoạt động (role DOCTOR, status ACTIVE).
    - `GET /api/admin/services/consultation-prices`: tra cứu toàn bộ giá khám với các bộ lọc serviceId, doctorId, visitMode, activeOnly.
    - `GET /api/admin/services/{id}/consultation-prices`: danh sách giá khám của một dịch vụ.
    - `POST /api/admin/services/{id}/consultation-prices`: thiết lập mức giá khám mới; kiểm tra dịch vụ EXAMINATION, bác sĩ DOCTOR active, visit_mode hợp lệ, amount > 0, valid_to > valid_from, và chống giao khoảng hiệu lực (`PRICE_OVERLAP`) bằng khóa bi quan `WITH (UPDLOCK, HOLDLOCK)`.
    - `PUT /api/admin/services/{id}/consultation-prices/{priceId}/end`: kết thúc mức giá sớm hơn thời điểm chốt, chống hồi tố về quá khứ và kiểm tra version.
  - Ghi nhận `AuditEvent` trong cùng transaction cho mọi thao tác.

- **Frontend UI & API**:
  - Thêm `frontend/src/services/schedulingCatalogApi.js` gọi các endpoint ca làm việc và giá khám.
  - Thêm màn hình `frontend/src/features/scheduling/SchedulingCatalogManagement.jsx`:
    - Tab 1: Quản lý khung ca khám (ShiftDefinition), xem khung giờ, nút khởi tạo ca chuẩn, modal chỉnh sửa ca có kiểm tra giờ hợp lệ và optimistic lock.
    - Tab 2: Quản lý bảng giá khám bệnh theo bác sĩ & hình thức khám, bộ lọc theo dịch vụ/bác sĩ/hình thức/chỉ xem giá đang áp dụng/tìm kiếm nhanh, modal thiết lập giá khám mới có live preview VND và validation, modal kết thúc giá khám sớm.
    - Tích hợp điều hướng "Ca & giá khám" trong `App.jsx` khi tài khoản có quyền `CATALOG_MANAGE`.

- **Kiểm tra**:
  - Thêm `backend/src/test/java/com/suncare/clinic/modules/scheduling/SchedulingCatalogIT.java` chạy trên `SuncareClinicMigrationTest`: 2/2 PASS (kiểm tra toàn diện ShiftDefinition và ConsultationPricing: từ chối bác sĩ thiếu quyền, khởi tạo ca chuẩn, lỗi giờ không hợp lệ, cập nhật và xung đột concurrency 409; tạo giá khám EXAMINATION, lỗi amount/mode/period, tạo giá thành công, chặn overlap 409, cho phép khác visit_mode hoặc khác doctor, kết thúc giá sớm).
  - Chạy lại `ClinicalServiceIT`: 5/5 PASS (không có regression).
  - Chạy lại default backend tests `mvn test`: 8/8 PASS.
  - Frontend: `npm run lint` PASS (0 errors), `npm run build` PASS (Vite build 422ms).
  - Chưa kiểm tra tương tác/responsive trên trình duyệt trực tiếp với backend test, giữ đúng nguyên tắc của repo.

### Rà soát P11 (2026-11-02)

- Đối chiếu API, UI, DTO, schema V3 và tài liệu nghiệp vụ. Sửa tra giá mặc định dùng UTC thay vì múi giờ máy chủ; thao tác kết thúc giá chỉ cho rút ngắn thời hạn và không hồi tố về quá khứ. Bổ sung kiểm tra từ chối kéo dài khoảng giá.
- Kiểm tra trên `SuncareClinicMigrationTest`: `ClinicalServiceIT` 5/5 PASS trước ca kiểm tra mới; chạy lại riêng `ClinicalServiceIT#managesClsPricesAndValidatesNonOverlappingValidity` PASS sau sửa cuối. Chưa kiểm tra luồng UI và responsive nên P11 vẫn IN_PROGRESS. Không sửa DB chính.
- Rà tiếp frontend: form giá gửi chuỗi thập phân nguyên vẹn cho `BigDecimal` thay vì ép `Number` dễ mất độ chính xác. `npm run lint` và `npm run build` PASS sau sửa. Cầu nối browser báo `The browser access host is unavailable`, nên chưa xác nhận luồng tương tác và 320/375/768/1280 px; không coi build là bằng chứng browser.
- Việc tiếp theo: kiểm tra UI P11 qua API thật trên DB thử, lint/build và responsive; sau đó mới chốt P11.

### Chốt P10 — kiểm tra UI và sửa ngày hiển thị (2026-11-02)

- Dùng backend `migrationtest` cổng 8081 và Vite cổng 5174, tách khỏi phiên `local` cổng 8080/5173. Tạo tài khoản, khoa, chuyên khoa giả P10 trong DB thử; chạy browser headless Edge qua UI thật: đăng nhập, tạo phân khoa, lỗi 409 khi gán trùng khoảng, gán trưởng khoa và chuyên khoa, tải lại thấy lịch sử, kết thúc nhiệm kỳ/phân khoa và ngừng chuyên khoa. Không có lỗi JavaScript.
- Phát hiện và sửa ngày lịch sử `Invalid Date`: `Timestamp` trong danh sách JDBC bị JSON chuyển múi giờ thêm một lần; API nay trả chuỗi thời điểm UTC, UI hiển thị đúng giờ Việt Nam. Cho phép kết thúc sớm phân công có hạn tương lai. Kiểm tra 320/375/768/1280 px không tràn ngang; đã xem ảnh 320 và 1280 px.
- Sau sửa cuối: `StaffAssignmentIT` 1/1 PASS trên `SuncareClinicMigrationTest`, gồm định dạng UTC ở GET endpoint; `npm run lint` và `npm run build` PASS. Dọn fixture P10, xác nhận còn 0 Staff/Department/Specialty/Account thử. Đã dừng đúng backend/Vite thử; phiên `local` của người dùng vẫn chạy. Không sửa DB chính. P10 DONE; chưa thử hai kết nối ghi đồng thời, để bộ kiểm chứng cạnh tranh P45.

### Yêu cầu đã nhận cho P13–P14 (2026-11-02)

- Trưởng khoa phân lịch và xem lịch sử chỉ trong khoa mình đang phụ trách. Màn hình phân lịch chia hai phần nhìn đồng thời: calendar từ thứ Hai đến thứ Bảy để đặt/xem ca, và bảng nhân viên có tìm kiếm/bộ lọc theo thuộc tính nhân viên (mã, tên, vai trò, khoa, chuyên khoa, trạng thái khi dữ liệu API hỗ trợ). Chọn ngày bắt đầu và ngày kết thúc của đợt phân ca; không tự đặt ca vào Chủ nhật.
- Mỗi lần phân ca là một đợt lịch riêng (`WorkSchedule` với các `WorkAssignment` thuộc đợt đó). Danh sách lịch sử hiển thị từng đợt; chọn một đợt sẽ nạp đúng các phân công của đợt ấy lên calendar, kể cả khi đã công bố. Cần giữ bản đã công bố và các phân công lịch sử, không gộp các đợt thành một calendar không phân biệt nguồn.
- Lịch được lưu ở trạng thái DRAFT trước khi công bố; P14 xử lý publish và khóa kiểm tra xung đột. Không coi bản nháp là lịch bệnh nhân có thể đặt.
- Chủ dự án đã chốt: phòng ONLINE là tài nguyên được quản lý và phân lịch như phòng vật lý (D24). Hiện V2 chỉ cho Room CONSULTATION/LAB, V3 bắt WorkAssignment ONLINE có `room_id = NULL`, và API P09 cũng chỉ cho hai loại phòng cũ. Khi tới phần triển khai phòng/lịch online, cần migration mới để thêm loại Room ONLINE và đổi CHECK WorkAssignment, đồng thời cập nhật API/UI phòng, kiểm tra chống giao lịch cùng phòng. Không sửa V2/V3 đã áp dụng; chưa triển khai thay đổi schema trong lượt ghi nhận này.
- Phụ thuộc: hoàn tất P10, P11, P12 trước P13 theo roadmap; giờ ca và sức chứa thực tế cần chốt tại P12. Không đánh dấu P13/P14 đã triển khai từ ghi chú này.

### P10 — phân khoa, chuyên khoa và trưởng khoa (2026-11-02)

- Thêm API `/api/admin/staff-assignments` để xem lịch sử, tạo/kết thúc phân khoa và nhiệm kỳ trưởng khoa, bật/ngừng chuyên khoa. `ACCOUNT_MANAGE` được kiểm tra ở Security filter và service. Phân khoa khóa hàng Staff để tránh ghi khoảng hiệu lực chồng nhau; trưởng khoa khóa Department, yêu cầu role `DEPARTMENT_HEAD`, khoa khám và khoảng phân khoa bao phủ nhiệm kỳ. Chuyên khoa yêu cầu bác sĩ, phân khoa hiện hành và liên kết khoa/chuyên khoa. Mọi thay đổi có audit cùng transaction.
- Thêm màn hình Phân công, dùng API thật, hiển thị trạng thái tải/rỗng/lỗi/thành công; ngày nhập theo giờ Việt Nam và gửi UTC. `GET /api/admin/staff` bổ sung `staffId` cho form phân công.
- Kiểm tra: backend compile PASS; `StaffAssignmentIT` 1/1 PASS trên `SuncareClinicMigrationTest` sau khi sửa fixture `granted_at`; frontend `npm run lint` và `npm run build` PASS. Chưa thử UI tương tác/responsive trên browser, chưa chạy test cạnh tranh hai kết nối; không xem các phần này là PASS.
- Không thay schema hoặc dữ liệu DB chính. Chưa chốt P10 DONE vì thiếu kiểm tra browser với tài khoản quản trị trên DB thử.
- Tiếp tục ngày 2026-11-02: đối chiếu D19, D24 và tài liệu 03–05; cho phép kết thúc phân công cũ khi Staff đã ngừng hoạt động, vẫn chặn gán mới. Bổ sung test thiếu `ACCOUNT_MANAGE`, chặn kết thúc phân khoa khi nhiệm kỳ trưởng khoa còn hiệu lực, đăng nhập và GET endpoint thực có xác thực. `StaffAssignmentIT` 1/1 PASS trên `SuncareClinicMigrationTest`; sau test còn 0 fixture P10 Staff/Department/Specialty. Chưa kiểm tra browser do cầu nối trình duyệt báo `The browser access host is unavailable`; không sử dụng cửa sổ Edge đang có phiên họp của người dùng. Backend profile `local` ở cổng 8080 trỏ DB chính, không dùng để ghi dữ liệu thử.

### P09 — quản lý phòng (2026-11-02)

- Thêm `GET/POST /api/admin/organization/rooms`, `PUT /api/admin/organization/rooms/{id}` trong module `organization`, chỉ `CATALOG_MANAGE`. Phòng `CONSULTATION` chỉ thuộc khoa `CLINICAL`, phòng `LAB` chỉ thuộc khoa `LAB` đang hoạt động; mã duy nhất, sửa dùng `rowversion`, ngừng hoạt động thay xóa. Giữ khoa và loại phòng bất biến sau tạo để không đổi nghĩa lịch sử sử dụng; audit cùng transaction.
- Thêm `RoomManagement.jsx` và `roomApi.js`, điều hướng Phòng cho người có `CATALOG_MANAGE`; form tạo/sửa và lọc theo khoa, loại, trạng thái, có tải/rỗng/lỗi/thành công. `RoomManagementIT` 2/2 PASS trên `SuncareClinicMigrationTest`: loại/khoa sai, mã trùng, version cũ, khoa ngừng hoạt động, anonymous/thu hồi quyền, rollback khi audit lỗi. Backend `.\mvnw.cmd test -q`, frontend `npm run lint`/`npm run build` PASS; `git diff --check` không lỗi whitespace. Sau test còn 0 fixture Account/Department/Room `P09`.
- Chưa thử thao tác UI/responsive trên browser với backend thử, nên P09 tiếp tục IN_PROGRESS theo điều kiện DONE của repo. Không có migration mới cho P09 và không ghi dữ liệu P09 lên DB chính.
- Việc tiếp theo duy nhất: P09 — thử UI phòng với backend thử tại 320/375/768/1280 px, gồm tạo/sửa/lọc, lỗi và sai quyền; sửa lỗi phát hiện rồi chốt DONE nếu đạt.

### P08 — xác nhận hoàn thành và chuyển P09 (2026-11-02)

- Chủ dự án xác nhận P08 đã xong và yêu cầu sang P09. Chuyển P08 sang DONE theo xác nhận này; các kiểm tra browser không có log từ agent vẫn là chưa được agent xác minh, không ghi thành kết quả test mới.

### P08 — khoa, chuyên khoa và liên kết (2026-11-02)

- Theo yêu cầu trực tiếp của chủ dự án, triển khai P08 khi P07.c còn thiếu kiểm tra browser; không đổi trạng thái P07. Thêm API `GET /api/admin/organization`, tạo/sửa Department và Specialty, bật/ngừng liên kết DepartmentSpecialty. Tất cả cần `CATALOG_MANAGE`, validate mã/tên/loại, giữ `rowversion` khi sửa, ghi audit cùng transaction; ngừng hoạt động thay vì xóa danh mục.
- V14 bổ sung `is_active`, `updated_at`, `version` cho DepartmentSpecialty để ngừng/kích hoạt lại liên kết mà giữ dòng lịch sử. V14 đã áp dụng và xác nhận `success=1` trên `SuncareClinicMigrationTest` rồi `SuncareClinic`; chỉ tiến trình tạm cổng 8081 được dừng, tiến trình người dùng ở 8080/5173 được giữ nguyên. Cập nhật từ điển dữ liệu cho cột mới.
- UI `OrganizationManagement.jsx` và `organizationApi.js` cho danh sách, tạo/sửa khoa và chuyên khoa, liên kết/ngừng liên kết; có tải, rỗng, lỗi, thành công, xung đột và lọc quyền ở điều hướng. `OrganizationCatalogIT` 2/2 PASS trên DB thử: mã trùng, input sai, anonymous/thu hồi quyền, version cũ, liên kết ngừng rồi kích hoạt lại không nhân dòng và rollback khi audit lỗi. Kiểm tra sau test còn 0 Account/Department/Specialty `P08` thử. Backend `.\mvnw.cmd test -q`, frontend `npm run lint`/`npm run build` PASS; `git diff --check` không có lỗi whitespace.
- Chưa chạy thao tác UI hoặc đo responsive 320/375/768/1280 px trên browser: browser connector báo `The browser access host is unavailable`; Edge đang được người dùng chia sẻ màn hình. Theo điều kiện DONE của repo, P08 tiếp tục IN_PROGRESS. Backend 8080 đang chạy từ trước, cần nạp code mới để thấy API P08 khi thử UI.
- Sửa trạng thái rỗng của chuyên khoa chiếm đủ hai cột trên màn hình rộng, thống nhất chiều rộng với trạng thái rỗng của khoa. Frontend lint/build PASS sau sửa; chưa kiểm tra trực quan trên browser.
- Việc tiếp theo duy nhất: P08 — chạy backend/frontend thử và xác minh toàn luồng UI khoa/chuyên khoa, liên kết, lỗi 409, sai quyền và responsive trên browser; sửa lỗi phát hiện rồi chốt DONE nếu đạt.

### P07.c — tinh chỉnh giao diện quản lý nhân viên (2026-11-02)

- Chuẩn hóa nút, nhãn trạng thái/vai trò và hộp xác nhận thành `components/ui/Button.jsx`, `Badge.jsx`, `ConfirmDialog.jsx`; `StaffManagement.jsx` sử dụng các thành phần này, thay màu viết trực tiếp bằng token Sunflower Clinical và giữ nguyên lời gọi API/quyền thao tác.
- Đặt lại phân trang ngay khi người dùng đổi bộ lọc; loại bỏ effect cập nhật state không cần thiết. Tại `frontend/`, `npm run lint` PASS và `npm run build` PASS; `git diff --check` không có lỗi whitespace.
- Chưa chạy thử tương tác và responsive trên browser với backend `migrationtest`; P07 tiếp tục IN_PROGRESS. Việc tiếp theo duy nhất: P07.c — thử trực quan và toàn luồng UI admin trên browser với database thử ở 320/375/768/1280 px rồi chốt P07 nếu đạt.
- Lượt tiếp theo: thay bộ SVG tự viết trong `StaffManagement.jsx` bằng Font Awesome đã có trong ứng dụng. `npm run lint`, `npm run build` và `git diff --check` PASS sau thay đổi. Browser connector trả `The browser access host is unavailable`; môi trường frontend không có Playwright/Puppeteer. Edge hiện đang hiển thị cuộc họp của người dùng nên chưa dùng cửa sổ đó để thao tác. Chưa có bằng chứng browser cho P07.c; giữ nguyên IN_PROGRESS và việc tiếp theo nêu trên.
- Theo phản hồi của chủ dự án, thêm hiệu ứng mở/đóng cho panel tạo và quản lý nhân viên cùng lớp nền; chỉ tháo panel sau hiệu ứng đóng, tôn trọng `prefers-reduced-motion`. `npm run lint`, `npm run build` và `git diff --check` PASS sau chỉnh sửa. Chưa xem hiệu ứng trực tiếp trên browser; P07.c vẫn IN_PROGRESS, việc tiếp theo là kiểm tra browser trên database thử và các kích thước responsive.
- Theo yêu cầu mới, panel quản lý hiển thị đầy đủ thông tin Staff do API trả về và có nút `Chỉnh sửa`; form chỉ sửa tên, ngày sinh, giới tính, liên hệ, địa chỉ và thông tin chuyên môn. `PUT /api/admin/staff/{accountId}` cần `ACCOUNT_MANAGE`, giữ mã/tên đăng nhập/role/trạng thái bất biến, kiểm tra `rowversion`, ghi audit cùng transaction. `StaffManagementIT` 9/9 PASS trên `SuncareClinicMigrationTest` (gồm input sai, xung đột, sai quyền, rollback); backend `mvnw.cmd test -q` PASS, frontend lint/build PASS, `git diff --check` không có lỗi whitespace. Chưa thử browser toàn luồng; P07.c vẫn IN_PROGRESS. Việc tiếp theo duy nhất: chạy UI với backend thử và xác minh xem/sửa Staff, xung đột 409, responsive 320/375/768/1280 px.

## Hiện trạng đã đối chiếu

- Tại P00, backend chỉ có Spring Boot và `GET /api/health`; frontend có trang giới thiệu và kiểm tra Health API. API đăng ký đã được thêm ở P03; đăng nhập và UI nghiệp vụ vẫn chưa có.
- Ban đầu có 11 migration V1–V11. Người dùng cho biết các bảng chưa có dữ liệu thật; trước P02 đã kiểm tra trực tiếp `Role`, `Permission`, `Account` ở cả hai database đều 0 dòng. P02 đã thêm V12 và seed danh mục quyền, chưa tạo dữ liệu bệnh nhân.
- `docs/PROJECT_STATE_2.md` ghi nhận mốc database theo phiên trước. Các tài liệu nghiệp vụ 01–06 hiện đã nằm trong `docs/`; `docs/PROJECT_STATE.md` cũ hơn.
- `AGENTS.md` và `docs/01-06*.md` đang là file chưa được Git theo dõi trước phiên này; giữ nguyên, không xóa hoặc tự commit.

## Kết quả phiên này

- Phạm vi P00: bổ sung quyết định `granted_by` vào `AGENTS.md` và `docs/03-tu-dien-du-lieu.md`; tạo file tiến độ này. P00 chưa sửa code hoặc database.
- Quyết định nghiệp vụ của chủ dự án: bệnh nhân tự đăng ký có thể để `AccountRole.granted_by = NULL`; bootstrap admin cũng chưa có người cấp nên để NULL. Khi admin gán role cho staff, lưu ID admin. V2 hiện khai báo `NOT NULL`, vì vậy P02 phải tạo V12 thay schema, không sửa V2.
- Frontend tại `frontend/`: `npm run lint` PASS; `npm run build` PASS.
- Backend tại `backend/`: `.\mvnw.cmd test` PASS (1 test, 0 lỗi) sau khi đặt `JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1` và thêm thư mục `D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0` vào `Path`.
- `.\mvnw.cmd '-Dtest=SqlServerConnectivityIT' test` PASS (1 test, 0 lỗi): kết nối `SuncareClinic`, đếm 58 bảng nghiệp vụ. Cổng SQL Server 1433 và DLL auth trên máy đều có.
- Lần chạy Maven đầu tiên thất bại do shell chưa có `JAVA_HOME`; sau khi đặt biến trong shell thì PASS. Không phải lỗi code. Maven in cảnh báo metadata repository 401 nhưng build vẫn PASS.
- Trong P00 chưa kiểm thử API nghiệp vụ, số dòng trong bảng, migration trên database thử hoặc AT01–AT49.

### P01 — xử lý lỗi và validation dùng chung

- Thêm `common/response/ApiError.java` với `code`, `message`, `fieldErrors`; thêm `common/exception/ApiException.java` và `ApiExceptionHandler.java`.
- Request có `@Valid @RequestBody` sai trả HTTP 400 `VALIDATION_ERROR` và lỗi theo trường. JSON lỗi cú pháp trả HTTP 400 `INVALID_JSON`, không trả chi tiết parser cho client. Service có thể ném `ApiException` với HTTP status/mã lỗi rõ ràng.
- Thêm `ApiExceptionHandlerTests.java` kiểm tra validation, JSON lỗi, lỗi nghiệp vụ và Health API. Chỉ có endpoint giả trong test; chưa thêm API nghiệp vụ vào sản phẩm.
- Kiểm tra tại `backend/`: `.\mvnw.cmd test` PASS, tổng 5 test (4 mới + 1 context), 0 lỗi. Dùng JDK/DLL như phần P00. Chưa thử request qua server đang chạy thật; MockMvc đã kiểm tra HTTP response.

### P02 — role, permission và bootstrap admin

- Thêm `V12__allow_system_role_grants_and_seed_access.sql`: `AccountRole.granted_by` nullable, vẫn giữ FK; seed đúng 6 Role, 14 Permission và 14 RolePermission theo tài liệu 04. Không sửa V1–V11.
- Thêm `AdminBootstrapRepository`, `AdminBootstrapService`, `AdminBootstrapRunner` trong `modules/account/`. Chỉ chạy bootstrap khi có `--suncare.bootstrap.admin.enabled=true`; đọc login/tên/mật khẩu từ biến môi trường, hash BCrypt, tạo Staff + Account + AccountRole trong một transaction. Chạy lại không tạo thêm hoặc đổi mật khẩu hiện có.
- Test đơn vị bootstrap kiểm tra hash, chạy lại và trùng login; `mvnw.cmd test` PASS 8 test, 0 lỗi. Test kết nối chính `-Dtest=SqlServerConnectivityIT` PASS, vẫn 58 bảng.
- `SuncareClinicMigrationTest`: Flyway V12 PASS; kiểm tra 6 Role, 14 Permission, 14 RolePermission, cột `granted_by` nullable và FK vẫn tồn tại. Thử grant với ID người cấp không tồn tại bị SQL Server từ chối (547), rollback không còn dòng sai. Bootstrap chạy hai lần chỉ còn 1 Staff, 1 Account, 1 grant `SYSTEM_ADMIN` có `granted_by = NULL`.
- `SuncareClinic`: Flyway V12 PASS; kiểm tra cùng số role/permission và cột nullable. `Account` và `Staff` ở DB chính vẫn 0 dòng, vì chưa có mật khẩu admin do chủ dự án chọn. Code bootstrap đã sẵn sàng; không lưu mật khẩu trong repo.
- Lần Flyway đầu ở DB thử bị treo trước migration; thread dump dừng tại JDBC đọc truy vấn `sys.databases`, SQL Server cũng chậm đăng nhập. Event SQL Server báo bộ nhớ process bị paging. Sau khi dịch vụ phản hồi, chạy lại V12 thành công. Không có migration thất bại trong history.

Để tự tạo admin ở DB chính khi cần, mở PowerShell tại `backend/`, cấu hình JDK 21 và thư mục JDBC auth DLL trong `Path`, rồi chạy:

```powershell
$env:SUNCARE_ADMIN_LOGIN = 'admin.local'
$env:SUNCARE_ADMIN_NAME = 'Quản trị viên'
$adminPassword = Read-Host 'Mật khẩu admin (ít nhất 12 ký tự)' -AsSecureString
$env:SUNCARE_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $adminPassword).Password
try {
    & "$env:JAVA_HOME\bin\java.exe" -jar target\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=migrationmain --spring.main.web-application-type=none --suncare.bootstrap.admin.enabled=true
} finally {
    Remove-Item Env:SUNCARE_ADMIN_PASSWORD
}
```

Bootstrap cần V12 (đã có). Không bật cờ này khi chạy ứng dụng hằng ngày; chạy lại với login cũ không reset mật khẩu.

### P03 — bệnh nhân tự đăng ký

- Thêm `POST /api/accounts/register` trong `modules/account/controller`, DTO request/response, service và repository JDBC. Request nhận `loginName`, `password`, `fullName` và thông tin hồ sơ tùy chọn; server tự tạo `Patient`, `Account` loại `PATIENT`, `AccountRole` PATIENT với `granted_by = NULL` trong một transaction. Mã bệnh nhân `PAT-` + UUID không tái sử dụng.
- Login được trim/lowercase; mật khẩu hash BCrypt; response HTTP 201 chỉ có `accountId`, `patientCode`. Trùng login trả HTTP 409 `LOGIN_EXISTS`. Bật Jackson từ chối field lạ để `role`/`staffId` không được nhận từ đăng ký công khai.
- `PatientRegistrationIT` chạy trên `SuncareClinicMigrationTest`: 4 test PASS cho đăng ký thành công, trùng login, gửi role/staffId và rollback khi gán role lỗi. Kiểm tra SQL thực tế: password hash khớp, role PATIENT, `staff_id`/`granted_by` NULL. Test xóa dữ liệu Patient/Account do chính nó tạo.
- AT02 kiểm tra bằng SQL transaction trên DB thử: Account đồng thời có patient_id/staff_id và Account không có hồ sơ đều bị CHECK từ chối (SQL error 547); rollback còn 0 Patient/Staff thử. Sau test, DB thử có 0 Patient, 1 Account admin thử từ P02 và 1 AccountRole tương ứng.
- `.\mvnw.cmd test` PASS 8 test mặc định, 0 lỗi; `.\mvnw.cmd '-Dtest=PatientRegistrationIT' test` PASS 4 integration test, 0 lỗi. Chưa có đăng nhập hoặc UI đăng ký; thuộc P04–P05. DB chính chưa có Patient/Account mới từ P03.

## Danh sách công việc

Chi tiết và điều kiện nghiệm thu của từng mã nằm trong `AGENTS.md`. Chỉ đổi `DONE` khi có kết quả kiểm tra tương ứng.

| Mã | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| P00 | DONE | Đã kiểm tra baseline, xem phần Kết quả phiên này. |
| P01 | DONE | 4 MockMvc test PASS; Maven tổng 5 test PASS. |
| P02 | DONE | V12 PASS trên DB thử và chính; seed 6/14/14; bootstrap DB thử chạy lặp không nhân đôi; DB chính chưa tạo admin. |
| P03 | DONE | API + transaction; 4 integration test DB thử PASS; AT01–AT02 và rollback kiểm tra. |
| P04 | DONE | JWT HS256, AuthToken V13, đăng nhập/đăng xuất/me; 4 integration test trên DB thử PASS, migration V13 DB thử và chính. |
| P05 | DONE | Chủ dự án xác nhận hoàn thành ngày 2026-11-02; bằng chứng kiểm tra browser/API toàn luồng chưa được bổ sung vào sổ. |
| P06 | DONE | Chủ dự án xác nhận đã tự kiểm tra UI và thấy ổn ngày 2026-11-02; log trước đó ghi API DB thử, lint/build và giới hạn browser của agent. |
| P07 | IN_PROGRESS | API tạo/list/gán/thu hồi role/khóa và UI đã code; 7 integration test DB thử, REST smoke, lint/build PASS; còn kiểm tra UI trên browser/responsive. |
| P08 | DONE | API/UI/V14, DB thử và lint/build đạt; chủ dự án xác nhận xong, browser chưa có log kiểm tra của agent. |
| P09 | DONE | API/UI và integration test DB thử 2/2 đạt; chủ dự án xác nhận hoàn thành. |
| P10 | DONE | API/UI, DB thử 1/1, browser Edge headless qua luồng thật, responsive 320/375/768/1280, lint/build PASS; fixture đã dọn. Hộp thoại kết thúc, lịch sử phân công thành bảng có tìm/lọc; `StaffAssignmentIT` 1/1, lint/build PASS; lượt UI mới chưa chạy browser. |
| P11 | IN_PROGRESS | API/UI đã có; sửa quy tắc giá, test đích danh và lint/build PASS. Còn kiểm tra UI tương tác/responsive trước khi DONE. |
| P12 | IN_PROGRESS | Backend API + DTO + Repository + Service + Controller; 2/2 SchedulingCatalogIT PASS trên migrationtest, ClinicalServiceIT 5/5 PASS; UI SchedulingCatalogManagement.jsx, lint/build PASS; còn kiểm tra browser trực tiếp. |
| P13 | IN_PROGRESS | API và quyền demo_head trả 200; `WorkScheduleIT` 2/2 PASS trên DB thử. Danh sách đợt phân trang 5 mục/trang; calendar nhóm ONSITE/ONLINE với cột nhãn cố định 12rem; click/Ctrl-click/drag giữ lựa chọn qua tuần; form có Hủy. Lint/build PASS; desktop 1536×864 đã xem. Còn 320/375/768/1280 px và tương tác browser. Không ghi DB chính. |
| P14 | IN_PROGRESS | V15/V16, publish transaction, phân công nhiều ô, lọc bác sĩ/phòng theo xung đột và sinh slot ONLINE 20 phút. `WorkScheduleIT` 2/2, `WorkSchedulePublishIT` 5/5, `RoomManagementIT` 2/2 PASS trên DB thử V16 sau cập nhật `workSlots`; lint/build/diff-check PASS, fixture đã dọn. Còn browser kiểm tra luồng chọn ca/publish/phòng ONLINE. Không ghi DB chính. |
| P15 | IN_PROGRESS | API/UI tìm lịch theo bác sĩ/ca, khoảng ngày với preset, thẻ bác sĩ mở lịch theo ngày và ca sáng/chiều. `OnsiteAppointmentSearchIT` 4/4 PASS trên DB thử V16, gồm biên ngày, qua Chủ Nhật, khoảng ngược/ngày quá khứ. Còn browser/date picker/responsive. |
| P16 | IN_PROGRESS | API P16.a và UI P16.b đã có; `OnsiteAppointmentBookingIT` 6/6 PASS trên DB thử V16, lint/build/diff-check PASS. Còn browser toàn luồng tìm/đặt và responsive; chưa gửi booking lên DB chính. |
| P17 | TODO | Theo lộ trình trong AGENTS.md. |
| P18 | TODO | Theo lộ trình trong AGENTS.md. |
| P19 | TODO | Theo lộ trình trong AGENTS.md. |
| P20 | TODO | Theo lộ trình trong AGENTS.md. |
| P21 | TODO | Theo lộ trình trong AGENTS.md. |
| P22 | TODO | Theo lộ trình trong AGENTS.md. |
| P23 | TODO | Theo lộ trình trong AGENTS.md. |
| P24 | TODO | Theo lộ trình trong AGENTS.md. |
| P25 | TODO | Theo lộ trình trong AGENTS.md. |
| P26 | TODO | Theo lộ trình trong AGENTS.md. |
| P27 | TODO | Theo lộ trình trong AGENTS.md. |
| P28 | TODO | Theo lộ trình trong AGENTS.md. |
| P29 | TODO | Theo lộ trình trong AGENTS.md. |
| P30 | TODO | Theo lộ trình trong AGENTS.md. |
| P31 | TODO | Theo lộ trình trong AGENTS.md. |
| P32 | TODO | Theo lộ trình trong AGENTS.md. |
| P33 | TODO | Theo lộ trình trong AGENTS.md. |
| P34 | TODO | Theo lộ trình trong AGENTS.md. |
| P35 | TODO | Theo lộ trình trong AGENTS.md. |
| P36 | TODO | Theo lộ trình trong AGENTS.md. |
| P37 | TODO | Theo lộ trình trong AGENTS.md. |
| P38 | TODO | Theo lộ trình trong AGENTS.md. |
| P39 | TODO | Theo lộ trình trong AGENTS.md. |
| P40 | TODO | Theo lộ trình trong AGENTS.md. |
| P41 | TODO | Theo lộ trình trong AGENTS.md. |
| P42 | TODO | Theo lộ trình trong AGENTS.md. |
| P43 | TODO | Theo lộ trình trong AGENTS.md. |
| P44 | TODO | Theo lộ trình trong AGENTS.md. |
| P45 | TODO | Theo lộ trình trong AGENTS.md. |
| P46 | TODO | Theo lộ trình trong AGENTS.md. |
| P47 | TODO | Theo lộ trình trong AGENTS.md. |

## Bàn giao phiên sau

- Việc tiếp theo: P15.c — kiểm tra khoảng ngày tra cứu và UI P15/P16.
- Đọc trước: `frontend/src/features/appointment/OnsiteDateRangePicker.jsx`, `frontend/src/features/appointment/OnsiteAppointmentSearch.jsx`, `frontend/src/services/onsiteAppointmentApi.js`, module appointment backend và mục P15/P16 trong tài liệu này.
- Điều kiện cần: browser host hoạt động và UI dùng backend profile `migrationtest`; kiểm tra preset, ngày đầu/cuối, lỗi khoảng ngược, tìm lịch/chọn ca/đặt thử và responsive 320/375/768/1280 px. Không dùng backend mặc định trỏ DB chính để gửi booking.

### Kiểm tra lỗi import IDE ngày 2026-11-02

- Phạm vi: kiểm tra P03 hiện có, không thay đổi code nghiệp vụ. `backend/pom.xml` đã khai báo `spring-boot-starter-jdbc` (Spring DAO/transaction) và `spring-security-crypto` (BCrypt).
- Chạy `backend/.\mvnw.cmd test -q` với JDK 21: exit code 0; Java biên dịch được các import trong `PatientRegistrationService`, test mặc định chạy thành công. Đây là lỗi đồng bộ classpath trong IntelliJ, không phải class Java thiếu hoặc P03 chưa viết xong.
- IntelliJ tại `.idea/modules.xml` chỉ liệt kê module gốc `suncare-clinic`, còn `.idea/misc.xml` có tham chiếu `backend/pom.xml`; cần Maven Reload/Reimport `backend/pom.xml` trong IDE. Các file `.idea` là cấu hình riêng được Git bỏ qua. Shell mới không có `JAVA_HOME`, nên khi chạy Maven CLI cần đặt JDK 21 như hướng dẫn P00; đây là vấn đề môi trường riêng.
- Chưa xác nhận dấu lỗi trong cửa sổ IntelliJ đã biến mất sau khi người dùng reload Maven. Không thay đổi trạng thái P03 hoặc P04 theo kết quả IDE.

### Sửa cấu hình chạy Spring trong IntelliJ ngày 2026-11-02

- Lỗi `CannotGetJdbcConnectionException` khi Actuator kiểm tra DB có nguyên nhân gốc `UnsatisfiedLinkError: Unable to load authentication DLL mssql-jdbc_auth-13.4.0.x64`. JDBC URL dùng `integratedSecurity=true` và `NativeAuthentication` nên JVM phải thấy DLL xác thực Windows.
- Cấu hình Run `BackendApplication` tại `.idea/workspace.xml` đã đặt `-Djava.library.path` tới **file** DLL. Đã sửa thành thư mục `D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0`; DLL `mssql-jdbc_auth-13.4.0.x64.dll` tồn tại trong thư mục đó. `.idea` được Git bỏ qua, thay đổi chỉ áp dụng trên máy này.
- Kiểm tra kết nối SQL Server bằng `backend/.\mvnw.cmd '-Dtest=SqlServerConnectivityIT' test -q` với JDK 21 và thư mục DLL trên `Path`: exit 0, database `SuncareClinic`, 58 bảng. Chưa chạy lại trực tiếp cấu hình Spring trong cửa sổ IntelliJ sau khi sửa; nếu IDE đang mở, cần mở lại cấu hình Run hoặc khởi động lại IDE để nạp giá trị đã đổi.
- Không đổi code nghiệp vụ hay trạng thái P03/P04. Việc tiếp theo vẫn là P04.

### P04 — JWT đăng nhập, đăng xuất và tài khoản hiện tại

- Theo lựa chọn của chủ dự án trong phiên này, dùng JWT HS256, không dùng session. Thêm Spring Security, Nimbus JOSE và `AccountAuthController` với `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`. Login trả Bearer token 8 giờ; `/me` trả ID, login, loại tài khoản, liên kết hồ sơ, role và permission hiện hành; không trả hash.
- V13 tạo `AuthToken` để xác định token còn hiệu lực và thu hồi ngay khi logout. Mỗi request xác minh chữ ký/hạn JWT, đối chiếu token DB và Account ACTIVE, rồi đọc role/permission hiện hành; khóa tài khoản hoặc thu hồi role có hiệu lực request tiếp theo. Spring Security filter nạp authorities; endpoint chưa triển khai bị từ chối mặc định. Quyền trên từng hồ sơ vẫn phải kiểm tra khi làm use case sau.
- Secret lấy từ `SUNCARE_JWT_SECRET` (ít nhất 32 byte UTF-8). Nếu chưa đặt, ứng dụng sinh secret ngẫu nhiên khi khởi động; token cũ hết hiệu lực sau restart. Khi cần phiên đăng nhập bền qua restart, đặt biến môi trường bí mật ngoài repo. Không ghi secret vào file cấu hình.
- `AccountAuthIT` trên `SuncareClinicMigrationTest`: 4 test PASS gồm login/me/logout, mật khẩu sai và tài khoản LOCKED, thu hồi role và token giả, rollback khi bước cập nhật login lỗi. V13 đã chạy ở DB thử; sau `mvnw.cmd package -q` PASS đã chạy V13 ở DB chính. Lần chạy migrationmain đầu áp dụng V13 thành công nhưng chế độ non-web lỗi khởi tạo SecurityConfig; đã sửa `@ConditionalOnWebApplication`, chạy lại exit 0, schema main ở v13 và không có migration mới.
- Chưa thử UI P05. Mã nguồn/API và DB test đã xác nhận; không dùng dữ liệu bệnh nhân thật. Việc tiếp theo duy nhất: P05 — UI đăng ký/đăng nhập, logout và điều hướng theo role/quyền, dùng API thật, thử trạng thái tải/rỗng/lỗi/thành công.

- 2026-11-02: Lần chạy Spring từ IDE báo cổng 8080 đang dùng vì backend migrationtest do phiên kiểm tra P05 trước đó còn chạy (PID 69252). Đã dừng đúng phiên backend thử và xác nhận cổng 8080 trống; chưa chạy lại ứng dụng trong IDE. Dòng generated security password là cảnh báo auto-config không gây lỗi cổng. P05 vẫn chưa xác nhận giao diện responsive trong trình duyệt.


### P05 — sửa phản hồi lỗi đăng ký HTTP 400 (2026-11-02)

- Backend đang chạy từ IntelliJ ở profile local trả HTTP 400 VALIDATION_ERROR và ieldErrors khi login không đúng regex hoặc mật khẩu ngắn. Ví dụ thử request chỉ đọc kết quả: login chứa khoảng trắng và password 3 ký tự bị từ chối, nêu rõ hai trường. Không biết payload cụ thể của người dùng từ dòng Console đơn lẻ.
- Form đã thêm kiểm tra tương ứng DTO trước khi gửi, hướng dẫn tên đăng nhập 3–100 ký tự không dấu, mật khẩu 12–72 ký tự, kiểm tra tên/ngày sinh/email và độ dài tùy chọn. Lỗi server được gắn vào trường bằng tiếng Việt. 
pm run lint và 
pm run build PASS.
- P05 vẫn IN_PROGRESS vì chưa kiểm tra trực quan trong browser. Nếu người dùng vẫn nhận 400 sau khi tải lại frontend, cần xem response JSON của request đó để xác định trường và rule chính xác.


### P05 — bổ sung hồ sơ Patient và chỉnh giao diện (2026-11-02)

- Theo yêu cầu mới của chủ dự án, đăng ký Patient nhận/lưu thêm `identityNumber`, `emergencyName`, `emergencyPhone` tương ứng ba cột có sẵn trong V1; không sửa migration. Mật khẩu đăng ký đổi từ 12 xuống tối thiểu 8 ký tự, tối đa 72; admin bootstrap vẫn yêu cầu 12 ký tự vì là tài khoản đặc quyền.
- Form mới nhóm thông tin bệnh nhân, liên hệ khẩn cấp và đăng nhập. Sau khi tạo thành công chuyển sang tab đăng nhập, giữ sẵn login/password trong state của tab; không tự đăng nhập. Lỗi theo trường và lỗi toàn form hiển thị rõ bằng tiếng Việt. Avatar chưa nằm trong P05, thuộc P39.
- Thêm `frontend/AGENTS.md` và `frontend/AGENTS_SKILL.md` làm hướng dẫn cục bộ cho màn hình frontend và checklist kiểm tra.
- `backend/.\mvnw.cmd test -q` PASS. `backend/.\mvnw.cmd '-Dtest=PatientRegistrationIT' test -q` PASS 6/6 trên `SuncareClinicMigrationTest`, gồm lưu liên hệ khẩn cấp và mật khẩu 8/7 ký tự. `frontend/npm run lint` và `npm run build` PASS. Health API trực tiếp cổng 8080 và qua Vite proxy cổng 5173 đều trả UP tại lần kiểm tra; lỗi 502 trước đó không còn tái hiện lúc này.
- Chưa xác nhận trực quan layout responsive và toàn bộ luồng form trong browser. P05 vẫn IN_PROGRESS cho đến khi kiểm tra phần đó; không dùng dữ liệu bệnh nhân thật cho thử nghiệm.
- API client hiện thông báo riêng khi Vite proxy trả 502/503: kiểm tra Spring Boot trên cổng 8080; `npm run lint` và `npm run build` PASS sau thay đổi.


### P05 — nền thiết kế Sunflower Clinical (2026-11-02)

- Phạm vi yêu cầu riêng trong P05: rà soát toàn bộ `suncare-*` ở UI hiện có; cập nhật `frontend/src/index.css`, `App.jsx`, `features/account/AccountForm.jsx`. Không đổi API, backend, database hay chức năng form.
- Token nền ivory/trắng, vàng hướng dương, xanh lá và màu chữ đậm được đặt ở Tailwind v4. Đổi các vùng hero xanh gradient sang bề mặt sáng; CTA vàng dùng chữ đậm. Giữ cặp vàng đậm/chữ trắng chỉ ở nút kết nối lỗi, đáp ứng tương phản theo bảng trong skill. Thêm focus rõ và giảm chuyển động theo hệ thống.
- Kiểm tra tại `frontend/`: `npm run lint` PASS (exit 0); `npm run build` PASS (exit 0, Vite 8.3.5). Đã rà soát mã nguồn cho các lớp `suncare-*` và chữ trắng trên vàng.
- Chưa xác minh bằng ảnh tại 320/375/768/1280 px: cầu nối trình duyệt báo `The browser access host is unavailable`. Cần mở Vite và kiểm tra trực quan đủ bốn kích thước, gồm form đăng ký dài, login, guest và tài khoản theo vai trò. Chưa chạy tương tác API trong phiên này; P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — kiểm tra trực quan responsive và luồng đăng ký/đăng nhập bằng API thật trên database thử, rồi chốt P05 khi đạt.

### P05 — trang giới thiệu trước đăng nhập (2026-11-02)

- Theo yêu cầu mới, khách vào trang giới thiệu mặc định; nút Đăng ký/Đăng nhập mới mở form. Form bỏ toàn bộ ba khẩu hiệu kỹ thuật và panel trang trí. Đăng xuất dùng hộp xác nhận; hủy thì giữ phiên hiện tại. Bỏ mục kiểm tra API khỏi giao diện công khai.
- Chưa có API GET công khai cho khoa/chuyên khoa/bác sĩ, cũng chưa có danh sách tên đã xác nhận trong docs. Trang giới thiệu có khu vực cho hai danh sách với trạng thái chưa công bố; không tạo bác sĩ/khoa giả hay gọi endpoint không tồn tại. Đang chờ chủ dự án cung cấp danh sách được duyệt hoặc cho phép giữ trạng thái này.
- `frontend/npm run lint` PASS (exit 0); `frontend/npm run build` PASS (exit 0). Chưa kiểm tra trực quan responsive và luồng API trong trình duyệt ở phiên này; P05 tiếp tục IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — bổ sung danh sách khoa/bác sĩ đã duyệt khi có dữ liệu, rồi xác minh giao diện và luồng trên browser ở 320/375/768/1280 px.
- Chủ dự án xác nhận chưa có danh sách khoa/bác sĩ được duyệt; giữ trạng thái “đang cập nhật” trên trang giới thiệu cho đến khi có dữ liệu chính thức.

### P05 — làm trang giới thiệu sinh động hơn (2026-11-02)

- Thêm ảnh minh họa khu vực tiếp đón vào `frontend/src/assets/clinic-reception-illustration.png`; ảnh tạo riêng cho giao diện, có nhãn “Hình minh họa” để không ngụ ý đây là ảnh cơ sở thật.
- Chỉnh hero thành bố cục chữ + ảnh lớn, tăng diện tích nền ivory; hai khu vực khoa và bác sĩ dùng nền xanh/vàng nhạt với nội dung “đang cập nhật” đúng quyết định của chủ dự án. Token màu mới được khai báo trong `index.css`; không thêm tên khoa/bác sĩ giả, không đổi API hay luồng đăng nhập.
- `frontend/npm run lint` PASS, `frontend/npm run build` PASS. Ảnh PNG 1.95 MB; có thể tối ưu định dạng khi hoàn thiện trang. Chưa xác nhận trực quan ở 320/375/768/1280 px vì công cụ chụp trình duyệt hiện trả sai cửa sổ dù Edge có tab Suncare; không báo kiểm tra responsive PASS.
- Việc tiếp theo duy nhất: P05 — kiểm tra trực quan bốn kích thước và luồng đăng ký/đăng nhập qua API thật trên database thử.

### P05 — hộp xác nhận đăng xuất (2026-11-02)

- Thay `window.confirm` bằng `<dialog>` theo màu Sunflower Clinical trong `frontend/src/App.jsx`: tiêu đề/mô tả rõ, nút Ở lại và Đăng xuất. Hỗ trợ Escape, bấm nền để đóng khi chưa gửi; khóa đóng và hai nút lúc đang gửi. Nếu API lỗi, giữ hộp thoại mở và hiển thị lỗi tại chỗ; thành công mới xóa phiên và trở về trang giới thiệu.
- `frontend/npm run lint` PASS; `frontend/npm run build` PASS. Chưa thử thao tác bằng trình duyệt trong phiên này; P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — kiểm tra hộp thoại, luồng tài khoản và responsive tại 320/375/768/1280 px trên trình duyệt với backend thử.

### P05 — chỉnh bố cục và hiệu ứng hộp đăng xuất (2026-11-02)

- Hộp thoại trong `App.jsx` đổi thành phần tiêu đề có biểu tượng bên trái, mô tả ngắn, vùng thao tác riêng với hai nút rộng bằng nhau; mặc định focus vào “Ở lại”. Vẫn giữ hành vi Hủy/Escape/bấm nền, khóa đóng khi request đang chạy và hiển thị lỗi API trong hộp.
- `index.css` thêm hiệu ứng mở 180 ms cho hộp thoại và nền tối; tắt hiệu ứng theo `prefers-reduced-motion`.
- `frontend/npm run lint` PASS; `frontend/npm run build` PASS. Chưa kiểm chứng trực quan bằng trình duyệt trong phiên này; P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — thử hộp đăng xuất và các luồng tài khoản ở 320/375/768/1280 px trên trình duyệt.

### P05 — chỉnh trang giới thiệu toàn chiều ngang (2026-11-02)

- Theo góp ý của chủ dự án, chỉ trang khách `home` bỏ khung `max-w-6xl` căn giữa; hero và các dải nội dung phủ chiều ngang, phần chữ vẫn canh trái theo lề responsive. Header đổi nền ivory nhẹ. Bỏ nhãn “Hình minh họa” trên ảnh; alt vẫn mô tả đúng là ảnh minh họa, không khẳng định ảnh cơ sở thật.
- Bổ sung thông tin có căn cứ từ `docs/01-nghiep-vu.md`: phạm vi một cơ sở ngoại trú, hai hình thức khám và nguyên tắc chọn lịch. Ghi rõ các bước đặt khám đang được phát triển. Khoa/bác sĩ cụ thể vẫn “đang cập nhật” theo quyết định trước, không thêm dữ liệu giả.
- `frontend/npm run lint` PASS; `frontend/npm run build` PASS. Chưa xác nhận bố cục bằng trình duyệt ở 320/375/768/1280 px trong phiên này; P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — kiểm tra trực quan responsive và luồng đăng ký/đăng nhập/đăng xuất trên trình duyệt với backend thử.

### P05 — màn hình tài khoản hai nửa và hiệu ứng mở/đóng (2026-11-02)

- Màn hình login/register trong `AccountForm.jsx` chuyển thành bố cục toàn chiều ngang: form trái/ảnh và lời giới thiệu phải khi đăng nhập; hai nửa trượt đổi chỗ khi đăng ký. Dưới 1024 px xếp dọc, form ở trước để nhập liệu thuận tiện. Giữ nguyên field, validation, API, dữ liệu nhập và thông báo.
- `index.css` thêm chuyển động ngắn cho hai nửa, nội dung form và lúc rời màn hình; dialog đăng xuất có cả hiệu ứng đóng trước khi gọi `close()`. Cả hai tôn trọng `prefers-reduced-motion`.
- Bổ sung quy tắc mở/đóng form, dialog, menu, popup vào `frontend/AGENTS_SKILL.md` và `.agents/skills/suncare-clinical-frontend/SKILL.md`; không ghi đè quy tắc cũ.
- `frontend/npm run lint` PASS; `frontend/npm run build` PASS. Chưa kiểm tra trực quan tại 320/375/768/1280 px vì cầu nối trình duyệt báo không khả dụng; chưa chạy lại luồng API trên browser. P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — kiểm tra trực quan chuyển cảnh, form dài và các luồng tài khoản trên trình duyệt với backend thử.


### P05 — cân lại màn hình tài khoản, form luôn bên trái (2026-11-02)

- Theo góp ý mới, bỏ chuyển chỗ hai nửa. Từ 1024 px, form luôn bên trái: đăng nhập chiếm 43%, đăng ký mở rộng lên 58% bằng transition; nội dung cũ fade out ngắn rồi nội dung mới hiện. Dưới 1024 px xếp dọc, form trước ảnh. Không đổi field, validation hoặc API.
- Bên phải chuyển thành bố cục ba ảnh minh họa (tiếp đón, trao đổi y tế, hoa hướng dương) và một đoạn giới thiệu ngắn; ảnh bác sĩ là ảnh minh họa, không phải danh tính thật. Chuyển ba ảnh từ PNG sang JPEG để tổng dung lượng từ khoảng 5.97 MB còn khoảng 0.53 MB. Giữ alt mô tả tính minh họa.
- `frontend/npm run lint` PASS; `frontend/npm run build` PASS. Chưa xác minh trực quan tại 320/375/768/1280 px do cầu nối trình duyệt không khả dụng, chưa chạy lại các luồng API trên browser. P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — kiểm tra trực quan bốn kích thước, chuyển chế độ và luồng tài khoản qua backend thử.

### P05 — rút khu ảnh tài khoản còn hai ảnh (2026-11-02)

- Theo góp ý của chủ dự án, `AccountForm.jsx` không dùng ảnh tiếp đón cũ. Khu minh họa login/register chỉ còn ảnh trao đổi y tế làm ảnh chính và ảnh hoa hướng dương làm điểm nhấn; `index.css` thay lưới ba ô bằng bố cục hai ảnh chồng nhẹ, giữ form bên trái và chuyển động thay đổi bề rộng.
- Ảnh tiếp đón vẫn phục vụ riêng trang giới thiệu; không xóa asset đang được `App.jsx` dùng. Không đổi API, trường form hoặc nghiệp vụ.
- `frontend/npm run lint` PASS; `frontend/npm run build` PASS. Chưa xác nhận trực quan trên browser tại 320/375/768/1280 px; P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — kiểm tra trực quan bố cục hai ảnh, chuyển chế độ và luồng tài khoản trên browser với backend thử.

### P05 — tránh cuộn dọc khi form đăng nhập vừa màn hình (2026-11-02)

- `App.jsx` chuyển khung trang sang flex theo chiều dọc với `min-h-svh`; vùng tài khoản nhận phần chiều cao còn lại sau header, không dùng `calc(100vh - 89px)` cố định. `index.css` cho login căn form trong vùng còn lại và giới hạn chiều cao khu ảnh theo viewport. Từ 768 px giữ form/ảnh cạnh nhau; dưới 768 px ẩn khu ảnh ở login để form ngắn có thể vừa màn hình. Đăng ký vẫn hiển thị đủ trường và cuộn khi nội dung vượt chiều cao.
- `frontend/npm run lint` PASS; `frontend/npm run build` PASS. Chưa đo scrollHeight trực tiếp ở 320/375/768/1280 px do cầu nối trình duyệt chưa khả dụng; không tuyên bố mọi viewport đều không cuộn. P05 tiếp tục IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — đo trực quan scroll và thử các luồng tài khoản ở bốn kích thước trên trình duyệt với backend thử.

### P05 — bỏ khu ảnh bên phải của đăng ký (2026-11-02)

- Theo yêu cầu mới, chế độ đăng ký cho form mở rộng 100% chiều ngang; khu ảnh chuyển về độ rộng 0 và mờ đi trên desktop, ẩn hẳn trên mobile. Không còn vùng ảnh trống kéo dài khi cuộn form. Chế độ đăng nhập vẫn giữ hai ảnh bên phải.
- Từ 1024 px, form đăng ký bố trí thông tin bệnh nhân ở cột trái, liên hệ khẩn cấp và thông tin đăng nhập ở cột phải; dưới 1024 px xếp dọc. Giữ nguyên field, validation, API và thông báo.
- `frontend/npm run lint` PASS; `frontend/npm run build` PASS. Chưa kiểm tra trực quan ở 320/375/768/1280 px trên browser; P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — kiểm tra chuyển chế độ, cuộn trang đăng ký và các luồng tài khoản trên browser với backend thử.

### P05 — bỏ phần dư cuối trang đăng ký (2026-11-02)

- Khu ảnh bên phải trước đây chỉ thu về độ rộng 0; nay `AccountForm.jsx` đợi hiệu ứng đóng 420 ms rồi tháo khu ảnh khỏi DOM ở chế độ đăng ký. Khi trở về đăng nhập, khu ảnh được gắn lại. `index.css` bỏ giãn chiều cao bắt buộc cho trang đăng ký và rút khoảng đệm dưới form; không đổi trường hoặc API.
- Lần lint đầu phát hiện `setState` đồng bộ trong effect; đã chuyển cập nhật sang callback của timer. Chạy lại `frontend/npm run lint` PASS; `frontend/npm run build` PASS.
- Chưa đo trực quan khoảng trống/scrollHeight trên trình duyệt ở 320/375/768/1280 px; P05 vẫn IN_PROGRESS.
- Việc tiếp theo duy nhất: P05 — xác minh chiều cao trang đăng ký và luồng tài khoản trên browser với backend thử.

### Chuẩn bị tài khoản demo cho các role Staff (2026-11-02)

- Người dùng đã tạo Patient `nta_30` trên `SuncareClinic`; kiểm tra chỉ đọc xác nhận DB chính có 1 Account và 6 Role, chưa có `SYSTEM_ADMIN`.
- Chuẩn bị script một lần trong `backend/target/create-demo-staff.ps1` (folder Git bỏ qua): tạo `demo_doctor`, `demo_lab`, `demo_head`, `demo_cashier` với mật khẩu ngẫu nhiên riêng, BCrypt theo backend, Staff và AccountRole trong một transaction; grant ghi ID admin. Không sửa migration hoặc code backend.
- Dry run trên `SuncareClinicMigrationTest` PASS: bốn tài khoản tạo được rồi rollback; kiểm tra sau đó có 0 Account/Staff `DEMO_` còn lại. Chưa chạy ghi trên DB chính vì chưa có admin. Quy tắc dự án yêu cầu chủ dự án nhập mật khẩu admin qua biến môi trường khi bootstrap; chủ dự án đã trả lời sẽ thực hiện và báo login.
- Chưa tạo tài khoản Staff nào trên DB chính; không đánh dấu P07 DONE. Việc tiếp theo cho yêu cầu này: sau khi admin bootstrap thành công, chạy script trên DB chính, kiểm tra bốn role và bàn giao mật khẩu qua file local bị Git bỏ qua.

### Tạo tài khoản demo cho các role Staff (2026-11-02)

- Chủ dự án đã bootstrap `admin.local` trên `SuncareClinic`; kiểm tra tài khoản ACTIVE, loại STAFF, role SYSTEM_ADMIN. Tạo bốn tài khoản demo bằng script trong `backend/target/` (Git bỏ qua): `demo_doctor`/DOCTOR, `demo_lab`/LAB_STAFF, `demo_head`/DEPARTMENT_HEAD, `demo_cashier`/CASHIER. Mỗi tài khoản có Staff riêng và mật khẩu ngẫu nhiên riêng, hash BCrypt; AccountRole.granted_by là ID của `admin.local`.
- Kiểm tra SQL trên DB chính: 4 Account ACTIVE, 4 Staff khác nhau, đúng role và grantor; đối chiếu BCrypt với file bàn giao PASS 4/4. Mật khẩu chỉ lưu trong `backend/target/demo-staff-credentials.txt` bị Git bỏ qua; không ghi mật khẩu vào tài liệu hoặc log.
- Đây chỉ là dữ liệu demo để đăng nhập theo role. Chưa tạo khoa/chuyên khoa hay phân công cho bác sĩ/trưởng khoa, chưa chạy luồng UI/API đăng nhập trong phiên này. Không đánh dấu P07 hoặc chức năng nghiệp vụ của các role là DONE.
- Việc tiếp theo duy nhất của roadmap vẫn là P05 — kiểm tra giao diện và luồng tài khoản trên browser với backend thử; P07 sẽ triển khai API/UI quản lý Staff theo lộ trình.

### Dọn dữ liệu phân công thử của demo_head (2026-11-02)

- Theo yêu cầu của chủ dự án, xóa trong một transaction trên `SuncareClinic`: 3 dòng `StaffDepartment`, 2 dòng `DepartmentHeadAssignment`, 0 dòng `StaffSpecialty` của riêng `demo_head`. Kiểm tra lại còn 0 dòng ở cả ba bảng.
- Giữ nguyên tài khoản và role `DEPARTMENT_HEAD`; không xóa phân công của nhân viên khác, khoa/chuyên khoa hoặc lịch. Trước khi xóa đã xác nhận không có WorkSchedule liên quan hai khoa này.

### P05 — kiểm tra lại ứng dụng đang chạy (2026-11-02)

- Đối chiếu Git và màn hình Edge đang mở tại `localhost:5173`: tài khoản `demo_doctor` hiển thị vai trò Bác sĩ và hai mục dự kiến đúng phạm vi; mở hộp xác nhận đăng xuất rồi chọn “Ở lại”, phiên và trang tài khoản được giữ. Không đọc hoặc ghi lại mật khẩu demo trong kết quả kiểm tra.
- `GET /api/health` trực tiếp cổng 8080 và qua Vite proxy cổng 5173 đều trả `UP`. Tại `frontend/`, `npm run lint` và `npm run build` PASS. Tại `backend/`, `.\mvnw.cmd test -q` với JDK 21 và JDBC auth DLL PASS (exit 0); đây là bộ test mặc định, không bao gồm `*IT`.
- Backend đang chạy trỏ DB chính theo cấu hình mặc định. Chưa gửi đăng ký, đăng nhập hoặc đăng xuất thật và chưa tạo dữ liệu thử trên DB chính. Cầu nối ảnh của cửa sổ Edge trả ảnh VS Code dù cây accessibility của Edge đọc được; vì vậy chưa có bằng chứng bố cục tại 320/375/768/1280 px. P05 tiếp tục `IN_PROGRESS`.
- Việc tiếp theo duy nhất: P05 — chạy backend với profile `migrationtest`, kiểm tra đăng ký/đăng nhập/đăng xuất bằng dữ liệu giả và xác minh responsive trên trình duyệt.

### P06.a — API hồ sơ cá nhân (2026-11-02)

- Chủ dự án xác nhận P05 đã hoàn thành; sổ trước đó chưa có bằng chứng browser/API toàn luồng nên trạng thái DONE ghi theo xác nhận này, không coi các mục chưa ghi là kiểm thử vừa thực hiện.
- P06 được tách thành P06.a API và P06.b UI. Thêm `GET`/`PUT /api/accounts/profile` cho tài khoản đã đăng nhập. API chỉ dùng account ID từ JWT đã kiểm tra; request không có account ID, patient ID, staff ID, role hoặc account type. Patient được sửa thông tin liên hệ/nhân thân thông thường; Staff chỉ sửa thông tin cá nhân, không sửa giấy phép hoặc trình độ. Avatar thuộc P39.
- Cập nhật có điều kiện `rowversion` (Base64) để trả HTTP 409 khi dữ liệu đã đổi; tên/mã hồ sơ, loại tài khoản và role không được cập nhật. Không đổi migration/database chính.
- Kiểm tra tại `backend/` với JDK 21 và JDBC auth DLL: `.\mvnw.cmd test -q` PASS; `.\mvnw.cmd '-Dtest=PersonalProfileIT' test -q` PASS 2 test trên `SuncareClinicMigrationTest`. Test xác nhận cần đăng nhập, đọc/sửa hồ sơ Patient, phiên bản cũ bị chặn, input sai và account LOCKED bị từ chối. Dữ liệu Patient test được xóa sau mỗi case. Chưa có test trực tiếp cho đường Staff, UI hoặc browser.
- Việc tiếp theo duy nhất: P06.b — tạo UI xem/sửa hồ sơ từ API thật, thử Patient và Staff, trạng thái tải/lỗi/thành công, lint/build và responsive.

### P06.b — màn hình hồ sơ cá nhân (2026-11-02)

- Thêm `PersonalProfile.jsx`, nối `GET`/`PUT /api/accounts/profile` trong `accountApi.js`, thay placeholder Tài khoản trong `App.jsx`. Form hiện mã hồ sơ/loại tài khoản chỉ đọc, các trường chung cho Patient/Staff và ba trường bổ sung chỉ cho Patient. Gửi `version` theo phản hồi API; hiển thị tải, lỗi mạng, lỗi trường, xung đột 409, thành công, hết phiên và nút tải lại/bỏ thay đổi.
- Tại `frontend/`: `npm run lint` PASS, `npm run build` PASS. `git diff --check` không có lỗi whitespace. Không thêm dependency, không thay backend hoặc database trong lượt P06.b.
- Browser bridge hiện không có tab Suncare khả dụng và ảnh cửa sổ Edge trước đó trả sai cửa sổ. Backend đang chạy cổng 8080 theo cấu hình mặc định DB chính; chưa gửi cập nhật từ UI để tránh sửa dữ liệu chính. Chưa xác minh trực quan 320/375/768/1280 px, luồng Patient/Staff qua browser, hoặc lỗi 409 thực tế trên UI. Vì vậy P06 vẫn `IN_PROGRESS`, chưa báo DONE.
- Việc tiếp theo duy nhất: P06.b — chạy backend thử với `migrationtest`, thử màn hình hồ sơ Patient và Staff bằng dữ liệu giả, xác minh 409 và responsive ở bốn kích thước rồi chốt P06 khi đạt.

### P06.b — chế độ xem trước khi sửa và thông tin chuyên môn Staff (2026-11-02)

- Theo góp ý của chủ dự án, hồ sơ mở ở chế độ xem chỉ đọc. Nút `Chỉnh sửa hồ sơ` mới hiện form và hai nút `Lưu thay đổi`/`Bỏ thay đổi`; lưu thành công hoặc hủy quay lại chế độ xem. `Tải lại` đọc lại phiên bản hồ sơ từ API.
- `GET /api/accounts/profile` trả thêm `professionalLicense` và `qualification` cho Staff; màn hình hiển thị trong mục Thông tin chuyên môn ở chế độ chỉ đọc, nếu trống ghi `Chưa cập nhật`. Không cho tự sửa giấy phép/trình độ, không đổi migration hay bảng. Patient không nhận các giá trị Staff này.
- `frontend/npm run lint` PASS, `npm run build` PASS. `backend/.\mvnw.cmd '-Dtest=PersonalProfileIT' test -q` PASS 3/3 trên `SuncareClinicMigrationTest`, gồm truy vấn Staff và kiểm tra hai trường chuyên môn; `git diff --check` không báo lỗi whitespace. Chưa thử trực quan/browser hoặc lưu hồ sơ Staff qua UI; P06 tiếp tục `IN_PROGRESS`.
- Việc tiếp theo duy nhất: P06.b — xác minh hai chế độ xem/sửa trên browser với backend thử, dữ liệu Patient/Staff giả và responsive rồi chốt P06 khi đạt.

### P06.b — kiểm tra API thật trên môi trường thử (2026-11-02)

- Chủ dự án xác nhận ảnh đại diện xử lý sau; vẫn thuộc P39. Thêm lựa chọn `SUNCARE_API_TARGET` trong `frontend/vite.config.js` để có thể chạy frontend thử ở cổng 5174 với backend `migrationtest` ở 8081 mà không thay proxy mặc định 8080.
- Khởi động backend profile `migrationtest` trên 8081, Health API trả `UP`; Vite thử trên 5174. Tạo Patient giả ở DB thử, login qua API, `GET /api/accounts/profile` trả `PATIENT`, `PUT` lưu số điện thoại giả, `version` thay đổi; gửi lại cùng `version` cũ nhận HTTP 409. Không thử ghi vào DB chính.
- Hai Account thử (ID 37, 38) và Patient liên quan (ID 29, 30), cùng AuthToken/AccountRole, đã được xóa trên `SuncareClinicMigrationTest`; truy vấn còn 0 Account thử. Tệp credential tạm trong `backend/target/` đã xóa. Đã dừng riêng server thử 8081/5174, giữ phiên 8080/5173 đang có.
- `frontend/npm run build` PASS. Lint lần đầu phát hiện `process` chưa import trong Vite config; đã sửa và chạy lại `npm run lint` PASS. Cửa sổ Edge bị thu nhỏ và browser bridge vẫn không khả dụng nên chưa xác minh tương tác UI hay responsive, Staff qua browser cũng chưa thử. P06 tiếp tục `IN_PROGRESS`.
- Việc tiếp theo duy nhất: P06.b — thử chế độ xem/sửa Patient và Staff trên browser với backend thử, đo responsive 320/375/768/1280 px rồi chốt P06 khi đạt.

### P06 — xác nhận của chủ dự án và P07.a (2026-11-02)

- Chủ dự án xác nhận đã tự test P06 và thấy ổn, yêu cầu sang task tiếp theo; chuyển P06 thành DONE theo xác nhận này. Không ghi các kiểm tra browser thiếu log của agent thành kết quả agent đã chạy. Ảnh đại diện vẫn để P39.
- Tách P07: P07.a API tạo Staff/Account/role; P07.b API gán/thu hồi role và khóa tài khoản; P07.c UI quản lý tối thiểu cùng kiểm tra toàn luồng. P07.a thêm `POST /api/admin/staff`, cần quyền `ACCOUNT_MANAGE`. Request nhận thông tin Staff, mật khẩu tối thiểu 12 ký tự và danh sách role Staff cụ thể; tạo Staff, Account loại `STAFF`, gán role với `granted_by` là ID admin trong cùng transaction. BCrypt hash mật khẩu; login và mã Staff duy nhất. Chưa mở tạo `SYSTEM_ADMIN` qua API này vì chính sách cấp thêm admin chưa chốt.
- Tài liệu `docs/05-giao-dich-rang-buoc.md` TX01 có cụm “role STAFF”, trái `AGENTS.md`, V12 và schema: `STAFF` là account type, không phải role. P07.a dùng các role `DOCTOR`, `LAB_STAFF`, `DEPARTMENT_HEAD`, `CASHIER` đã seed; chưa sửa tài liệu nghiệp vụ. Cần chốt cách diễn đạt/chính sách `SYSTEM_ADMIN` trước khi mở rộng API cấp role.
- Kiểm tra tại `backend/` với JDK 21 và JDBC auth DLL: `.\mvnw.cmd '-Dtest=StaffManagementIT' test -q` PASS 4/4 trên `SuncareClinicMigrationTest`, gồm hai role cùng grantor admin, không đăng nhập/role sai/login trùng, quyền bị thu hồi, rollback khi grant lỗi. Lần đầu fixture thiếu `granted_at` nên test fail trước khi vào API; đã sửa fixture và chạy lại PASS. `.\mvnw.cmd test -q` PASS; `git diff --check` không có lỗi whitespace. Truy vấn DB thử sau test còn 0 Account `p07.admin.%`/`p07.staff.%`.
- Chưa làm API thu hồi/khóa hoặc UI, chưa chạy test trên DB chính. Việc tiếp theo duy nhất: P07.b — API gán/thu hồi role và khóa tài khoản Staff, kiểm tra quyền, không tự khóa admin cuối cùng và hiệu lực quyền ở request tiếp theo.

### P07.b/P07.c — quản lý role, khóa tài khoản và UI admin (2026-11-02)

- Thêm `GET /api/admin/staff`, `POST /api/admin/staff/{accountId}/roles`, `DELETE /api/admin/staff/{accountId}/roles/{roleCode}`, `PATCH /api/admin/staff/{accountId}/status`. Tất cả cần `ACCOUNT_MANAGE`; đối tượng phải là Account loại `STAFF`. Khóa Account khi đổi role/trạng thái để tránh hai thao tác đồng thời. Admin không thể tự đổi hoặc đổi tài khoản đang có `SYSTEM_ADMIN`; API thường chỉ quản lý bốn role `DOCTOR`, `LAB_STAFF`, `DEPARTMENT_HEAD`, `CASHIER`.
- Tạo/đổi role/trạng thái ghi `AuditEvent` cùng transaction; grant ghi `AccountRole.granted_by` là ID admin. Thu hồi role và khóa có hiệu lực ở request tiếp theo nhờ bộ lọc xác thực đọc DB. Security trả JSON tiếng Việt khi 403. UI `StaffManagement.jsx` cho tạo Staff, xem danh sách, gán/thu hồi role và khóa/mở khóa với bước xác nhận; `App.jsx` chỉ hiện mục quản lý khi có `ACCOUNT_MANAGE`.
- `backend/.\mvnw.cmd '-Dtest=StaffManagementIT' test -q` PASS 7/7 trên `SuncareClinicMigrationTest`: hai role, grantor, anonymous/role sai/login trùng, rollback tạo và audit, thu hồi quyền admin, role phản ánh request sau, khóa làm token mất hiệu lực, bảo vệ admin. `.\mvnw.cmd test -q` PASS. `frontend/npm run lint` và `npm run build` PASS. `git diff --check` không có lỗi whitespace.
- Chạy backend thử ở 8081 và Vite thử ở 5174; REST smoke bằng admin/Staff giả trên DB thử PASS: tạo, list, grant, revoke, khóa. Đã xóa Account ID 81–82, Staff ID 50–51, token/role/audit thử; còn 0 Account `p07.%`. Đã dừng server thử, giữ phiên 8080/5173 của người dùng.
- Cửa sổ Edge có thao tác người dùng đồng thời, bridge trình duyệt không khả dụng/geometry lỗi, nên chưa kiểm tra trực quan UI admin tại 320/375/768/1280 px hoặc bấm toàn bộ luồng qua browser. P07 vẫn `IN_PROGRESS`, chưa tuyên bố DONE. AT03 mới kiểm được Account đa role và quyền đổi ở request sau; giới hạn theo khoa/phòng phải kiểm khi P10/P13 triển khai dữ liệu phân công. Chưa chạy trên DB chính.
- Việc tiếp theo duy nhất: P07.c — thử trực quan và tương tác UI admin trên browser với backend `migrationtest`, xử lý lỗi phát hiện rồi chốt P07 khi đạt.
