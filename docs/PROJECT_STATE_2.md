# SUNCARE CLINIC — PROJECT STATE

> Tài liệu trạng thái hiện hành để bàn giao giữa các cuộc trò chuyện. Cập nhật: 2026-11-02. Nội dung dựa trên kết quả người phát triển cung cấp trong cuộc trò chuyện về SQL Server và Flyway; phân biệt rõ xác nhận của người phát triển với kết quả kiểm thử có log.

## 1. Thông tin chung

- Dự án: **Suncare Clinic Management Website** (một cơ sở khám ngoại trú).
- Thư mục gốc: `D:\TaiLieu\Nam4Ky1\Project_1\suncare-clinic`.
- Giai đoạn 00 — Khởi tạo: **Hoàn thành**.
- Giai đoạn 01 — SQL Server, schema và migration: **Hoàn thành triển khai theo xác nhận của người phát triển**.
- Giai đoạn tiếp theo: **02 — Backend API: Patient, Account, Staff và phân quyền**.
- Môi trường hiện tại: Windows, phát triển cục bộ; chưa triển khai production.

## 2. Công nghệ

| Thành phần | Công nghệ / phiên bản đã xác định |
|---|---|
| Backend | Spring Boot 4.1.2, Java 21, Maven Wrapper |
| Frontend | React JavaScript, Vite, Tailwind CSS v4, Font Awesome |
| Cơ sở dữ liệu | Microsoft SQL Server 2025, phiên bản `17.0.1000.7` |
| Edition SQL Server | Enterprise Evaluation Edition (64-bit) |
| Kết nối | Microsoft JDBC Driver `13.4.0.jre11`, Windows Authentication |
| Migration | Flyway, 11 migration V1–V11 |
| Công cụ | SSMS, VS Code / IntelliJ IDEA, PowerShell |

Chưa tích hợp Cloudinary, MoMo hay Jitsi vào các luồng nghiệp vụ.

## 3. Giai đoạn 00 — Khởi tạo

- Đã khởi tạo Spring Boot Backend và React/Vite Frontend.
- Đã cấu hình Tailwind CSS v4, Font Awesome và proxy `/api` trong môi trường phát triển.
- Đã có `GET /api/health`, trả trạng thái `UP` trong các lần kiểm tra được người phát triển xác nhận.
- Các module nghiệp vụ chủ yếu mới ở mức cấu trúc; chưa triển khai chức năng quản lý bệnh nhân, đăng nhập, đặt lịch, CLS hoặc thanh toán.

## 4. SQL Server và database

- Máy chủ: `DESKTOP-4FOOJD8`; instance mặc định `MSSQLSERVER`.
- TCP/IP: Enabled; Listen All: Yes; cổng tĩnh `1433`; cổng động bỏ trống.
- Xác thực: **Windows Authentication Only**.
- Collation đã quan sát: `SQL_Latin1_General_CP1_CI_AS`; compatibility level `170`.
- Database chính: **`SuncareClinic`**.
- Database thử migration: **`SuncareClinicMigrationTest`**.
- SSMS đã kết nối TCP thành công đến `SuncareClinic` qua cổng 1433.

## 5. Schema và Flyway

- Thư mục migration: `backend/src/main/resources/db/migration`.
- Có 11 file Flyway từ V1 đến V11, được kiểm tra tĩnh: **58 câu lệnh CREATE TABLE**.
- Trên `SuncareClinicMigrationTest`: **58 bảng nghiệp vụ**, `flyway_schema_history` ghi **11 thành công / 0 thất bại**.
- Người phát triển xác nhận đã chạy V1–V11 trên database chính ở Bước 25. **Chưa nhận log đối chiếu sau triển khai trên database chính trong cuộc trò chuyện**, nên không gán cho đó kết quả truy vấn độc lập.
- **Không chỉnh sửa V1–V11 sau khi đã áp dụng**. Nếu cần thay đổi lược đồ, tạo migration kế tiếp (V12 trở đi) sau khi thiết kế và rà soát.

## 6. Bằng chứng kiểm tra trên database thử

- 58 bảng; 58 Primary Key; 120 Foreign Key; 104 CHECK Constraint.
- 42 UNIQUE Constraint; 13 UNIQUE Index riêng; 13 Filtered Index.
- Không có bảng thiếu Primary Key; không phát hiện FK/CHECK bị disabled hoặc not trusted.
- 16 ràng buộc trọng yếu được kiểm tra tên và thuộc tính, đều `OK`.
- Kiểm thử DML có rollback: CHECK loại dịch vụ/result, CHECK thời gian ca, UNIQUE mã ca, FK tham chiếu Specialty: **PASS**.
- Kiểm tra rollback sau các phép thử: **PASS**.
- Đây là kiểm tra cấu trúc và một số trường hợp lỗi; **chưa chứng minh toàn bộ AT01–AT49, tranh chấp đồng thời, thanh toán hay luồng nghiệp vụ**.

## 7. Cấu hình kết nối Backend

- JDBC Driver Maven: `com.microsoft.sqlserver:mssql-jdbc:13.4.0.jre11`.
- Java đang dùng: `C:\Program Files\Java\jdk-21.0.12.1`, 64-bit (`amd64`).
- DLL xác thực Windows trên máy phát triển, ngoài repository:
  `D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0\mssql-jdbc_auth-13.4.0.x64.dll`.
- Cấu hình mặc định: `backend/src/main/resources/application.properties` trỏ `SuncareClinic`, với **`spring.flyway.enabled=false`**.
- Profile kiểm thử: `application-migrationtest.properties` (trỏ database thử và bật Flyway).
- Profile chạy migration database chính: `application-migrationmain.properties` (đã dùng theo xác nhận Bước 25); **không kích hoạt khi chạy thông thường**.
- Cấu hình SSL `trustServerCertificate=true` chỉ dành cho phát triển cục bộ; production cần chứng chỉ được xác minh.
- Không commit secret hoặc thư viện DLL vào repository nếu không có quy trình quản lý thích hợp.

## 8. Trạng thái Backend

- Package gốc: `com.suncare.clinic`.
- Endpoint đã có: `GET http://localhost:8080/api/health`.
- File kiểm thử tích hợp: `backend/src/test/java/com/suncare/clinic/SqlServerConnectivityIT.java`.
- Lỗi trước đó: file test nằm nhầm trong `src/main/java` gây thiếu JUnit; đã chuyển sang `src/test/java` và Maven báo **1 test, 0 lỗi, BUILD SUCCESS**.
- Bước 26 (chạy Backend mặc định và Health API sau triển khai): **được người phát triển xác nhận hoàn thành**; không có nguyên văn đầu ra Health API đính kèm trong lần xác nhận đó.
- Chưa có API nghiệp vụ Patient/Account/Staff được xác nhận đã triển khai trong giai đoạn này.

## 9. Trạng thái Frontend

- React JavaScript, Vite, Tailwind v4 và Font Awesome đã khởi tạo ở giai đoạn 00.
- Môi trường phát triển sử dụng `http://localhost:5173`; proxy `/api` đến `http://127.0.0.1:8080`.
- Có giao diện cơ bản và kiểm tra trạng thái Backend; chưa có UI nghiệp vụ được xác nhận triển khai.

## 10. Các file quan trọng

- `docs/PROJECT_STATE.md` — trạng thái tổng thể dự án.
- `docs/implementation/PROGRESS.md` — bằng chứng triển khai và các điểm chưa xác minh.
- `docs/database/SCHEMA_PLAN.md` — kế hoạch schema.
- `backend/pom.xml` — dependencies Spring Boot / JDBC / Flyway.
- `backend/src/main/resources/application.properties` — cấu hình chạy bình thường.
- `backend/src/main/resources/application-migrationtest.properties` — chạy thử Flyway.
- `backend/src/main/resources/application-migrationmain.properties` — chạy Flyway database chính.
- `backend/src/main/resources/db/migration/V1__*.sql` đến `V11__*.sql`.

Tài liệu nghiệp vụ `01-nghiep-vu.md`, `03-tu-dien-du-lieu.md`, `04-trang-thai-phan-quyen.md`, `05-giao-dich-rang-buoc.md`, `06-bao-cao-kiem-chung.md` có trong tài liệu Project của ChatGPT; **không tự mặc định đã tồn tại trong repository cục bộ**.

## 11. Hướng dẫn chạy cục bộ

### Backend

```powershell
cd "D:\TaiLieu\Nam4Ky1\Project_1\suncare-clinic\backend"
$env:Path = "D:\TaiLieu\Nam4Ky1\Project_1\jdbc-auth\13.4.0;" + $env:Path
.\mvnw.cmd spring-boot:run
```

- Chỉ dùng cấu hình mặc định, **không** bật `migrationtest` hoặc `migrationmain` cho lần chạy Backend thông thường.
- API kiểm tra: `http://localhost:8080/api/health`.

### Frontend

```powershell
cd "D:\TaiLieu\Nam4Ky1\Project_1\suncare-clinic\frontend"
npm run dev
```

## 12. Chưa hoàn thành / giới hạn đã biết

- Chưa triển khai Endpoint nghiệp vụ, Authentication, Authorization và DTO/Validation cho Patient/Staff/Account.
- Chưa xây dựng Repository/Service nghiệp vụ và test transaction đồng thời.
- Chưa kiểm tra đầy đủ toàn bộ AT01–AT49 trên SQL Server bằng dữ liệu giả và hai kết nối cạnh tranh.
- Quy tắc liên bảng và chống giao khoảng lịch, giá, phân công phải được bảo vệ trong transaction/backend; metadata CHECK/UNIQUE đơn lẻ không đủ.
- Chưa tích hợp MoMo, Cloudinary, Jitsi; chưa có tính năng báo cáo và PDF hóa đơn thực tế.
- Chưa hoàn thiện bảo mật/cấu hình cho production; SQL Server Enterprise Evaluation là phiên bản đánh giá có thời hạn.

## 13. Nhật ký mốc tiến độ

| Thời điểm | Mốc | Kết quả / căn cứ |
|---|---|---|
| 2026-11-01 | Giai đoạn 00 — Khởi tạo | Hoàn thành theo xác nhận |
| 2026-11-01 | SQL Server và `SuncareClinic` | SSMS kết nối, database được tạo |
| 2026-11-01 | Migration V1–V11 | 11 file, 58 CREATE TABLE, kiểm tra PowerShell thành công |
| 2026-11-01 | `SuncareClinicMigrationTest` | Flyway 11/11 thành công, 58 bảng, kiểm tra constraint và rollback PASS |
| 2026-11-01 | `SuncareClinic` | Bước 25 được người phát triển xác nhận hoàn thành |
| 2026-11-01 | Backend sau migration | Bước 26 được người phát triển xác nhận hoàn thành |
| 2026-11-02 | Bàn giao giai đoạn Database | Chuẩn bị tài liệu PROJECT_STATE và PROGRESS cho chat mới |

## 14. Trạng thái tổng kết và công việc kế tiếp

**Đã đạt mốc:** Thiết lập database và migration; ứng dụng nền có Health API.

**Bắt đầu ở cuộc trò chuyện tiếp theo:** Rà soát hiện trạng mã nguồn Backend và phát triển **một tính năng có phạm vi rõ ràng**: đăng ký Patient + Account + AccountRole trong một transaction, bảo đảm không thể tự cấp quyền STAFF/ADMIN, đúng nghiệp vụ và từ điển dữ liệu. Đọc file thực tế trước khi tạo hoặc sửa mã nguồn. Chưa triển khai Appointment/Payment/CLS trong cùng tác vụ.

**Nguyên tắc bàn giao:** Không chạy lại migration đã áp dụng như bước khởi tạo, không sửa V1–V11, không suy diễn rằng kiểm thử API/nghiệp vụ đã xong khi mới có kiểm thử schema.