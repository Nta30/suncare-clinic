# Tiến độ triển khai Suncare Clinic

Cập nhật: 2026-11-02

Việc tiếp theo: P05 — UI đăng ký, đăng nhập và điều hướng theo quyền
Trạng thái: IN_PROGRESS (P05; P00–P04 đã DONE)

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
| P05 | IN_PROGRESS | Đã có UI đăng ký/đăng nhập/logout và API thật; lint/build PASS. Còn kiểm tra trực quan responsive và luồng trên browser. |
| P06 | TODO | Theo lộ trình trong AGENTS.md. |
| P07 | TODO | Theo lộ trình trong AGENTS.md. |
| P08 | TODO | Theo lộ trình trong AGENTS.md. |
| P09 | TODO | Theo lộ trình trong AGENTS.md. |
| P10 | TODO | Theo lộ trình trong AGENTS.md. |
| P11 | TODO | Theo lộ trình trong AGENTS.md. |
| P12 | TODO | Theo lộ trình trong AGENTS.md. |
| P13 | TODO | Theo lộ trình trong AGENTS.md. |
| P14 | TODO | Theo lộ trình trong AGENTS.md. |
| P15 | TODO | Theo lộ trình trong AGENTS.md. |
| P16 | TODO | Theo lộ trình trong AGENTS.md. |
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

- Việc tiếp theo: P05 — UI đăng ký/đăng nhập, đăng xuất và điều hướng theo role/quyền từ `GET /api/auth/me`. JWT đã được chọn ở P04.
- Đọc trước: `AGENTS.md` phần P04, `docs/04-trang-thai-phan-quyen.md` phần quyền, `Account`/`AccountRole` ở V1–V2/V12, module `account` và endpoint đăng ký P03. Không cần nạp lại tài liệu lịch/CLS/thanh toán.
- Kiểm tra: login đúng/sai, tài khoản LOCKED, logout, truy `/me` khi chưa xác thực, thu hồi role/khóa tài khoản có hiệu lực ở request tiếp theo; test DB thử, không dùng dữ liệu bệnh nhân thật.
- Backend khi chạy trong PowerShell mới cần cấu hình JDK 21 và JDBC auth DLL như phần P00. DB chính chưa có admin; khi cần cấp tài khoản nhân viên thì chạy bootstrap theo hướng dẫn P02 với mật khẩu do chủ dự án tự nhập.
- Các điểm mở sau này: phương thức session/token nếu môn học bắt buộc; giờ ca/giá/sức chứa thực tế; quy trình hoàn tiền, MoMo, video và Cloudinary. Chỉ hỏi khi đến việc phụ thuộc.

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

### P05 — kiểm tra lại ứng dụng đang chạy (2026-11-02)

- Đối chiếu Git và màn hình Edge đang mở tại `localhost:5173`: tài khoản `demo_doctor` hiển thị vai trò Bác sĩ và hai mục dự kiến đúng phạm vi; mở hộp xác nhận đăng xuất rồi chọn “Ở lại”, phiên và trang tài khoản được giữ. Không đọc hoặc ghi lại mật khẩu demo trong kết quả kiểm tra.
- `GET /api/health` trực tiếp cổng 8080 và qua Vite proxy cổng 5173 đều trả `UP`. Tại `frontend/`, `npm run lint` và `npm run build` PASS. Tại `backend/`, `.\mvnw.cmd test -q` với JDK 21 và JDBC auth DLL PASS (exit 0); đây là bộ test mặc định, không bao gồm `*IT`.
- Backend đang chạy trỏ DB chính theo cấu hình mặc định. Chưa gửi đăng ký, đăng nhập hoặc đăng xuất thật và chưa tạo dữ liệu thử trên DB chính. Cầu nối ảnh của cửa sổ Edge trả ảnh VS Code dù cây accessibility của Edge đọc được; vì vậy chưa có bằng chứng bố cục tại 320/375/768/1280 px. P05 tiếp tục `IN_PROGRESS`.
- Việc tiếp theo duy nhất: P05 — chạy backend với profile `migrationtest`, kiểm tra đăng ký/đăng nhập/đăng xuất bằng dữ liệu giả và xác minh responsive trên trình duyệt.
