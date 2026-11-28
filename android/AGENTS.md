# Quy tắc riêng cho Android

Các quy tắc root trong `../AGENTS.md` vẫn quản lý nghiệp vụ, bảo mật, dữ liệu, hợp đồng API và kiểm chứng. File này chỉ bổ sung quy ước cho ứng dụng Android trong thư mục `android/`.

- Với các việc Android, làm việc, sửa tệp, chạy lệnh build và lưu kết quả kiểm thử trong `android/`. Chỉ đọc hợp đồng API hoặc tài liệu ngoài thư mục này khi cần đối chiếu. Mọi thay đổi ngoài `android/` cần yêu cầu riêng của chủ dự án.
- Giữ **Kotlin + Jetpack Compose + Material 3** (`minSdk = 26`). Không chuyển sang React Native, Flutter hay thêm framework nặng khi việc hiện tại chưa cần.
- Tổ chức code theo cấu trúc rõ ràng trong `app/src/main/java/com/example/suncareclinic/`: `data/` (`api/`, `dto/`, `local/`), `ui/` (`theme/`, `components/`, `navigation/`, `screens/`).
- Đồng bộ nhận diện màu **Sunflower Clinical** với `frontend/src/index.css` (nền warm ivory `#FFFDF8`, bề mặt trắng `#FFFFFF`, nút chính vàng hướng dương `#F4B942` + chữ đậm `#26342C`, xanh lá `#59744B`, không dùng chữ trắng trên nền vàng).
- Khi thiết kế hoặc chỉnh sửa màn hình trong `android/`, sử dụng skill `../.agents/skills/mobile-app-ui-design/SKILL.md` cho bố cục mobile (lưới 8dp, thumb-zone, phân cấp chữ, trạng thái loading/empty/error, chuyển động gọn). Chỉ áp dụng phần phù hợp với Kotlin + Jetpack Compose + Material 3 và không ghi đè quy tắc nghiệp vụ của Suncare Clinic.
- Mọi màn hình gắn API phải gọi Backend Spring Boot thật (dùng profile `migrationtest` khi thử ghi dữ liệu; Emulator/máy thật gọi `http://127.0.0.1:8080/` kèm `adb reverse tcp:8080 tcp:8080`). Theo dõi tiến độ Android riêng tại `ANDROID_PROGRESS.md` (`A00`–`Axx`).

## Phạm vi chức năng & Quy tắc UX Mobile (loại trừ các phần chỉ dành cho Web)

- **Vai trò trong phạm vi App**: Chỉ triển khai luồng nghiệp vụ cho **Bệnh nhân (`PATIENT`)** (trọng tâm chính: đăng ký/đăng nhập, hồ sơ cá nhân, tìm & đặt lịch trực tiếp/trực tuyến, thanh toán MoMo, khám video trực tuyến, xem lịch khám & hồ sơ bệnh án EMR/đơn thuốc/kết quả CLS chỉ đọc) và **Bác sĩ (`DOCTOR`)** (hỗ trợ di động: lịch trực & lịch hẹn được giao, danh sách/chi tiết lượt khám, tra cứu nhanh tiền sử/dị ứng/lịch sử khám cũ, ghi nhận nhanh sinh hiệu/chẩn đoán/hóa đơn hoặc xác nhận nhanh).
- **Loại bỏ khỏi kế hoạch App (chỉ làm trên Web)**:
  - Quản trị hệ thống `SYSTEM_ADMIN` (`P07`–`P12`: quản lý nhân viên, phân quyền, khoa/chuyên khoa/phòng, danh mục dịch vụ/bảng giá/thuốc/dị nguyên).
  - Xếp lịch & công bố lịch làm việc `DEPARTMENT_HEAD` (`P13`–`P14`).
  - Thu ngân & hoàn tiền `CASHIER` (`P21`, `P30`, `P42`: thu tiền mặt tại quầy, hoàn tiền thủ công kèm chứng từ, in hóa đơn nhiệt/PDF tại quầy).
  - Vận hành phòng Cận lâm sàng `LAB_STAFF` (`P25`–`P27`, `P40`: hàng đợi check-in lab, nhập bảng chỉ số, tải lên/phát hành/đính chính tệp kết quả CLS).
  - Báo cáo thống kê & xuất CSV (`reporting`: `P43+`).
- **Không bê nguyên UX Web sang App**:
  - Không dùng bảng nhiều cột dàn ngang kiểu desktop (`<table>`); thay bằng danh sách thẻ (`SuncareCard`) xếp dọc + chạm mở `ModalBottomSheet` chi tiết.
  - Giữ Bottom Navigation Bar gọn gàng (tối đa 4–5 tab theo đúng vai trò).
  - Với các nghiệp vụ nhập liệu dài của Bác sĩ ở giai đoạn sau (kê đơn thuốc nhiều dòng `P22`, chỉ định nhiều CLS `P24`, đối chiếu bảng kết quả lab dài `P28`), ưu tiên dạng **xem nhanh / xác nhận gọn** trên App; thao tác nhập bảng phức tạp giữ trên Web.
  - Không tự dựng trình tạo/in PDF phức tạp trong App; hiển thị dạng thẻ tóm tắt rõ ràng hoặc mở bản PDF/HTML do server trả về.
