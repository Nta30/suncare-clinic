# Repository Guidelines

## Phạm vi và thứ tự áp dụng chỉ dẫn

- Yêu cầu hiện tại của người dùng quyết định phạm vi và ưu tiên. Quy tắc nghiệp vụ, bảo mật và dữ liệu của dự án vẫn theo hướng dẫn repo, tài liệu nghiệp vụ và API; nếu yêu cầu mới thay đổi một quyết định nghiệp vụ, làm rõ ảnh hưởng trước khi sửa phần phụ thuộc.
- `AGENTS.md` ở root quản lý quy trình chung, roadmap, nghiệp vụ, dữ liệu và kiểm chứng. `AGENTS.md` trong thư mục con chỉ bổ sung quy tắc cho thư mục đó, không thay đổi hợp đồng hoặc quy trình chung.
- Skill chỉ hướng dẫn phần chuyên môn của skill. Chỉ dùng phần phù hợp với yêu cầu và stack hiện tại; skill không mở rộng phạm vi công việc hoặc ghi đè quy tắc repo.
- Nếu tài liệu nghiệp vụ, schema, code hoặc API mâu thuẫn về một quyết định ảnh hưởng nghiệp vụ, nêu rõ nguồn và điểm khác nhau; dừng phần phụ thuộc cho tới khi có quyết định.

## Project Structure & Module Organization
- `frontend/src/` contains the React JavaScript UI; keep API calls in `services/`, imported images in `assets/`, and global styles in `index.css`. Static public assets belong in `frontend/public/`.
- `backend/src/main/java/com/suncare/clinic/` contains Spring Boot code, including `common/controller/HealthController.java`. Organize new business functionality by module.
- `backend/src/main/resources/` holds configuration and Flyway SQL migrations in `db/migration/`; backend tests live in `backend/src/test/java/`.
- `docs/` contains project handoffs, the single implementation ledger at `docs/implementation/PROGRESS.md`, and `database/SCHEMA_PLAN.md`. Check implementation against the relevant documents and update `PROGRESS.md` after significant milestones; do not create additional project-state ledgers.

## Build, Test, and Development Commands
Run commands from the indicated directory, not the repository root.

In `frontend/`:
- `npm install` installs dependencies.
- `npm run dev` starts Vite on port 5173; `/api` proxies to port 8080.
- `npm run lint` runs ESLint, including React Hooks and Refresh rules.
- `npm run build` creates `dist/`; `npm run preview` previews that build.

In `backend/`, use Java 21 and the Maven wrapper:
- `.\mvnw.cmd spring-boot:run` starts the API.
- `.\mvnw.cmd test` runs default tests.
- `.\mvnw.cmd package` tests and builds the JAR in `target/`.
- `.\mvnw.cmd "-Dtest=SqlServerConnectivityIT" test` explicitly runs database integration checks.

## Coding Style & Naming Conventions
Use two-space indentation, single-quoted JavaScript strings, and no semicolons in frontend code. Keep React components PascalCase and functions camelCase. Retain JavaScript, Tailwind CSS v4, and Font Awesome. For Java, follow surrounding formatting; use four spaces in new classes, PascalCase classes, and camelCase methods. Name new migrations `V<number>__<snake_case_description>.sql`; preserve applied migrations.

## Testing Guidelines
Backend tests use JUnit Jupiter and Spring Boot testing. Use `*Tests` for default tests and `*IT` for explicitly invoked integration tests. The connectivity test requires configured SQL Server native authentication and asserts database `SuncareClinic` with 58 business tables. No coverage threshold or frontend test runner is configured. Run lint/build for UI changes and manually verify responsive layout and `/api/health` success/error states.

## Commit & Pull Request Guidelines
History uses short descriptive subjects, such as `Create initial website`, without a consistent prefix convention. Write focused, action-oriented commits. PRs should describe changes, link relevant issues, report validation and database prerequisites, and include screenshots for UI changes.

## Security & Configuration
Keep credentials in ignored local configuration or environment variables. Never commit secrets or real patient data. Flyway is disabled by default; validate schema changes against the `migrationtest` database before deliberately using `migrationmain`.

## Cách làm việc cho đồ án và các phiên tiếp theo

Phần này hướng dẫn triển khai từng bước; danh sách bên dưới là kế hoạch, **không phải tính năng đã hoàn thành**. Trao đổi, giải thích và bàn giao bằng tiếng Việt. Mục tiêu là code sinh viên có thể đọc, hiểu và trình bày khi bảo vệ đồ án, nhưng vẫn đúng nghiệp vụ và bảo vệ dữ liệu.

### Đọc gì trước khi bắt đầu

1. Đọc `AGENTS.md`, chạy `git status --short`, xem code của phần sắp làm; giữ nguyên thay đổi đang có của người dùng.
2. Đọc `docs/implementation/PROGRESS.md` để chọn việc tiếp theo. Đây là sổ tiến độ duy nhất; không sinh thêm `PROJECT_STATE_3`, `_4` qua mỗi phiên.
3. Đọc `docs/PROJECT_STATE_2.md` để biết mốc bàn giao database ngày 2026-11-02. `docs/PROJECT_STATE.md` là tài liệu cũ, có nội dung chưa cập nhật; không lấy đó làm lý do tạo lại database.
4. Đọc tài liệu liên quan đến việc đang làm: `01-nghiep-vu.md` (phạm vi/quyết định), `02-erd.md` (quan hệ), `03-tu-dien-du-lieu.md` (dữ liệu), `04-trang-thai-phan-quyen.md` (quyền/trạng thái), `05-giao-dich-rang-buoc.md` (transaction), `06-bao-cao-kiem-chung.md` (báo cáo/AT01–AT49), trong `docs/`.
5. Đối chiếu với SQL trong `backend/src/main/resources/db/migration/` và `docs/database/SCHEMA_PLAN.md`. Các tài liệu 01–06 hiện đã có trong repository; không cần lấy bản ở folder `Suncare` bên ngoài.

Code và SQL cho biết hiện trạng triển khai; tài liệu 01–06 cho biết yêu cầu cần đạt. Nếu mâu thuẫn, ghi rõ file/cột/quy tắc và hỏi về quyết định nghiệp vụ bị ảnh hưởng; không tự sửa yêu cầu để khớp code. Mốc database trong handoff là kết quả được ghi nhận trước đó, không phải bằng chứng vừa chạy kiểm thử ở phiên hiện tại.

### Quy tắc chia việc và kết thúc phiên

- Khi người dùng nói “làm tiếp”, chọn việc chưa xong đầu tiên có đủ điều kiện trong `PROGRESS.md`; nếu chưa có thì bắt đầu `P00`. Nếu người dùng chỉ định việc khác, kiểm tra phụ thuộc trước.
- Mặc định hoàn thành **một việc nhỏ có mã** mỗi lượt yêu cầu. Không tự triển khai toàn bộ roadmap. Việc lớn tiếp tục chia thành `Pxx.a`, `Pxx.b` với đầu ra và kiểm tra riêng trước khi code.
- Trước khi sửa, nêu ngắn gọn mục tiêu, file dự kiến và cách kiểm tra. Tự xử lý lựa chọn kỹ thuật thông thường; chỉ hỏi khi thiếu quyết định ảnh hưởng nghiệp vụ/phạm vi. Không hỏi xác nhận lại cho từng file.
- Hoàn thành xuyên suốt phạm vi đã chọn: code, kiểm tra phù hợp, cập nhật tiến độ và giải thích. Nếu việc chỉ làm API, ghi rõ UI còn ở bước sau; không đánh dấu toàn bộ chức năng hoàn thành.
- Nếu thiếu SQL Server, cấu hình hoặc tài khoản dịch vụ: ghi chính xác phần đã làm, phần chưa kiểm tra và cách tiếp tục. Không báo PASS từ việc chỉ đọc code hoặc dùng mock.
- Cuối phiên báo mã việc, thay đổi chính, cách chạy/thử, kết quả, vấn đề còn lại và đúng một việc tiếp theo. Không tự commit/push nếu chưa được yêu cầu.

### Code đơn giản, đúng folder đã chia

- Backend giữ `com.suncare.clinic.modules.<module>`; tạo `controller/`, `service/`, `repository/`, `dto/`, `model/` bên trong module khi cần. Ví dụ: `modules/account/service/RegistrationService.java`. Không tạo trước hàng loạt class cho cả 58 bảng.
- Controller nhận/validate request và trả HTTP; Service xử lý nghiệp vụ/transaction; Repository chứa SQL tham số hóa. Ưu tiên `JdbcTemplate` hoặc `NamedParameterJdbcTemplate` theo dependency JDBC hiện có. Không tự thêm JPA/Hibernate hay đổi cách truy cập DB giữa chừng.
- Dùng class cụ thể, constructor injection, DTO có tên rõ nghĩa và mapping tường minh. Không bắt buộc interface + implementation cho mỗi service; tránh generic CRUD framework, microservices, CQRS, event bus hoặc abstraction chưa có nhu cầu.
- Dùng `common/config`, `common/exception`, `common/response` cho cấu hình, xử lý lỗi và cấu trúc phản hồi thực sự dùng chung. Không nhét toàn bộ nghiệp vụ vào `common` hoặc `HealthController`.
- Frontend giữ `features/<module>/` cho màn hình/component riêng; `components/ui/` cho thành phần dùng chung, `components/layout/` cho khung trang, `services/` cho gọi API, `routes/` cho điều hướng, `hooks/` và `utils/` khi có nhu cầu dùng lại. Giữ `App.jsx` gọn dần theo tính năng đang làm.
- Ưu tiên function component, `useState`, `useEffect`, `fetch`; không tự thêm Redux hoặc thư viện form/UI lớn. Thêm dependency chỉ khi bước hiện tại cần, giải thích mục đích. Không chuyển sang TypeScript.
- Tên biến/class tiếng Anh rõ nghĩa, giao diện và thông báo dễ hiểu bằng tiếng Việt. Comment ngắn giải thích quy tắc khó, nhất là khóa/tiền/trạng thái; tránh comment lặp lại từng dòng code. Không cố tình làm code kém chất lượng để “giống sinh viên”.

### Phân công module theo schema

| Module backend / feature frontend | Trách nhiệm chính | SQL tham chiếu |
|---|---|---|
| `account` | Patient, Staff, Account, Role, Permission, cấp/khóa tài khoản | V1–V2 |
| `organization` | Khoa, chuyên khoa, phòng, phân khoa/chuyên khoa/trưởng khoa | V1–V2 |
| `scheduling` | Ca, lịch làm việc, phân công, công bố, sinh OnlineSlot | V3, V5 |
| `appointment` | Tìm lịch, BookingHold, SlotReservation, Appointment và event | V5 |
| `encounter` | Bắt đầu khám, tiền sử/dị ứng, snapshot, sinh hiệu, chẩn đoán, hoàn tất | V6 |
| `clinical` | Danh mục Service/giá CLS/phòng phục vụ, chỉ định, queue, kết quả, review | V3, V7 |
| `prescription` | Danh mục Drug, đơn và chi tiết đơn | V8 |
| `billing` | Giá khám theo bác sĩ/hình thức, hóa đơn, thu/phân bổ/hoàn tiền | V3, V9 |
| `medicalrecord` | Truy vấn EMR từ hồ sơ đã có, không tạo bảng EMR sao chép | V5–V8 |
| `media` | Cloudinary, FileObject, quyền truy cập tệp | V4, V7 |
| `video` | VideoSession và lịch sử tham gia | V10 |
| `reporting` | Dashboard, CSV đúng phạm vi; tổng hợp không cộng trùng | V11 và các bảng nghiệp vụ |

Service giá CLS và giá khám cần thống nhất kiểm tra hiệu lực, không viết hai cách tính tiền khác nhau. `SystemSetting`, `CommandReceipt`, `AuditEvent` ở V4 là phần hỗ trợ dùng chung; chỉ thêm mã hỗ trợ khi use case cần, không xây framework tổng quát.

### Những quy tắc không được đơn giản hóa sai

- Giữ phạm vi một cơ sở ngoại trú; không thêm nội trú, BHYT, tồn kho/bán thuốc, giảm giá, kế toán hay hóa đơn thuế.
- Một Account thuộc Patient **hoặc** Staff; chỉ bệnh nhân tự đăng ký. Role thực tế là `PATIENT`, `DOCTOR`, `LAB_STAFF`, `DEPARTMENT_HEAD`, `CASHIER`, `SYSTEM_ADMIN`; `STAFF` là account type, không phải role trong SQL. `DEPARTMENT_HEAD` là quyền bổ sung cho `DOCTOR`; phân công trưởng khoa mới xác định khoa và thời hạn.
- Phân quyền ở backend theo hành động **và** chủ sở hữu/bác sĩ phụ trách/khoa/phòng trực. Ẩn nút frontend không thay kiểm tra quyền; admin không tự có quyền sửa bệnh án. Khóa tài khoản/thu hồi role phải có hiệu lực ở request tiếp theo.
- Trực tiếp chỉ chọn chuyên khoa/ngày/ca; phân bác sĩ theo tỷ lệ lượt đặt/sức chứa, hòa theo mã. Không cho bệnh nhân chọn bác sĩ, hủy hoặc đổi lịch trực tiếp.
- Online có slot 20 phút, giữ chỗ 5 phút. Chỉ tạo Appointment + Encounter sau khi xác minh MoMo hợp lệ trong hạn. Hủy online khi cách giờ hẹn ít nhất 24 giờ và chưa khám; không đổi lịch, không tự hồi sinh booking khi tiền đến muộn.
- Phí khám chỉ thu một lần; CLS phát sinh sau hóa đơn đã phát hành có hóa đơn bổ sung. Phân bổ tiền theo dòng; thu một phần chưa đồng nghĩa mọi chỉ định đã trả đủ. Chỉ CASHIER xác nhận tiền mặt, MoMo do hệ thống xác minh.
- CLS đủ tiền mới READY; lab xác nhận có mặt mới vào queue. Hạn mặc định 10 ngày chốt lúc trả đủ; hoãn giữ Order/Encounter/tiền/hạn. Hết hạn khi chưa bắt đầu thì hủy dòng thu và yêu cầu hoàn đúng phần tiền. Đã bắt đầu trong hạn được tiếp tục.
- Hoàn tất khám cần chẩn đoán FINAL, kết quả hiện hành đã review hoặc chỉ định đã hủy, không còn khoản phải thu/đơn DRAFT. Đơn thuốc không bắt buộc; chờ hoàn tiền không chặn hoàn tất.
- Không sửa đè kết quả/đơn/chứng từ đã phát hành hoặc xóa lịch sử thu/hoàn/event. Đính chính kết quả bằng phiên bản mới; giữ snapshot tên/giá/liều, truy mã định danh qua FK.
- Dùng `BigDecimal` cho tiền, UTC cho thời điểm và `Asia/Saigon` cho ngày hiển thị/lọc; `rowversion` là dữ liệu nhị phân chống mất cập nhật. Không dùng `double` tính tiền hay coi `rowversion` là ngày giờ.
- Các thao tác nhiều bảng có transaction, event/audit theo tài liệu 04–05; chống đặt trùng, thu trùng và hoàn trùng bằng khóa SQL/idempotency. Không chỉ “SELECT rồi INSERT” hoặc chặn double-click. Không giữ transaction khi gọi MoMo, upload hay tạo bản in.
- Không sửa V1–V11 đã áp dụng; thay schema bằng phiên bản tiếp theo còn trống sau khi đối chiếu. Không tự reset database hoặc chạy lại migration chính để bắt đầu phiên. Kiểm thử ghi dữ liệu trên database thử với dữ liệu giả.

## Lộ trình công việc nhỏ

Thực hiện theo thứ tự phụ thuộc dưới đây. Mỗi hàng là một đầu ra riêng; màn hình phải có trạng thái tải/rỗng/lỗi/thành công và gọi API thật khi được đánh dấu hoàn thành. Các mã AT tham chiếu `docs/06-bao-cao-kiem-chung.md`, là yêu cầu kiểm chứng chứ chưa là kết quả PASS.

### Giai đoạn A — Nền tảng và tài khoản

| Mã | Việc và điều kiện nghiệm thu |
|---|---|
| P00 | Lập `PROGRESS.md`, xác minh hiện trạng/source và lệnh build/test nền; tách lỗi môi trường khỏi lỗi code, ghi điểm chưa chốt trước P02/P04. |
| P01 | Tạo xử lý lỗi/validation dùng chung tối thiểu; request sai trả lỗi rõ ràng, không lộ stack trace/secret, Health API vẫn chạy. |
| P02 | Tạo migration V12 cho `AccountRole.granted_by` nullable; đăng ký công khai/bootstrap để NULL, admin gán role staff ghi ID admin. Chuẩn bị role/permission và bootstrap admin trên môi trường phát triển; chạy lại không nhân đôi, không hardcode mật khẩu. Thử migration ở DB thử trước DB chính. |
| P03 | API đăng ký Patient + Account + AccountRole trong một transaction, hash mật khẩu; kiểm tra trùng login, rollback và chặn tự cấp role/staff_id (TX01, AT01–02). |
| P04 | Đăng nhập/đăng xuất/xem tài khoản hiện tại bằng Spring Security; ghi lựa chọn session hoặc token, kiểm tra tài khoản khóa và thu hồi quyền ở request tiếp theo. Không tự viết thuật toán mã hóa. |
| P05 | UI đăng ký/đăng nhập, khung điều hướng theo quyền trong folder hiện có; thử hợp lệ, sai thông tin, chưa đăng nhập và đăng xuất. |
| P06 | API và UI xem/sửa hồ sơ cá nhân được phép; không sửa type/role/liên kết hồ sơ qua request, không truy cập hồ sơ người khác. Chưa cần upload ảnh. |
| P07 | Admin tạo Staff + Account, gán/thu hồi role và khóa tài khoản; UI quản lý tối thiểu, rollback khi lỗi, kiểm tra tài khoản đa role đúng phạm vi (AT03). |

### Giai đoạn B — Danh mục, lịch và đặt trực tiếp

| Mã | Việc và điều kiện nghiệm thu |
|---|---|
| P08 | Quản lý Department/Specialty và liên kết; API + UI danh sách/form, mã duy nhất và ngừng hoạt động thay xóa lịch sử. |
| P09 | Quản lý Room; kiểm tra loại phòng/khoa, hiển thị và lọc đúng dữ liệu liên quan. |
| P10 | Phân khoa/chuyên khoa/trưởng khoa cho Staff; chặn khoảng hiệu lực chồng nhau, giữ lịch sử và giới hạn quyền trưởng khoa. |
| P11 | Danh mục Service, RoomService và giá CLS; kiểm tra loại dịch vụ, phòng hỗ trợ, giá dương và khoảng hiệu lực. |
| P12 | Giá khám theo bác sĩ/hình thức và ShiftDefinition; có UI cấu hình, không tự coi giờ ca/giá mẫu là chính sách thật. |
| P13 | Lịch DRAFT và WorkAssignment; UI phân công tối thiểu, kiểm tra vai trò/khoa/phòng/thời gian/sức chứa. |
| P14 | Công bố lịch bằng transaction, chống giao lịch và sinh slot online 20 phút; chặn sửa phân công đã có booking/hold (TX02, AT08). |
| P15 | API + UI tra cứu chuyên khoa/ngày/ca còn chỗ trực tiếp; dùng lịch công bố và giá còn hiệu lực. |
| P16 | Đặt trực tiếp tạo Appointment + Encounter/event nguyên tử; kiểm tra cân bằng, retry, hai request giành chỗ cuối (TX03, AT04–06, AT17, AT45). |
| P17 | UI bệnh nhân xem lịch của mình, bác sĩ xem lịch được giao; không lộ lịch người khác, không có thao tác đổi/hủy trực tiếp. |

### Giai đoạn C — Khám trực tiếp và thu tiền cơ bản

| Mã | Việc và điều kiện nghiệm thu |
|---|---|
| P18 | Hồ sơ tiền sử/dị ứng và bắt đầu Encounter; bác sĩ đúng phân công, snapshot một lần, ghi event. |
| P19 | UI khám và API ghi sinh hiệu, ghi chú, chẩn đoán sơ bộ/FINAL; validate dữ liệu và không sửa lượt khám ngoài quyền. |
| P20 | Bác sĩ lập/phát hành hóa đơn phí khám trực tiếp, chốt snapshot; chặn phí khám lần hai, không cần CLS mới lập hóa đơn. |
| P21 | Thu ngân thu tiền mặt và phân bổ theo dòng; UI số dư, chống thu vượt/retry; chuẩn bị test thu một phần (TX05, AT21, AT45). |
| P22 | Danh mục thuốc rồi đơn DRAFT/ISSUED: tách thành hai việc con; validate liều/số lượng, snapshot, bản phát hành bất biến. |
| P23 | Hoàn tất lượt khám không CLS, cập nhật Appointment cùng transaction; thử còn nợ/thiếu FINAL/đơn DRAFT bị chặn, không có đơn vẫn được hoàn tất (AT18, AT34). |

### Giai đoạn D — Chỉ định, kết quả và hoàn tiền

| Mã | Việc và điều kiện nghiệm thu |
|---|---|
| P24 | Tạo ClinicalOrder và dòng hóa đơn nháp nguyên tử, giữ giá; sau phát hành tạo hóa đơn bổ sung. Thu đủ từng dòng mới READY và chốt hạn (TX07, AT20–23, AT26). |
| P25 | Lab check-in → queue → tiếp nhận → bắt đầu; API + UI đúng phòng/lịch trực, kiểm tra hạn dưới khóa và một queue active/order (TX08, AT24, AT27–28). |
| P26 | Hoãn/quay lại CLS và Encounter WAITING_CLS; giữ cùng chỉ định, không thu lại hoặc gia hạn (AT25). |
| P27 | Nhập chỉ số/báo cáo, xác nhận/phát hành ResultVersion; hoàn tất Order/queue, bản phát hành không sửa. Tệp đính kèm thực hiện tại P40. |
| P28 | Bác sĩ review đúng phiên bản và hoàn tất Encounter có CLS; không review chéo lượt khám, kết quả mới cần review mới (TX10, AT34–36). |
| P29 | Hủy chỉ định trước tiếp nhận/hết hạn, hủy InvoiceLine và tạo RefundRequest; thử job lặp, đua với START và rollback toàn bộ (TX09, AT29–32, AT49). |
| P30 | Thu ngân xử lý hoàn thủ công với bằng chứng sau khi chốt quyền vận hành; chống chi vượt/trùng, không ghi Refund giả khi thất bại (TX11, AT33). |

### Giai đoạn E — MoMo và khám online

| Mã | Việc và điều kiện nghiệm thu |
|---|---|
| P31 | Cấu hình MoMo sandbox và PaymentRequest/QR cho hóa đơn; đọc tài liệu nhà cung cấp tại thời điểm tích hợp, chưa coi redirect là đã thu. |
| P32 | Callback/đối soát: xác minh, PaymentEvent, Payment/Allocation, hoàn tiền thừa; thử callback giả/lặp, tiền mặt và MoMo đồng thời (TX05, AT12–13, AT22). |
| P33 | API + UI tìm slot/chọn hoặc tự phân bác sĩ online; giữ chỗ + reservation + hóa đơn phí khám trong transaction, chưa tạo Appointment (TX04, AT07, AT09–10). |
| P34 | Xác nhận booking sau MoMo, xử lý hết hạn/tiền muộn; thử biên 5 phút, slot tái cấp và rollback (AT11, AT14–15). |
| P35 | Hủy online hợp lệ, giải phóng reservation, hủy dòng phí và yêu cầu hoàn; thử đúng/sát mốc 24 giờ và chặn đổi lịch (TX06, AT16–17). |
| P36 | Bảng khai trước khám và màn hình khám online; tái sử dụng Encounter/CLS, hóa đơn bổ sung không thu lại phí khám (AT19). |
| P37 | Tích hợp video sau khi chốt nhà cung cấp; quyền tham gia, lịch sử kết nối lại, không ghi hình/không tự hoàn tất khi ngắt kết nối (AT44). |
| P38 | Bác sĩ ghi nhận NO_SHOW theo điều kiện tài liệu; hỏi chính sách trực tiếp còn mở trước khi bật nhánh đó, không tự hủy/hoàn tiền. |

### Giai đoạn F — Tệp, hồ sơ, báo cáo và bàn giao

| Mã | Việc và điều kiện nghiệm thu |
|---|---|
| P39 | Upload avatar qua Cloudinary, thay ảnh đúng quyền/version; DB lỗi không xóa ảnh cũ, DB chỉ lưu URL/public_id (AT46). |
| P40 | Tệp CLS gắn ResultVersion và đọc có kiểm tra quyền; đính chính bằng successor sau khi chốt quy trình, không sửa/xóa bản cũ (AT35, AT47). |
| P41 | EMR cho bệnh nhân/bác sĩ đúng phạm vi, chỉ trả nội dung được công bố; không tạo bản sao bệnh án, không lộ draft/file người khác (AT37–38, AT48). |
| P42 | Bản in/HTML/PDF hóa đơn nội bộ có snapshot và trạng thái QR hiện hành; không lấy giá/tên mới thay nội dung đã chốt (AT43). |
| P43 | Dashboard và truy vấn thống kê theo vai trò; tách việc con theo lượt khám/lịch/CLS/thu-hoàn/workload, kiểm tra tổng không nhân do JOIN và ngày Việt Nam (AT39–41). |
| P44 | CSV từ các truy vấn đã kiểm tra; UTF-8 BOM, quoting, chống công thức, lọc quyền phía server và audit xuất (AT42). |
| P45 | Tạo bộ dữ liệu demo giả và checklist AT01–AT49; hoàn thiện từng nhóm còn thiếu, chạy cạnh tranh bằng hai kết nối SQL Server độc lập. |
| P46 | Chạy kịch bản demo đầu-cuối theo từng vai trò, sửa lỗi cản luồng, kiểm tra responsive và cấu hình local; ghi lệnh cài/chạy cùng tài khoản demo an toàn. |
| P47 | Hoàn thiện tài liệu kiến trúc/API/ERD, kết quả kiểm thử, giới hạn và kịch bản bảo vệ đồ án; chỉ tuyên bố hoàn thành phần có bằng chứng, tách triển khai Internet nếu được yêu cầu. |

P24 dùng nền hóa đơn/thu tiền P20–P21; P29 phải có cơ chế tạo yêu cầu hoàn trước khi bật job. P33–P35 cần P31–P32 để nghiệm thu luồng online đầy đủ. Có thể làm P39 sau P06 và P41 từng phần sau P23/P28; ghi rõ đổi thứ tự trong tiến độ. Không dùng mock thanh toán/video để đánh dấu tích hợp thật hoàn thành.

## Kiểm tra và mẫu bàn giao bắt buộc

Một việc chỉ `DONE` khi đầu ra đúng phạm vi, kiểm tra liên quan đạt và tiến độ được cập nhật. API ghi dữ liệu cần thử thành công, input sai, sai quyền/chủ sở hữu và rollback khi có nhiều bước. Tính năng tranh chấp cần kiểm thử SQL Server thật; unit test không thay được kiểm tra khóa/ràng buộc. UI cần lint/build và thử luồng thực tế; không chạy toàn bộ AT01–AT49 sau mỗi thay đổi nhỏ.

`SqlServerConnectivityIT` hiện hardcode tên `SuncareClinic` và 58 bảng. Không chỉ đổi sang profile `migrationtest` rồi kỳ vọng test này qua; chuẩn bị cấu hình/assertion phù hợp cho test nghiệp vụ trên DB thử. `.\mvnw.cmd test` không tự chạy tên `*IT`; ghi lệnh chạy đích danh. Không điều chỉnh assertion chỉ để che lỗi schema.

Mẫu ghi trong `docs/implementation/PROGRESS.md` (tạo từ P00, cập nhật cùng thay đổi):

```markdown
# Tiến độ triển khai Suncare Clinic
Cập nhật: YYYY-MM-DD
Việc hiện tại: Pxx — tên việc
Trạng thái: TODO | IN_PROGRESS | BLOCKED | DONE

## Kết quả phiên này
- Phạm vi và file đã thay đổi:
- API/UI/migration đã thêm:
- Quyết định kỹ thuật và lý do:
- Kiểm tra: lệnh, môi trường/database, kết quả thực tế, AT liên quan.
- Chưa kiểm tra/lỗi còn lại/đầu vào còn thiếu:

## Danh sách công việc
| Mã | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| P00 | ... | ... |

## Bàn giao phiên sau
- Việc tiếp theo: Pxx — một mục tiêu cụ thể.
- Đọc trước: các file liên quan.
- Điều kiện cần / câu hỏi chưa chốt:
- Cách kiểm tra và kết quả mong đợi:
```

### Những điểm cần hỏi đúng thời điểm

- Quyết định 2026-11-02 của chủ dự án: bệnh nhân tự đăng ký được để `AccountRole.granted_by = NULL`; bootstrap admin cũng để NULL vì chưa có tài khoản cấp quyền. Admin gán role nhân viên phải ghi ID tài khoản admin. V12 đã đổi cột thành nullable và seed role/permission trên DB thử rồi DB chính; V1–V11 giữ nguyên. Tài khoản admin trên DB chính chỉ tạo khi chủ dự án nhập mật khẩu qua biến môi trường.
- Trước P04: nếu môn học có yêu cầu bắt buộc về JWT/session hoặc thư viện, hỏi một lần và ghi quyết định; nếu không có thì chọn giải pháp đơn giản, nhất quán với yêu cầu thu hồi quyền.
- Trước cấu hình lịch/giá: giờ ca, sức chứa, giá thật; dữ liệu demo phải ghi rõ là giả. Không tự đặt chính sách bác sĩ nghỉ đột xuất, đổi phân công đã có bệnh nhân.
- Trước P30–P32/P37/P39: xác nhận người chi/duyệt hoàn, cấu hình MoMo sandbox, nhà cung cấp video, Cloudinary và giới hạn tệp. Hướng dẫn đặt secret trong cấu hình local, không yêu cầu ghi secret vào tài liệu bàn giao.
- Trước P38/P40: chính sách vắng mặt trực tiếp và thông báo/xử lý đính chính kết quả sau hoàn tất khám còn mở trong tài liệu 01. Chỉ dừng phần phụ thuộc câu trả lời; tiếp tục phần độc lập nếu có.
