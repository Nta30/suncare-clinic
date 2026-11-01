
# SUNCARE CLINIC — PROJECT STATE

## 1. Thông tin chung

- Tên dự án: Suncare Clinic
- Thương hiệu: SUNCARE
- Loại dự án: Clinic Management Website
- Ngày cập nhật: 2026-11-01
- Giai đoạn: 00 — KHỞI TẠO DỰ ÁN
- Trạng thái: HOÀN THÀNH KHỞI TẠO
- Môi trường phát triển: Windows
- Hình thức triển khai hiện tại: Local Development

### Thư mục gốc

`D:\TaiLieu\Nam4Ky1\Project_1\suncare-clinic`

### Công cụ phát triển

| Thành phần | Công cụ |
|---|---|
| Backend IDE | IntelliJ IDEA |
| Frontend IDE | Visual Studio Code |
| Backend | Spring Boot, Java 21, Maven |
| Frontend | React, JavaScript, Vite |
| CSS | Tailwind CSS v4 |
| Icons | Font Awesome |
| Database dự kiến | Microsoft SQL Server |
| Database IDE dự kiến | SSMS |
| Lưu trữ ảnh dự kiến | Cloudinary |
| Thanh toán dự kiến | MoMo |
| Video dự kiến | Jitsi |

Phiên bản Spring Boot chính xác được xác định
bằng file `backend/pom.xml`.

---

## 2. Phạm vi dự án

Suncare Clinic là website quản lý phòng khám
cho một cơ sở khám chữa bệnh ngoại trú.

Các nhóm nghiệp vụ dự kiến:

1. Tài khoản và phân quyền.
2. Quản lý bệnh nhân và nhân viên.
3. Quản lý khoa, chuyên khoa và phòng.
4. Quản lý lịch làm việc.
5. Đặt khám trực tiếp và trực tuyến.
6. Khám bệnh và chẩn đoán.
7. Chỉ định và kết quả cận lâm sàng.
8. Kê đơn thuốc.
9. Hóa đơn, thanh toán và hoàn tiền.
10. Hồ sơ bệnh án điện tử.
11. Dashboard và báo cáo.
12. Quản lý tệp và khám video.

Ngoài phạm vi phiên bản đầu tiên:

- Điều trị nội trú.
- Bảo hiểm y tế (BHYT).
- Quản lý kho thuốc.
- Bán hoặc cấp phát thuốc.
- Kế toán tổng hợp.
- Hóa đơn thuế.

Các quy tắc nghiệp vụ và thiết kế dữ liệu
phải tuân theo tài liệu đã thống nhất:

- `01-nghiep-vu.md`
- `03-tu-dien-du-lieu.md`

Hai tài liệu trên được cung cấp trong ChatGPT
Project. Không mặc định rằng chúng đã được
sao chép vào thư mục docs trên máy.

---

## 3. Tiến độ khởi tạo

### Cuộc trò chuyện 00 — KHỞI TẠO DỰ ÁN

- [x] Bước 1: Tạo thư mục gốc suncare-clinic.
- [x] Bước 2: Cấu hình Spring Initializr.
- [x] Bước 3: Tải và giải nén Spring Boot.
- [x] Bước 4: Khởi tạo Vite React JavaScript.
- [x] Bước 5: Cài Tailwind CSS v4 và Font Awesome.
- [x] Bước 6: Thiết lập cấu trúc frontend/backend.
- [x] Bước 7: Tạo Spring Boot Health API.
- [x] Bước 8: Xây dựng giao diện và kết nối API.
- [x] Bước 9: Kiểm tra compile, test và build.
- [x] Bước 10: Tạo PROJECT_STATE.md.

Kết quả các bước 1–9 do người phát triển
xác nhận trong quá trình hướng dẫn.

File này được tạo để ghi nhận Bước 10.

---

## 4. Trạng thái Backend

### Công nghệ

- Java 21.
- Spring Boot.
- Maven và Maven Wrapper.
- Spring Web MVC.
- Spring Boot Actuator.
- Validation.
- Spring Boot DevTools.

### Vị trí

`backend/`

### Package gốc

`com.suncare.clinic`

### Tổ chức mã nguồn

- `common/config`
- `common/controller`
- `common/exception`
- `common/response`
- `modules/account`
- `modules/organization`
- `modules/scheduling`
- `modules/appointment`
- `modules/encounter`
- `modules/clinical`
- `modules/prescription`
- `modules/billing`
- `modules/medicalrecord`
- `modules/reporting`
- `modules/media`
- `modules/video`

Các module nghiệp vụ mới ở mức cấu trúc.
Chưa triển khai API hoặc xử lý nghiệp vụ.

### API đã triển khai

**GET /api/health**

Controller:

`backend/src/main/java/com/suncare/clinic/common/controller/HealthController.java`

Địa chỉ:

`http://localhost:8080/api/health`

Phản hồi mong đợi:

```json
{
  "status": "UP",
  "service": "suncare-clinic"
}
```

API xác nhận ứng dụng Spring Boot phản hồi.
Chưa kiểm tra database hoặc dịch vụ bên ngoài.

### Trạng thái kiểm tra

- Compile: Đã hoàn thành theo xác nhận.
- Maven Test: Đã hoàn thành theo xác nhận.
- Maven Package: Đã hoàn thành theo xác nhận.
- REST Health API: Đã kiểm tra thành công.
- Kết nối SQL Server: Chưa thực hiện.

---

## 5. Trạng thái Frontend

### Công nghệ

- React.
- JavaScript.
- Vite.
- Tailwind CSS v4.
- Font Awesome.

### Vị trí

`frontend/`

### Cấu trúc chính

```text
src/
├── app/
├── assets/
├── components/
│   ├── ui/
│   └── layout/
├── features/
│   ├── account/
│   ├── organization/
│   ├── scheduling/
│   ├── appointment/
│   ├── encounter/
│   ├── clinical/
│   ├── prescription/
│   ├── billing/
│   ├── medicalrecord/
│   ├── reporting/
│   ├── media/
│   └── video/
├── hooks/
├── routes/
├── services/
│   └── healthApi.js
├── utils/
├── App.jsx
├── index.css
└── main.jsx
```

### Giao diện đã có

- Giao diện thương hiệu Suncare.
- Header và sidebar.
- Điều hướng đến các khu vực trên trang.
- Khu vực giới thiệu hệ thống.
- Danh sách phân hệ dự kiến.
- Thẻ trạng thái kết nối Spring Boot.
- Nút kiểm tra lại kết nối.
- Hiển thị lỗi khi backend không phản hồi.
- Giao diện responsive.

### API Frontend đang sử dụng

`GET /api/health`

File gọi API:

`frontend/src/services/healthApi.js`

Vite proxy chuyển `/api` đến:

`http://127.0.0.1:8080`

Vite dev server:

`http://localhost:5173`

Proxy hiện chỉ áp dụng cho môi trường phát triển.

### Trạng thái kiểm tra

- React Dev Server: Hoạt động.
- Tailwind CSS v4: Hoạt động.
- Font Awesome: Hoạt động.
- Responsive: Đã kiểm tra.
- Backend Connection: Thành công.
- Frontend Build: Hoàn thành theo xác nhận.
- Frontend Lint: Đã kiểm tra theo Bước 9.

---

## 6. Database

Hệ quản trị dự kiến: Microsoft SQL Server.

Trạng thái hiện tại:

- [ ] Tạo database.
- [ ] Thiết kế và triển khai SQL schema.
- [ ] Tạo migration.
- [ ] Cấu hình Spring Data JPA.
- [ ] Cài SQL Server JDBC Driver.
- [ ] Kết nối backend với SQL Server.
- [ ] Kiểm tra transaction và dữ liệu.

Chưa có database nghiệp vụ.

Không tự tạo schema hoặc bảng khi chưa đến
giai đoạn triển khai database.

Thiết kế phải dựa trên từ điển dữ liệu
và quy tắc nghiệp vụ đã thống nhất.

---

## 7. Tích hợp bên ngoài

| Dịch vụ | Trạng thái |
|---|---|
| Cloudinary | Chưa tích hợp |
| MoMo | Chưa tích hợp |
| Jitsi | Chưa tích hợp |
| Triển khai Internet | Chưa thực hiện |

Không lưu mật khẩu, API key, token, private key
hoặc thông tin thanh toán bí mật trong file này.

---

## 8. Hướng dẫn chạy dự án

### Backend — IntelliJ IDEA

Mở thư mục:

`D:\TaiLieu\Nam4Ky1\Project_1\suncare-clinic\backend`

Chạy class Spring Boot có annotation
`@SpringBootApplication`.

Hoặc sử dụng PowerShell:

```powershell
cd "D:\TaiLieu\Nam4Ky1\Project_1\suncare-clinic\backend"
.\mvnw.cmd spring-boot:run
```

Kiểm tra:

`http://localhost:8080/api/health`

### Frontend — VS Code

Mở thư mục:

`D:\TaiLieu\Nam4Ky1\Project_1\suncare-clinic\frontend`

Chạy:

```powershell
npm run dev
```

Mở:

`http://localhost:5173`

Lưu ý: Phải chạy npm trong thư mục frontend,
không chạy ở thư mục gốc suncare-clinic.

---

## 9. Lệnh kiểm tra dự án

### Backend

Chạy trong thư mục `backend`:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd test
.\mvnw.cmd package
```

Kết quả thành công: BUILD SUCCESS.

File JAR nằm trong:

`backend/target/`

### Frontend

Chạy trong thư mục `frontend`:

```powershell
npm run lint
npm run build
```

Kết quả build nằm trong:

`frontend/dist/`

Các kết quả trên đã được người phát triển
xác nhận trong Bước 9.

Chưa lưu log kiểm thử tự động vào tài liệu này.

---

## 10. Những phần chưa triển khai

- Database SQL Server.
- Entity và Repository nghiệp vụ.
- CRUD nghiệp vụ.
- Đăng ký và đăng nhập.
- Phân quyền.
- Quy trình đặt khám.
- Quy trình khám.
- Quản lý cận lâm sàng.
- Kê đơn thuốc.
- Hóa đơn và thanh toán.
- Hồ sơ bệnh án điện tử.
- Dashboard thực tế.
- Cloudinary.
- MoMo.
- Jitsi.
- Cấu hình production.
- Triển khai website lên Internet.

Giao diện hiện tại chỉ phục vụ khởi tạo
và kiểm tra kết nối hệ thống.

Không sử dụng dữ liệu bệnh nhân thực.

---

## 11. Quy ước phát triển

1. Backend viết bằng Java 21, Spring Boot.
2. Frontend viết bằng React và JavaScript.
3. Không chuyển frontend sang TypeScript.
4. Sử dụng Tailwind CSS v4.
5. Icon sử dụng Font Awesome.
6. Backend phát triển bằng IntelliJ IDEA.
7. Frontend phát triển bằng VS Code.
8. Backend cung cấp REST API.
9. Phát triển theo các module nghiệp vụ.
10. Không tự ý thay đổi quy tắc nghiệp vụ đã chốt.
11. Không lưu thông tin bí mật vào Git.
12. Mỗi chức năng cần có bước kiểm tra rõ ràng.
13. Chỉ đánh dấu hoàn thành khi đã kiểm tra.
14. Cập nhật PROJECT_STATE.md sau các mốc quan trọng.

---

## 12. Hướng phát triển tiếp theo

Giai đoạn khởi tạo đã hoàn tất.

Giai đoạn tiếp theo cần được triển khai
trong cuộc trò chuyện riêng.

Các công việc dự kiến:

- Rà soát yêu cầu nghiệp vụ và dữ liệu.
- Lập kế hoạch triển khai database.
- Chuẩn bị SQL Server.
- Thiết kế migration theo từ điển dữ liệu.
- Xác định thứ tự triển khai các module.
- Phát triển từng chức năng có kiểm thử.

Không triển khai các công việc này trong
cuộc trò chuyện khởi tạo.

---

## 13. Nhật ký tiến độ

| Ngày | Nội dung | Trạng thái |
|---|---|---|
| 2026-11-01 | Tạo thư mục gốc Suncare | Hoàn thành |
| 2026-11-01 | Khởi tạo Spring Boot | Hoàn thành |
| 2026-11-01 | Khởi tạo React + Vite | Hoàn thành |
| 2026-11-01 | Cài Tailwind và Font Awesome | Hoàn thành |
| 2026-11-01 | Tổ chức thư mục dự án | Hoàn thành |
| 2026-11-01 | Tạo Health API | Hoàn thành |
| 2026-11-01 | Kết nối React và Spring Boot | Hoàn thành |
| 2026-11-01 | Kiểm tra build và test | Hoàn thành |
| 2026-11-01 | Tạo PROJECT_STATE.md | Hoàn thành khi lưu file |

### Quy tắc cập nhật nhật ký

Mỗi lần hoàn thành một công việc:

1. Ghi ngày thực hiện.
2. Ghi chức năng hoặc thay đổi.
3. Ghi kết quả kiểm thử.
4. Ghi lỗi hoặc vấn đề còn tồn tại.
5. Cập nhật phần chưa triển khai.
6. Chỉ đánh dấu hoàn thành khi đã xác nhận.

---

## 14. Trạng thái tổng kết

**Giai đoạn:** 00 — KHỞI TẠO DỰ ÁN

**Trạng thái:** HOÀN THÀNH

Backend Spring Boot và Frontend React
đã được khởi tạo, có REST Health API
và giao diện kiểm tra kết nối.

Database và các nghiệp vụ thực tế
chưa được triển khai.

Suncare Clinic sẵn sàng bước vào
giai đoạn phát triển tiếp theo.
