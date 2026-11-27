# Tiến độ triển khai Android — Suncare Clinic

Cập nhật: 2026-11-27  
Việc hiện tại: A13 — Hoàn tất lượt khám trên Android  
Trạng thái: IN_PROGRESS — P23 đã sẵn sàng, đang kiểm tra tích hợp A13

> **Phạm vi sổ tiến độ này**: Theo dõi tiến độ ứng dụng di động Android trong thư mục `android/` theo mã `Axx`.  
> Mọi thay đổi Backend, Database (Flyway) và Web Frontend vẫn quản lý duy nhất tại `docs/implementation/PROGRESS.md` (mã `P00`–`P47`). Không ghi trùng trạng thái hoàn thành của API Backend sang file này.  
> **Quy tắc ghi nhật ký**: Mỗi mốc `Axx` mới được thêm thành một mục riêng phía trên, **giữ nguyên lịch sử các mốc từ `A00` đến hiện tại**, không xóa nhật ký cũ.

## Thông tin nền tảng hiện tại

- **Project**: `Suncare Clinic` (`android/`)
- **Package / Application ID**: `com.example.suncareclinic`
- **SDK**: `minSdk = 26`, `compileSdk = 36.1`, `targetSdk = 36`
- **UI Stack**: Kotlin + Jetpack Compose + Material 3 (`buildFeatures.compose = true`)
- **Networking**: Retrofit `2.11.0` + Gson + OkHttp `4.12.0`, `BuildConfig.API_BASE_URL = "http://127.0.0.1:8080/"` (dùng kèm `D:\Sdk\platform-tools\adb.exe reverse tcp:8080 tcp:8080` để xuyên qua Firewall/VPN trên cả Emulator và máy thật).
- **Backend đích (local dev)**: Spring Boot (`http://127.0.0.1:8080` qua `adb reverse`; dùng profile `migrationtest` khi kiểm thử ghi dữ liệu, tuyệt đối không ghi dữ liệu thử lên DB chính).

## Phạm vi kế hoạch Android (đã chốt loại bỏ các phần chỉ dành cho Web)

- **Giữ trong kế hoạch App**:
  - **Bệnh nhân (`PATIENT`)**: Đăng ký/đăng nhập/hồ sơ (`A02`–`A03`), tìm & đặt lịch trực tiếp (`A04`–`A05`), lịch khám của tôi (`A06`), và về sau khi Backend có: đặt lịch trực tuyến + thanh toán MoMo (`P31`–`P35`), khám video trực tuyến (`P37`), xem EMR/đơn thuốc/kết quả CLS chỉ đọc (`P41`).
  - **Bác sĩ (`DOCTOR`)**: Lịch trực & lịch hẹn được giao (`A07`), khám trực tiếp (`A09`–`A11`), và về sau chỉ bổ sung các thao tác xem nhanh/xác nhận gọn phù hợp di động.
- **Loại bỏ khỏi kế hoạch App (chỉ triển khai trên Web)**:
  - Quản trị hệ thống `SYSTEM_ADMIN` (`P07`–`P12`), xếp & công bố lịch `DEPARTMENT_HEAD` (`P13`–`P14`), quầy thu ngân & hoàn tiền `CASHIER` (`P21`, `P30`, `P42`), vận hành phòng CLS `LAB_STAFF` (`P25`–`P27`, `P40`), báo cáo & xuất CSV (`P43+`).
- **Quy ước UX Mobile (không bê nguyên Web)**:
  - Không dùng bảng nhiều cột dàn ngang; dùng danh sách `SuncareCard` + `ModalBottomSheet` chi tiết.
  - Bottom Navigation tối đa 4–5 tab theo vai trò; không tự dựng trình tạo/in PDF phức tạp trong App.

## Lộ trình công việc Android

| Mã | Việc và điều kiện nghiệm thu | Phụ thuộc Backend | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|---|---|
| A00 | Khởi tạo project `android/` (Kotlin + Compose + Material 3), cài bộ skill Android, kiểm tra build Gradle (`.\gradlew.bat :app:assembleDebug`) và cấu hình kết nối `/api/health`. | P00, P01 | DONE | Đã tạo project, cài skill Android, `:app:assembleDebug` PASS, thiết lập `adb reverse tcp:8080 tcp:8080` và xác nhận `GET /api/health` từ Emulator trả `{"service":"suncare-clinic","status":"UP"}`. |
| A01 | Thiết lập hệ thống giao diện **Sunflower Clinical** (`Color.kt`, `Type.kt`, `Theme.kt`) và bộ component dùng chung (`SuncareButton`, `StatusBadge`, `SuncareCard`, `StateViews`, `SuncareTextField`) đồng bộ với `frontend/src/index.css`. | — | DONE | Đã cài đặt đầy đủ màu/chữ/theme/component; đã xác minh ảnh thực tế trên Emulator và tinh chỉnh tương phản/khoảng cách. |
| A02 | Tầng mạng (`Retrofit` + `OkHttp` + `AuthInterceptor` + `TokenStorage`) và màn hình **Đăng nhập / Đăng ký bệnh nhân** kèm kiểm tra `/api/health`, khôi phục phiên và hộp thoại Đăng xuất. | P03, P04 | DONE | Đã nghiệm thu đăng ký `a08.patient`, đăng nhập, `/api/auth/me` và đăng nhập trực tiếp trên `emulator-5554` qua backend `migrationtest` cổng `8081` (`API kết nối`). |
| A03 | Khung điều hướng **Bottom Navigation Bar** theo vai trò và màn hình **Hồ sơ cá nhân** (chế độ xem chỉ đọc → chỉnh sửa có `version` chống 409, đăng xuất xác nhận). | P06 | DONE | Đã nghiệm thu `GET/PUT /api/accounts/profile` với `version` và xem ảnh thực tế màn `Hồ sơ` trên `emulator-5554` hiển thị đúng dữ liệu vừa cập nhật (`456 Duong A08, TP.HCM`). |
| A02.UI | Làm mới màn Đăng nhập và Đăng ký theo nhận diện Sunflower Clinical. | A02 | DONE | Build debug, 4 unit test và kiểm tra giao diện, lỗi để trống, mất kết nối và thử lại trên `emulator-5554` đạt. |
| A04 | Màn hình Bệnh nhân **Tìm lịch khám trực tiếp** (chọn chuyên khoa, khoảng ngày bằng BottomSheet, ca, tên bác sĩ, xem lịch theo từng bác sĩ). | P15 | DONE | Đã nghiệm thu `GET /api/appointments/onsite/options` & `/availability` trên `SuncareClinicMigrationTest` và xác minh hiển thị trên `emulator-5554`. |
| A05 | Luồng Bệnh nhân **Đặt lịch khám trực tiếp** (giữ bác sĩ hoặc tự phân cân bằng, nhập triệu chứng, gửi `Idempotency-Key`, màn xác nhận & xử lý lỗi 409). | P16 | DONE | Đã nghiệm thu đặt lịch trực tiếp tạo `Appointment` + `Encounter` nguyên tử và thử gửi lại cùng `Idempotency-Key` trả `replayed = true` trên `SuncareClinicMigrationTest`. |
| A06 | Màn hình Bệnh nhân **Lịch khám của tôi** (lọc trạng thái, xem chi tiết lịch hẹn/triệu chứng, không có nút đổi/hủy trực tiếp). | P17 | DONE | Đã nghiệm thu `GET /api/patient/appointments` & `/{id}` và xem ảnh thực tế trên `emulator-5554` hiển thị đúng lịch `APT...`, lọc trạng thái và không có nút đổi/hủy trực tiếp. |
| A07 | Màn hình Bác sĩ **Lịch trực & Danh sách lịch hẹn được giao** (lọc ngày/ca/trạng thái, xem chi tiết bệnh nhân & triệu chứng tự khai). | P17 | DONE | Đã nghiệm thu `GET /api/doctor/appointments` & `/timetable` và xem ảnh thực tế trên `emulator-5554` với tài khoản `a08.doctor`. |
| A08 | Kiểm thử toàn luồng trên Emulator / thiết bị thật với backend `migrationtest` và rà soát giao diện (Compose Preview, kích thước màn hình nhỏ/lớn). | A01–A07 | DONE | Đã bổ sung `mssql-jdbc_auth-13.4.0.x64.dll` chính thức (Microsoft JDBC 13.4.0), tạo & migrate `SuncareClinicMigrationTest` (64 bảng), chạy PASS toàn bộ unit tests + E2E `A02`–`A11` + chụp ảnh kiểm tra giao diện trên `emulator-5554`, sửa lỗi co hẹp `StatusBadge` cạnh mã dài và dọn sạch `0` dữ liệu thử. |
| A09 | Màn hình Bác sĩ **Khám trực tiếp — Danh sách lượt khám, Bắt đầu khám, Tiền sử bệnh & Dị ứng** (tab `Khám bệnh`, xác nhận có mặt/snapshot 1 lần, thêm/sửa tiền sử & dị ứng có `version` chống 409). | P18 | DONE | Đã nghiệm thu worklist, thêm tiền sử/dị ứng, bắt đầu khám chốt snapshot và xem ảnh thực tế màn danh sách & chi tiết lượt khám trên `emulator-5554`. |
| A10 | Màn hình Bác sĩ **Sinh hiệu, Ghi chú lâm sàng, Chẩn đoán ICD-10-VN & Lịch sử khám chỉ đọc** (bên trong chi tiết lượt khám). | P19, P41 | DONE | Đã nghiệm thu ghi sinh hiệu (`Idempotency-Key`), lưu ghi chú (`version`), tra cứu ICD-10-VN (`J00`), lưu chẩn đoán `FINAL` chính và đọc `/history` trên `SuncareClinicMigrationTest`. |
| A11 | Màn hình Bác sĩ **Hóa đơn phí khám trực tiếp** (xem danh sách, tạo nháp, xác nhận phát hành với `Idempotency-Key` & `ROWVERSION`, xử lý 409/503 — khớp điểm dừng Web `P20.b`). | P20 | DONE | Đã nghiệm thu tạo hóa đơn nháp và phát hành `ISSUED` (`300.000 đ`) với `Idempotency-Key` & `version` trên `SuncareClinicMigrationTest` + unit test Gson + sửa layout mã hóa đơn dài; `:app:testDebugUnitTest :app:assembleDebug` PASS (0 lỗi, 0 cảnh báo). |
| A03.UI | Làm mới các màn sau đăng nhập cho Bệnh nhân và Bác sĩ. | A03–A11 | DONE | Build debug, 4 unit test và kiểm tra trực quan hai vai trò trên emulator với API `migrationtest`. |
| A12 | Bác sĩ xem đơn thuốc của lượt khám hiện tại, gồm đơn nháp và đơn đã phát hành. | P22, A09 | DONE | Build, 6 unit test, kiểm tra hiển thị ba trạng thái trên emulator và quyền đọc bằng API thật trên DB V23 riêng. |
| A13 | Bác sĩ xác nhận hoàn tất lượt khám trực tiếp không cận lâm sàng và Android cập nhật trạng thái từ phản hồi API. | P23, A09–A12 | IN_PROGRESS | Đã nối API hoàn tất từ bản P23 mới lấy về, đang kiểm tra trên emulator và DB thử. |

---

## Nhật ký triển khai chi tiết (từ A00 đến hiện tại)

### A13 — Tích hợp API hoàn tất lượt khám sau khi lấy P23 (2026-11-27)

- Commit `2c22976` đã cung cấp `POST /api/doctor/encounters/{encounterId}/complete` với `completionConfirmed: true`. Android xác nhận trước khi gửi, chặn khi còn biểu mẫu chưa lưu, hiển thị lý do Backend từ chối và có thao tác thử lại xác minh khi mất phản hồi.
- Sau khi máy chủ xác nhận, cập nhật trạng thái chỉ đọc và tải lại chi tiết, danh sách phụ trách. Tận dụng cơ chế retry trả cùng kết quả của P23.

### A13 — Xác minh phụ thuộc trước khi lấy P23 (2026-11-27)

- Đã đối chiếu `P23` trong sổ Backend/Web, các mapping của `EncounterController` và quy tắc `TX10`. P23 vẫn `TODO` và chưa có API chuyển lượt khám sang `COMPLETED`.
- Khi P23 sẵn sàng, nối thao tác xác nhận của bác sĩ trong chi tiết lượt khám với endpoint và hợp đồng do Backend cung cấp. Hiển thị lý do bị chặn từ API và tải lại chi tiết, danh sách sau khi hoàn tất thành công.
- Kiểm tra trên DB thử với ca có chẩn đoán `FINAL`, đã thu đủ và không kê đơn, cùng các ca thiếu `FINAL`, còn khoản phải thu, còn đơn `DRAFT` và bác sĩ ngoài phạm vi. Backend phải cập nhật `Encounter` và `Appointment` trong cùng giao dịch. Không bắt buộc có đơn thuốc.
- Lượt này chỉ ghi nhận phụ thuộc và tiêu chí nghiệm thu trong sổ Android. Chưa có mã tích hợp hoặc kết quả kiểm thử A13.

### A12 — Xem đơn thuốc của lượt khám hiện tại (2026-11-27)

- Bác sĩ mở tab `Đơn thuốc` trong chi tiết lượt khám. Android dùng `GET /api/doctor/encounters/{encounterId}/prescriptions`, tải lại khi mở tab và có nút làm mới. Tab hiển thị đơn nháp/đã phát hành, thuốc, liều, cách dùng, tần suất, số ngày, số lượng và lời dặn, với số liệu và đơn vị từ bản ghi đơn.
- Xử lý tải, chưa có đơn, lỗi có thể thử lại, ngoài phạm vi và phiên hết hạn. Khi rời tab, Android bỏ dữ liệu đang hiển thị và hủy yêu cầu đang chạy. Phạm vi A12 là xem đơn trong `android/`.
- Kiểm tra Android bằng `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` trong `android/`. Build thành công, 6 unit test đạt, gồm đọc hợp đồng Gson và định dạng đơn cũ, đơn vị liều và cấp phát riêng.
- Dùng bản sao backend và DB dùng một lần `SuncareClinicAndroidA12Test` ở V23, với tệp thử bên dưới `android/app/build/`. API xác nhận lượt chưa có đơn trả danh sách rỗng, bác sĩ khác nhận HTTP 404 và bệnh nhân nhận HTTP 403. Trên `emulator-5554` đã kiểm tra chưa có đơn, nháp và phát hành trên cùng lượt khám, nút làm mới và giá trị giả `1 mL` / `10,125 chai`. DB thử có sẵn ở V20 thiếu cột đơn vị nên dùng DB riêng để kiểm tra đúng hợp đồng P22.
- Đã cài APK cuối, đăng xuất tài khoản giả và trả `adb reverse` về cổng mặc định 8080. DB V23 dùng riêng còn `0` Account và `0` Prescription trước khi xóa. A12 được nghiệm thu trên emulator với backend thử. Phiên này dùng backend mặc định để kiểm tra lại kết nối `/api/health`.

### A03.UI — Làm mới giao diện sau đăng nhập (2026-11-27)

- Chuẩn hóa tiêu đề vàng kem Sunflower Clinical, thẻ lịch, biểu tượng và bố cục màn rộng. Thanh điều hướng dùng màu vàng cho tab đang chọn. Tổng quan có lối tắt theo vai trò và dùng tên trên hồ sơ.
- Bệnh nhân có bố cục mới ở Hồ sơ xem/sửa, Tìm lịch, chọn ca, tóm tắt/xác nhận đặt lịch, danh sách và chi tiết lịch khám. Bác sĩ có bố cục mới ở Lịch làm việc, lịch hẹn, ca trực, danh sách lượt khám và các phần chi tiết lượt khám. Phạm vi thay đổi là Compose UI và nhật ký Android.
- Chạy `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` trong `android/`, build thành công và 4 unit test đạt. Cài APK lên `emulator-5554`, mở các màn bằng tài khoản giả từ `SuncareClinicMigrationTest`. Đã xem hồ sơ và form chỉnh sửa, lọc/tìm/đến hộp xác nhận đặt lịch, chi tiết lịch hẹn, ca trực, hộp thoại đăng xuất, danh sách lượt khám và các tab tiền sử, sinh hiệu, chẩn đoán, lịch sử, hóa đơn.
- Đã xác minh khi cuộn màn Tìm lịch, nội dung dừng dưới thanh trạng thái và ô tìm bác sĩ nhận nội dung nhập. Lượt này kiểm tra giao diện và điều hướng, chưa lặp lại các giao dịch ghi từ app hoặc kiểm tra trên thiết bị vật lý.
- Đã dọn dữ liệu giả trên `SuncareClinicMigrationTest`, hậu kiểm `0` tài khoản, lịch hẹn và lượt khám. Khôi phục `adb reverse tcp:8080 tcp:8080` và xác nhận app báo đã kết nối với backend mặc định.

### A02.UI — Làm mới màn Đăng nhập và Đăng ký (2026-11-27)

- `AuthScreen.kt` dùng khối nhận diện xanh đậm với biểu tượng hướng dương, công tắc chế độ có vùng chạm 48dp, biểu mẫu giới hạn chiều rộng và thông báo mất kết nối kèm nút thử lại. `AuthViewModel.kt` giữ ba trạng thái kiểm tra, có kết nối và mất kết nối cho màn hình.
- Tại `android/`, chạy `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` thành công, 4 unit test đạt. Cài APK cuối lên `emulator-5554` và xác nhận app hiển thị kết nối.
- Trên emulator, đã xem hai chế độ, cuộn tới nút tạo tài khoản, kiểm tra lỗi đăng nhập khi bỏ trống và thử ngắt rồi phục hồi `adb reverse`. Nút `Thử lại` hiển thị lại `Đã kết nối máy chủ`. Phiên này không gửi đăng ký hoặc đăng nhập thành công và không ghi dữ liệu vào DB.
- Sau phản hồi về nhận diện Web, đổi nền khối đầu trang và tab đang chọn sang vàng kem `#FFF3D6`, viền `#FBE7B5`, giữ vàng hướng dương `#F4B942` cho nút chính và biểu tượng. Build debug và 4 unit test đạt, đã cài APK và xem lại cả hai màn trên `emulator-5554`.
- Thêm nút `Quên mật khẩu?` dưới ô mật khẩu trên màn Đăng nhập. Nút mở hướng dẫn liên hệ phòng khám vì dự án chưa có API đặt lại mật khẩu. Build debug và 4 unit test đạt. Trên `emulator-5554`, đã kiểm tra mở và đóng hộp thoại, chuyển sang Đăng ký và quay lại Đăng nhập có kết nối.

### Nghiệm thu E2E toàn luồng `A02`–`A11` trên `SuncareClinicMigrationTest` & `emulator-5554` (2026-11-26)

- **Chuẩn bị môi trường thử độc lập**:
  - Tải `mssql-jdbc_auth-13.4.0.x64.dll` chính thức từ release `microsoft/mssql-jdbc v13.4.0` vào `D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0\`.
  - Tạo DB thử `SuncareClinicMigrationTest`, chạy backend độc lập trên cổng `8081` với `--spring.profiles.active=migrationtest` (Flyway migrate sạch 64 bảng) cùng cấu hình cơ sở thử `SUNCARE_BILLING_FACILITY_NAME`/`ADDRESS`; nối `emulator-5554` vào cổng `8081` qua `adb reverse tcp:8080 tcp:8081` (không chạm cổng `8080` hay DB khác).
- **Kết quả kiểm chứng E2E thực tế (`PASS`)**:
  - `A02`–`A03`: đăng ký `a08.patient`, đăng nhập, `/api/auth/me`, cập nhật hồ sơ có `version` → `address=456 Duong A08, TP.HCM`.
  - `A04`–`A06`: tìm lịch trực tiếp (phí `300000`), đặt lịch kèm `Idempotency-Key` tạo `APT261126416206BED116` + `ENC2611264905B4721AFC`, gửi lại cùng khóa trả `replayed=True`, xem danh sách & chi tiết lịch Bệnh nhân.
  - `A07`, `A09`–`A11`: đăng nhập `a08.doctor`, xem lịch hẹn & lịch trực công bố, xem worklist, thêm tiền sử & dị ứng, xác nhận có mặt/bắt đầu khám (`IN_PROGRESS`, chốt snapshot), ghi sinh hiệu, lưu ghi chú (`version`), tra cứu ICD-10-VN (`J00`) & lưu chẩn đoán `FINAL` chính, đọc `/history`, lập hóa đơn nháp và phát hành `ISSUED` (`300000`).
- **Kiểm tra trực quan trên `emulator-5554` & sửa lỗi UI**:
  - Chụp và xem trực tiếp 6 màn hình trên `emulator-5554` (`Tìm lịch`, `Lịch khám Bệnh nhân`, `Hồ sơ`, `Lịch khám Bác sĩ`, `Danh sách lượt khám`, `Chi tiết lượt khám`).
  - Phát hiện & sửa lỗi `StatusBadge` bị co hẹp theo chiều dọc cạnh mã dài (`ENC...`/`INV...`) trong `DoctorEncountersScreen.kt` và `EncounterClinicalTabs.kt` bằng `Modifier.weight(1f)` + `Arrangement.spacedBy(8.dp)`.
  - Chạy lại `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` sau khi sửa → **BUILD SUCCESSFUL** (23s, 44 tasks, 0 lỗi, 0 cảnh báo) và cài APK bản chốt cuối lên `emulator-5554`.
- **Dọn dẹp & khôi phục**:
  - Đã dừng backend thử cổng `8081`, dọn sạch toàn bộ dữ liệu thử trên `SuncareClinicMigrationTest` (xác nhận `0` Account, `0` Appointment, `0` Encounter, `0` Invoice), và khôi phục `adb reverse tcp:8080 tcp:8080`.

### A11 — Màn hình Bác sĩ Hóa đơn phí khám trực tiếp (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `data/dto/DoctorEncounterDtos.kt`: bổ sung `InvoiceResponseDto`, `InvoiceLineDto`, `IssueInvoiceRequestDto` khớp đúng hợp đồng API Backend `P20` (`InvoiceResponse` & `IssueInvoiceRequest`).
  - `data/api/SuncareApi.kt` & `data/repository/DoctorEncounterRepository.kt`: nối 3 endpoint `GET /api/doctor/encounters/{encounterId}/invoices`, `POST /api/doctor/encounters/{encounterId}/invoices` (gửi `Idempotency-Key`), và `POST /api/doctor/encounters/{encounterId}/invoices/{invoiceId}/issue` (gửi `Idempotency-Key` + `{ "version": "<base64>" }`).
  - `ui/screens/encounter/EncounterClinicalViewModel.kt`: tải lười danh sách hóa đơn khi mở tab `Hóa đơn`, bảo lưu `Idempotency-Key` theo chữ ký thao tác (`create` / `issue:{id}:{version}`) qua lỗi mạng/5xx để thử lại an toàn không tạo trùng hóa đơn, chặn bấm trùng đồng thời, theo dõi checkbox xác nhận phát hành, kiểm tra đã có hóa đơn phí khám chưa hủy (`hasActiveExaminationInvoice`), xử lý 409 (cần tải lại) và 503 (`BILLING_FACILITY_NOT_CONFIGURED`).
  - `ui/screens/encounter/EncounterClinicalTabs.kt` & `DoctorEncountersScreen.kt`: thêm tab thứ 5 **`Hóa đơn`** trong chi tiết lượt khám Bác sĩ (`EncounterInvoicesSection` + `InvoiceCard`) hiển thị snapshot bệnh nhân/cơ sở, thời điểm phát hành `Asia/Ho_Chi_Minh`, từng dòng thu và tổng tiền định dạng `BigDecimal`, checkbox xác nhận trước khi phát hành `DRAFT`, và thông báo chỉ đọc rõ ràng trên bản `ISSUED` (phát hành chưa đồng nghĩa đã thanh toán).
  - `app/src/test/java/com/example/suncareclinic/SuncareAndroidUnitTest.kt`: bổ sung test giải mã Gson cho `InvoiceResponseDto` & `InvoiceLineDto`.
- **Kiểm tra**:
  - Lệnh: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (1m, 44 tasks, 0 lỗi, 0 cảnh báo).
  - Cài APK lên `emulator-5554` (`adb install -r`) và khởi chạy thành công. Không chỉnh sửa `backend/`, `frontend/` hay DB.
- **Chưa kiểm tra**: Chưa đăng nhập tài khoản `DOCTOR` trên `migrationtest` để bấm thử tạo nháp/phát hành/409/503 hóa đơn (chờ khôi phục `mssql-jdbc_auth-13.4.0.x64.dll` cho DB thử).

### A10 — Màn hình Bác sĩ Sinh hiệu, Ghi chú lâm sàng, Chẩn đoán ICD-10-VN & Lịch sử khám chỉ đọc (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `data/dto/DoctorEncounterDtos.kt`: bổ sung toàn bộ DTO `P19` (`EncounterClinicalResponseDto`, `EncounterNotesDto`, `VitalSignDto`, `DiagnosisItemDto`, `VitalSignRequestDto`, `EncounterNotesRequestDto`, `DiagnosisRequestDto`, `DiagnosisConceptDto`, `DiagnosisConceptSearchResponseDto`) và `P41` phần bác sĩ (`PreviousEncounterSummaryDto`, `PreviousEncounterPageDto`, `PreviousEncounterDetailDto`, đơn thuốc đã phát hành & kết quả CLS đã công bố).
  - `data/api/SuncareApi.kt` & `data/repository/DoctorEncounterRepository.kt`: nối đủ 8 API `/api/doctor/encounters/{id}/clinical`, `/clinical/vitals` (gửi `Idempotency-Key`), `/clinical/notes` (gửi `version`), `/clinical/diagnoses` (`POST`/`PUT` gửi `Idempotency-Key` & `version`), `/api/doctor/diagnosis-concepts/search`, `/api/doctor/encounters/{contextId}/history` và `/{previousId}`.
  - `ui/screens/encounter/EncounterClinicalViewModel.kt` & `EncounterClinicalTabs.kt`:
    - **Sinh hiệu & Ghi chú**: danh sách sinh hiệu theo giờ `Asia/Ho_Chi_Minh`, `ModalBottomSheet` ghi sinh hiệu giữ `Idempotency-Key` ổn định khi retry mạng/5xx; form ghi chú lâm sàng 5 mục giữ nguyên dữ liệu chưa lưu khi đổi tab, xử lý xung đột 409 kèm nút tải lại phiên bản mới nhất.
    - **Chẩn đoán ICD-10-VN**: tra cứu danh mục `/api/doctor/diagnosis-concepts/search` (debounce 300ms, hủy request cũ, lọc nhóm mã, phân trang), tự điền tên bệnh chuẩn nhưng giữ phần diễn giải bác sĩ đã sửa, bắt buộc chọn mã ICD khi chọn `FINAL`, và tự khóa công tắc `Đặt làm chẩn đoán chính` khi mã có `primaryAllowed = false`.
    - **Lịch sử khám chỉ đọc**: danh sách các lượt khám `COMPLETED` trước của bệnh nhân có phân trang, chạm mở `ModalBottomSheet` chỉ đọc hiển thị chẩn đoán, ghi chú, đơn thuốc `ISSUED` và kết quả CLS `PUBLISHED`.
  - `ui/screens/encounter/DoctorEncountersScreen.kt` & `SignedInShell.kt`: bổ sung thanh 4 tab trong chi tiết lượt khám (`Tiền sử & Dị ứng`, `Sinh hiệu & Ghi chú`, `Chẩn đoán`, `Lịch sử khám`), tải lười dữ liệu theo từng tab.
- **Kiểm tra**:
  - Lệnh: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (42s, 44 tasks, 0 lỗi, 0 cảnh báo).
  - Cài APK lên `emulator-5554` (`adb install -r`) và khởi chạy thành công. Không chỉnh sửa `backend/`, `frontend/` hay DB.
- **Chưa kiểm tra**: Chưa đăng nhập tài khoản `DOCTOR` trên `migrationtest` để bấm thử ghi sinh hiệu, lưu ghi chú, tra cứu ICD-10-VN và mở lịch sử khám cũ.

### A09 — Màn hình Bác sĩ Khám trực tiếp: Worklist, Bắt đầu khám, Tiền sử bệnh & Dị ứng (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `data/dto/DoctorEncounterDtos.kt`: thêm toàn bộ DTO `P18` (`EncounterWorklistDto`, `EncounterWorkItemDto`, `EncounterStartRequestDto`/`ResponseDto`, `EncounterHistoryResponseDto`, `PatientHistoryItemDto`, `PatientAllergyItemDto`, `EncounterSnapshotDto`, `AllergenOptionDto`, `PatientHistoryRequestDto`, `PatientAllergyRequestDto`).
  - `data/api/SuncareApi.kt` & `data/repository/DoctorEncounterRepository.kt`: nối 7 API `P18` (`GET /api/doctor/encounters`, `GET /{id}`, `POST /{id}/start`, `POST/PUT /{id}/histories`, `POST/PUT /{id}/allergies`), tự xóa token khi gặp 401 và parse `ApiError`.
  - `ui/screens/encounter/DoctorEncountersViewModel.kt` & `DoctorEncountersScreen.kt`:
    - **Danh sách lượt khám phụ trách**: chọn ngày (`‹ Trước` / `Hôm nay` / `Sau ›` theo `Asia/Ho_Chi_Minh`), tìm kiếm theo tên/mã BN/mã lượt khám (debounce 300ms), lọc trạng thái (`Tất cả`, `Chờ khám`, `Đang khám`, `Chờ CLS`, `Hoàn tất`, `Đã hủy`), phân trang `Trang trước`/`Trang sau`.
    - **Chi tiết lượt khám**: nút `Xác nhận có mặt & Bắt đầu khám` (chỉ hiện khi `PENDING`) có hộp thoại xác nhận (`presenceConfirmed = true`), hiển thị thẻ **Snapshot bất biến tại thời điểm bắt đầu khám** (nếu đã bắt đầu), danh sách **Tiền sử bệnh** (`PERSONAL`/`FAMILY`/`SURGERY`) và **Dị ứng** (chọn dị nguyên danh mục `allergenOptions` hoặc tự ghi `otherNote`, phản ứng, mức độ `UNKNOWN`/`MILD`/`MODERATE`/`SEVERE`) kèm `ModalBottomSheet` thêm/sửa gửi `version` (`ROWVERSION`) và nút tải lại khi gặp xung đột 409.
  - `ui/navigation/SignedInShell.kt` & `MainActivity.kt`: thêm tab **`Khám bệnh`** (`AppTab.Encounters`) trên thanh điều hướng dưới cùng dành riêng cho vai trò `DOCTOR` (tổng 4 tab: `Tổng quan`, `Lịch khám`, `Khám bệnh`, `Hồ sơ`).
- **Kiểm tra**:
  - Lệnh: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (25s, 44 tasks, 0 lỗi, 0 cảnh báo).
  - Cài APK lên `emulator-5554` (`adb install -r`) và khởi chạy thành công. Không chỉnh sửa `backend/`, `frontend/` hay DB.
- **Chưa kiểm tra**: Chưa đăng nhập tài khoản `DOCTOR` trên `migrationtest` để bấm thử bắt đầu khám và lưu tiền sử/dị ứng (vẫn đang chờ khôi phục file `mssql-jdbc_auth-13.4.0.x64.dll` cho DB thử).

### A08 — Kiểm thử toàn luồng & rà soát chất lượng Android (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `app/src/test/java/com/example/suncareclinic/SuncareAndroidUnitTest.kt`: bổ sung bộ JUnit 4 kiểm tra lọc trạng thái lịch khám Bệnh nhân (`PatientAppointmentsUiState`), lọc trạng thái + hình thức khám Bác sĩ (`DoctorAppointmentsUiState`), và giải mã Gson khớp đúng 7 hợp đồng JSON Backend (`AppointmentScheduleDetailDto`, `DoctorWorkShiftDto`, `DoctorOnlineSlotDto`, `DoctorSlotPatientDto`, `OnsiteAppointmentBookingResponseDto`, `PersonalProfileResponseDto`, `ApiErrorDto`).
  - `ui/screens/appointment/PatientAppointmentsScreen.kt`: thay `Locale("vi", "VN")` đã deprecated sang `Locale.forLanguageTag("vi-VN")`, đưa toàn bộ bản build về **0 cảnh báo**.
- **Kiểm tra đã đạt**:
  - Lệnh: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (27s, 44 tasks, 0 lỗi, 0 cảnh báo).
  - Cài APK lên `emulator-5554`, khôi phục `adb reverse tcp:8080 tcp:8080`, chụp ảnh thực tế trên Emulator: ứng dụng mở sạch, badge xanh **`API kết nối`** hiển thị đúng, bố cục Sunflower Clinical cân đối, không tràn khung.
- **Vấn đề môi trường còn lại (chưa chốt `DONE`)**:
  - Khi chạy backend độc lập với profile `migrationtest` trên cổng `8081` (`--spring.profiles.active=migrationtest --server.port=8081`) để tránh ghi dữ liệu thử lên DB chính (`SuncareClinic` ở cổng `8080`), `/api/health` trả `UP` nhưng yêu cầu truy cập DB (`POST /api/accounts/register`) gặp `UnsatisfiedLinkError: Unable to load authentication DLL mssql-jdbc_auth-13.4.0.x64` vì file `D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0\mssql-jdbc_auth-13.4.0.x64.dll` hiện không còn tồn tại trên máy.
  - Đã dừng tiến trình thử cổng `8081` và khôi phục `adb reverse tcp:8080 tcp:8080` về trạng thái ban đầu; **không** ghi dữ liệu thử lên DB chính `SuncareClinic`.
- **Cách tiếp tục để chốt `DONE` cho `A02`–`A08`**:
  - Đặt lại file `mssql-jdbc_auth-13.4.0.x64.dll` (của Microsoft JDBC Driver 13.4.0 for SQL Server) vào thư mục `D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0\` (hoặc cấu hình SQL login trong `application-migrationtest.properties`), sau đó chạy lại kiểm thử toàn luồng đăng ký/đăng nhập/hồ sơ/tìm & đặt lịch/xem lịch bệnh nhân/bác sĩ trên `SuncareClinicMigrationTest`.

### A07 — Màn hình Bác sĩ Lịch trực & Danh sách lịch hẹn được giao (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `data/dto/AppointmentScheduleDtos.kt`: bổ sung `DoctorWorkShiftDto`, `DoctorOnlineSlotDto`, `DoctorSlotPatientDto` khớp đúng hợp đồng API Backend `P17` (`DoctorWorkShiftResponse`, `DoctorOnlineSlotResponse`, `DoctorSlotPatientResponse`).
  - `data/api/SuncareApi.kt` & `data/repository/AppointmentScheduleRepository.kt`: nối 5 endpoint Bác sĩ: `GET /api/doctor/appointments`, `GET /api/doctor/appointments/{appointmentId}`, `GET /api/doctor/appointments/timetable?from=&to=`, `GET /api/doctor/appointments/timetable/{assignmentId}/slots`, và `GET /api/doctor/appointments/timetable/slots/{slotId}/patient`; tự xóa token khi nhận HTTP 401.
  - `ui/screens/appointment/DoctorAppointmentsViewModel.kt`: quản lý 2 phần (`Lịch hẹn được giao` và `Lịch trực công bố`), lọc trạng thái + hình thức khám (`Trực tiếp` / `Trực tuyến`), chọn khoảng ngày công bố (`Hôm nay`, `7 ngày tới`, `14 ngày tới`, `30 ngày tới` trong giới hạn 31 ngày của Backend theo múi giờ `Asia/Ho_Chi_Minh`), tải lười danh sách slot 20 phút cho ca trực tuyến, tải chi tiết lịch hẹn và thông tin bệnh nhân của slot.
  - `ui/screens/appointment/DoctorAppointmentsScreen.kt`: giao diện Sunflower Clinical với bộ chuyển 2 chế độ, thẻ lịch hẹn làm nổi bật tên bệnh nhân/chuyên khoa/giờ hẹn `Asia/Ho_Chi_Minh`, `ModalBottomSheet` chi tiết bệnh nhân + phí khám `BigDecimal` + triệu chứng tự khai, thẻ ca trực công bố hỗ trợ mở rộng danh sách slot 20 phút và xem nhanh thông tin bệnh nhân của slot đã đặt.
  - `ui/navigation/SignedInShell.kt`: nối tab **`Lịch khám`** của Bác sĩ vào `DoctorAppointmentsScreen`, hoàn tất thay thế toàn bộ màn placeholder của giai đoạn `A00`–`A07`.
- **Kiểm tra**:
  - Lệnh: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (28s, 38 tasks, 0 lỗi, 0 cảnh báo). Không thay đổi nghiệp vụ `backend/`, `frontend/` hay DB.
- **Chưa kiểm tra**: Chưa đăng nhập tài khoản `DOCTOR` trên Emulator để bấm thử danh sách lịch hẹn được giao, lịch trực công bố, slot 20 phút và chi tiết bệnh nhân với backend `migrationtest`.

### A06 — Màn hình Bệnh nhân Lịch khám của tôi (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `data/dto/AppointmentScheduleDtos.kt`: thêm `AppointmentScheduleListItemDto` và `AppointmentScheduleDetailDto` khớp đúng hợp đồng API Backend `P17` hiện có (`AppointmentScheduleListItemResponse` & `AppointmentScheduleDetailResponse`).
  - `data/api/SuncareApi.kt` & `data/repository/AppointmentScheduleRepository.kt`: nối `GET /api/patient/appointments` và `GET /api/patient/appointments/{appointmentId}`, tự xóa token khi nhận HTTP 401 và parse `ApiError`.
  - `ui/screens/appointment/PatientAppointmentsViewModel.kt`: tải danh sách lịch khám của bệnh nhân đang đăng nhập, hỗ trợ lọc trạng thái (`Tất cả`, `Đã đặt`, `Hoàn tất`, `Đã hủy`, `Vắng mặt`), quản lý mở/tải/thử lại/đóng chi tiết lịch hẹn theo `appointmentId`.
  - `ui/screens/appointment/PatientAppointmentsScreen.kt`: danh sách thẻ lịch khám theo bảng màu Sunflower Clinical kèm chip lọc trạng thái có số đếm, trạng thái tải/rỗng/lỗi/thành công, `ModalBottomSheet` chi tiết hiển thị đầy đủ mã lịch, trạng thái Appointment + Encounter, giờ khám chuyển đổi sang múi giờ `Asia/Ho_Chi_Minh`, bác sĩ, khoa/chuyên khoa, phòng khám, phí khám định dạng `BigDecimal` và triệu chứng tự khai. Tuân thủ quy tắc nghiệp vụ: **không hiển thị nút đổi hoặc hủy** cho lịch khám trực tiếp (`ONSITE`), thay bằng thông báo hướng dẫn rõ ràng.
  - `ui/navigation/SignedInShell.kt` & `MainActivity.kt`: nối tab **`Lịch khám`** của Bệnh nhân vào `PatientAppointmentsScreen` (khi danh sách rỗng có nút chuyển nhanh sang tab `Tìm lịch`).
- **Kiểm tra**:
  - Lệnh: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (29s, 38 tasks, 0 lỗi). Không thay đổi nghiệp vụ `backend/`, `frontend/` hay DB.
- **Chưa kiểm tra**: Chưa đăng nhập tài khoản `PATIENT` trên Emulator để bấm thử danh sách lịch, lọc trạng thái và mở chi tiết lịch hẹn với backend `migrationtest`.

### A05 — Luồng Bệnh nhân Đặt lịch khám trực tiếp (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `data/dto/OnsiteAppointmentDtos.kt`, `data/api/SuncareApi.kt`, `data/repository/OnsiteAppointmentRepository.kt`: thêm `OnsiteAppointmentBookingRequestDto`, `OnsiteAppointmentBookingResponseDto` và `POST /api/appointments/onsite/bookings` gửi kèm header `Idempotency-Key`.
  - `ui/screens/appointment/OnsiteSearchViewModel.kt`: bổ sung `DoctorAssignmentMode` (`SelectedDoctor` / `AutoAssign`), quản lý `symptoms` (validate ≤ 2.000 ký tự), khóa chế độ tự phân khi đang lọc theo tên bác sĩ, và sinh/giữ `Idempotency-Key` ổn định theo chữ ký payload (`specialtyId|date|shiftId|doctorId|symptoms`) để thử gửi lại an toàn khi gặp lỗi mạng/5xx; reset khóa khi đổi ca/payload hoặc nhận lỗi 409/400.
  - `ui/screens/appointment/OnsiteSearchScreen.kt` & `SignedInShell.kt`: nâng cấp thẻ buổi đang chọn thành `BookingDraftCard` với 2 thẻ chọn chế độ bác sĩ, ô nhập triệu chứng kèm bộ đếm ký tự, hộp thoại xác nhận `AlertDialog`, nút `Thử gửi lại (chống đặt trùng)` khi lỗi mạng/5xx, nút `Tải lại lịch còn chỗ` khi lỗi 409, và thẻ `BookingConfirmationCard` hiển thị đầy đủ mã `Appointment`, mã `Encounter`, bác sĩ/khoa/phòng thực tế được phân và phí khám đã chốt (`BigDecimal`).
- **Kiểm tra**:
  - Lệnh: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (28s, 38 tasks, 0 lỗi). Không chỉnh sửa `backend/`, `frontend/` hay DB.
  - Bước cài đặt lên Emulator báo `adb.exe: device offline` (chưa có Emulator đang mở).
- **Chưa kiểm tra**: Chưa chạy thử luồng đặt lịch trực tiếp (giữ bác sĩ & tự phân cân bằng, retry, lỗi 409) trên Emulator với backend `migrationtest`.

### A04 — Màn hình Bệnh nhân Tìm lịch khám trực tiếp (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `data/dto/OnsiteAppointmentDtos.kt`, `data/api/SuncareApi.kt`, `data/repository/OnsiteAppointmentRepository.kt`: nối `GET /api/appointments/onsite/options` và `GET /api/appointments/onsite/availability` (lọc tùy chọn theo `specialtyId`, `dateFrom`, `dateTo`, `shiftId`, `doctorName`), xử lý HTTP 401/403/400 và lỗi mạng.
  - `ui/screens/appointment/OnsiteSearchViewModel.kt`: tự tải chuyên khoa và ca khám, validate khoảng ngày theo múi giờ `Asia/Saigon` (`>= today`, `dateFrom <= dateTo`), hủy job search cũ khi đổi bộ lọc (debounce 300ms cho tìm tên bác sĩ), nhóm kết quả theo từng bác sĩ → ngày → ca sáng/chiều, quản lý mở rộng bác sĩ và chọn/bỏ chọn buổi khám.
  - `ui/screens/appointment/OnsiteSearchScreen.kt`: nhánh Trực tiếp đang mở (Trực tuyến hiển thị khóa `Sắp mở` vì thuộc P31–P34), bộ lọc chuyên khoa (chip cuộn ngang), `ModalBottomSheet` chọn khoảng ngày có 3 preset (`Ngày mai`, `7 ngày tới`, `30 ngày tới`) + nhập ngày tùy chỉnh, lọc ca, tìm tên bác sĩ, thẻ bác sĩ mở rộng từng ngày/ca với định dạng tiền tệ VND bằng `BigDecimal`, và thẻ tóm tắt buổi đang chọn sẵn sàng nối sang bước đặt lịch `A05`.
  - `ui/navigation/SignedInShell.kt` & `MainActivity.kt`: nối tab **`Tìm lịch`** của Bệnh nhân vào `OnsiteSearchScreen` thay cho placeholder.
- **Kiểm tra**:
  - Lệnh: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (34s, 38 tasks, 0 lỗi).
  - Đã cài APK (`adb install -r`) và khởi chạy lên `emulator-5554`. Không chỉnh sửa `backend/`, `frontend/` hay DB.
- **Chưa kiểm tra**: Chưa đăng nhập tài khoản `PATIENT` trên Emulator để bấm thử bộ lọc khoảng ngày, mở rộng bác sĩ và xem kết quả thật từ backend `migrationtest`.

### A03 — Khung điều hướng Bottom Navigation Bar & màn hình Hồ sơ cá nhân (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `data/dto/ProfileDtos.kt`, `data/api/SuncareApi.kt`, `data/repository/ProfileRepository.kt`: nối `GET/PUT /api/accounts/profile`, gửi kèm `version` (`ROWVERSION` Base64) chống ghi đè 409, không gửi 3 trường bệnh nhân (`identityNumber`, `emergencyName`, `emergencyPhone`) khi tài khoản là `STAFF`, tự xóa token khi nhận HTTP 401.
  - `ui/screens/profile/ProfileViewModel.kt` & `ui/screens/profile/ProfileScreen.kt`: mặc định mở ở **chế độ xem chỉ đọc** (hiện thông tin bệnh nhân/khẩn cấp cho `PATIENT` hoặc thông tin chuyên môn chỉ đọc cho `STAFF`), bấm `Chỉnh sửa hồ sơ` mở form sửa có validate khớp `PersonalProfileRequest`, `imePadding()`, nút `Tải lại phiên bản mới nhất` khi gặp 409 và nút `Đăng xuất`.
  - `ui/navigation/SignedInShell.kt` & `MainActivity.kt`: Bottom Navigation Bar hiển thị tab theo đúng vai trò (`Tổng quan`, `Tìm lịch` cho `PATIENT`, `Lịch khám` cho `PATIENT`/`DOCTOR`, `Hồ sơ`) kèm hộp thoại xác nhận Đăng xuất (`AlertDialog`).
- **Kiểm tra**:
  - Build: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'; .\gradlew.bat :app:assembleDebug --offline --no-daemon --console=plain` tại `android/` → **BUILD SUCCESSFUL** (28–29s, 38 tasks, 0 lỗi).
  - Cài đặt và khởi chạy lên `emulator-5554` (`adb install -r` + `am start`) thành công. Không chỉnh sửa `backend/`, `frontend/` hay DB.
- **Chưa kiểm tra**: Chưa đăng nhập để bấm thử luồng xem/sửa hồ sơ Patient & Staff, lỗi xung đột 409 và chuyển tab với backend `migrationtest`.

### A02 — Tầng mạng Retrofit/OkHttp, Đăng nhập / Đăng ký & kết nối Emulator (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `android/gradle/libs.versions.toml` & `android/app/build.gradle.kts`: thêm `retrofit 2.11.0`, `converter-gson`, `okhttp 4.12.0`, `lifecycle-viewmodel-compose`, `material-icons-extended`; bật `buildConfig = true`.
  - `android/app/src/main/AndroidManifest.xml`: thêm quyền `INTERNET` và `usesCleartextTraffic="true"` cho local dev.
  - `data/dto/AuthDtos.kt`, `data/local/TokenStorage.kt`, `data/api/SuncareApi.kt`, `data/api/ApiClient.kt`, `data/repository/AuthRepository.kt`: gọi `/api/health`, `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`, `/api/accounts/register`; tự gắn header `Authorization: Bearer <token>` và `Cache-Control: no-store`; parse `ApiError` + `fieldErrors`; tự xóa token khi nhận HTTP 401.
  - `ui/components/SuncareTextField.kt`: ô nhập liệu chuẩn Sunflower Clinical (nhãn cố định, báo lỗi inline, ẩn/hiện mật khẩu).
  - `ui/screens/auth/AuthViewModel.kt`, `AuthScreen.kt`, `SignedInHomeScreen.kt`, `MainActivity.kt`: màn hình Đăng nhập / Đăng ký bệnh nhân (3 nhóm thông tin, chọn giới tính bằng `FilterChip`, validate khớp `PatientRegistrationRequest`, điền sẵn tài khoản sau khi đăng ký thành công, `imePadding()`).
- **Tinh chỉnh UI sau khi xem ảnh thực tế trên Emulator**:
  - Đổi màu chữ nút `Thử lại` sang `SuncareTheme.colors.brandText` (`#765000`) để tăng tương phản trên nền vàng kem.
  - Chuyển banner mất kết nối backend sang tông `BadgeTone.Danger` để tách màu khỏi khối header vàng kem.
  - Thu gọn khoảng cách dọc (`12.dp`) và padding header (`14.dp`) để form Đăng nhập gọn đẹp trong 1 khung hình.
- **Xử lý kết nối Emulator**:
  - Chẩn đoán trực tiếp trên `emulator-5554`: `10.0.2.2:8080` bị `Connection timed out` (5s) do tường lửa/mạng ảo Windows dù `127.0.0.1:8080/api/health` trên Windows trả `UP`.
  - Đổi `BuildConfig.API_BASE_URL` sang `http://127.0.0.1:8080/` và chạy `D:\Sdk\platform-tools\adb.exe reverse tcp:8080 tcp:8080` → xác nhận `curl http://127.0.0.1:8080/api/health` từ Emulator trả `{"service":"suncare-clinic","status":"UP"}`.
- **Kiểm tra**:
  - `:app:assembleDebug` **BUILD SUCCESSFUL** (1m 23s lần tải thư viện đầu; 28s lần build lại), đã cài đặt và mở lên `emulator-5554`.
- **Chưa kiểm tra**: Chưa chạy thử thao tác đăng ký/đăng nhập/đăng xuất với backend `migrationtest`.

### A01 — Hệ thống giao diện Sunflower Clinical & bộ component dùng chung (2026-11-26)

- **Phạm vi và file đã thêm/sửa**:
  - `ui/theme/Color.kt`: khai báo bảng màu **Sunflower Clinical** (`Suncare50`–`Suncare900`, `ClinicalCanvas`, `ClinicalSurface`, `ClinicalInk`, `ClinicalMuted`, `ClinicalBorder`, `ClinicalLeaf`, `ClinicalSuccess`, `ClinicalWarning`, `ClinicalDanger`...) khớp `frontend/src/index.css`.
  - `ui/theme/Type.kt`: hệ thống Typography 1 font family (`headlineMedium`, `titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`, `labelLarge`, `labelMedium`).
  - `ui/theme/Theme.kt`: `SuncareClinicTheme` + `SuncareTheme.colors` (`@Immutable` qua `CompositionLocal`), tắt `dynamicColor` để giữ nhận diện Sunflower Clinical trên Android 12+.
  - `ui/components/SuncareButton.kt`, `StatusBadge.kt`, `SuncareCard.kt`, `StateViews.kt`: nút `Primary`/`Outline`/`Danger` (`minHeight = 48.dp`, có `loading`), badge 4 tone kèm chấm màu + chữ tiếng Việt, thẻ bo góc `16.dp` + `highlighted`, và bộ trạng thái `SuncareLoadingView`, `SuncareEmptyView`, `SuncareErrorView`, `SuncareInlineBanner`.
  - `MainActivity.kt`: bật `enableEdgeToEdge` với `SystemBarStyle.light`.
- **Kiểm tra**:
  - `:app:assembleDebug` **BUILD SUCCESSFUL** (34s, 36 tasks, 0 lỗi); đã xem ảnh thực tế trên Emulator và xác nhận đúng bảng màu, không tràn ngang, status bar hiển thị rõ.

### A00 — Khởi tạo project Android, bộ skill & quy ước riêng (2026-11-26)

- **Phạm vi và thay đổi**:
  - Khởi tạo project Android bằng Android Studio tại `android/` (Kotlin DSL, Jetpack Compose, Material 3, `com.example.suncareclinic`, `minSdk = 26`, `compileSdk = 36.1`, `targetSdk = 36`).
  - Tạo sổ tiến độ riêng `android/ANDROID_PROGRESS.md`, file quy tắc cục bộ `android/AGENTS.md` và cập nhật liên kết trong `AGENTS.md` gốc (không ảnh hưởng `backend/`, `frontend/` hay `docs/implementation/PROGRESS.md`).
  - Cài đặt các bộ skill hỗ trợ Android vào `.agents/skills/`: `mobile-app-ui-design` (khóa phạm vi riêng cho `android/`), `ui-ux-pro-max`, và 5 skill chính chủ từ `android/skills` (`adaptive`, `edge-to-edge`, `jetpack-compose-performance`, `navigation-3`, `testing-setup`).
  - Sửa tương thích thư viện trong `android/gradle/libs.versions.toml`: hạ `coreKtx` (`1.19.0` → `1.16.0`) và `lifecycleRuntimeKtx` (`2.11.0` → `2.9.0`) để biên dịch sạch trên `compileSdk = 36.1`.
- **Kiểm tra**:
  - `:app:assembleDebug` PASS; `adb reverse tcp:8080 tcp:8080` + `GET /api/health` từ `emulator-5554` trả `{"service":"suncare-clinic","status":"UP"}`.

---

## Bàn giao phiên sau

- **Trạng thái Android**: `A00`–`A12` đã đạt `DONE`. Bác sĩ có thể xem đơn thuốc của lượt khám hiện tại qua `P22`.
- **Việc tiếp theo trên Android**: `A13` — Nối API `P23` khi hợp đồng hoàn tất lượt khám sẵn sàng. Kiểm tra quyền, điều kiện chẩn đoán, khoản phải thu và đơn nháp theo phản hồi Backend.
- **Đọc trước**: `docs/implementation/PROGRESS.md` (khi làm Backend/Web) hoặc `android/ANDROID_PROGRESS.md` (khi làm tiếp Android).
- **Lưu ý kết nối**: Khi mở Emulator mới, chạy lại `D:\Sdk\platform-tools\adb.exe reverse tcp:8080 tcp:8080`.
