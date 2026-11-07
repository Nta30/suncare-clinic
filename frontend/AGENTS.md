# Quy tắc riêng cho frontend

Các quy tắc root trong `../AGENTS.md` vẫn quản lý phạm vi việc, roadmap, nghiệp vụ, bảo mật, kiểm tra và bàn giao. File này chỉ bổ sung quy ước mã nguồn frontend.

- Giữ React JavaScript, Vite, Tailwind CSS v4 và Font Awesome. Không chuyển sang TypeScript/Next.js hoặc thêm framework UI/state lớn nếu việc hiện tại không yêu cầu.
- Đặt màn hình riêng trong `src/features/<module>/`, thành phần dùng chung trong `src/components/ui/` hoặc `src/components/layout/`, và lời gọi API trong `src/services/`. Giữ `App.jsx` gọn theo tính năng.
- Với màn hình nghiệp vụ, căn nội dung từ trái và tận dụng chiều ngang khả dụng; tránh bọc toàn màn hình trong khung `max-width` căn giữa. Trên màn nhỏ, xếp các vùng nội dung theo chiều dọc.
- Khi xây hoặc chỉnh giao diện Suncare, dùng skill `.agents/skills/suncare-clinical-frontend/SKILL.md` cho hệ thống Sunflower Clinical và khả năng truy cập. Chọn skill bổ trợ qua `AGENTS_SKILL.md` khi công việc thực sự cần.
