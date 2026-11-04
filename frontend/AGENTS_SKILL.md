# Chọn skill cho công việc frontend

Đây là file định tuyến skill; nó không thêm checklist triển khai hay lặp lại quy tắc trong `../AGENTS.md`, `AGENTS.md` và skill chuyên môn. Các yêu cầu hiện tại của người dùng quyết định phạm vi. Quy tắc dự án, tài liệu nghiệp vụ, API và code hiện tại quyết định tính đúng đắn.

| Công việc | Skill dùng |
|---|---|
| Xây, chỉnh hoặc làm mới UI Suncare | `.agents/skills/suncare-clinical-frontend/SKILL.md` là hướng dẫn chính; đọc `references/design-system.md` và phần liên quan trong `references/clinical-screens.md`. |
| Thiết kế mới hoặc đổi bố cục lớn | Thêm `.agents/skills/frontend-design/SKILL.md` để tự phê bình bố cục; giữ bảng màu, kiểu chữ, token, luồng và khả năng truy cập của Suncare. |
| Audit theo Web Interface Guidelines | Dùng `.agents/skills/web-design-guidelines/SKILL.md` khi người dùng yêu cầu audit UI/accessibility/UX; đó là quy trình review riêng, không tự mở rộng thành sửa code. |
| Tối ưu hiệu năng React | Dùng `.agents/skills/vercel-react-best-practices/SKILL.md` và chỉ đọc rule liên quan. Chọn hướng dẫn phù hợp React chạy client với Vite; bỏ qua phần chỉ dành cho Next.js, RSC hoặc server actions và không thêm thư viện chỉ vì một rule gợi ý. |

Không cần nạp mọi skill cho mỗi thay đổi. Skill thiết kế và hiệu năng bổ sung chuyên môn; chúng không thay đổi API, quyền, nghiệp vụ, stack hoặc phạm vi đã được xác định trong hướng dẫn dự án.
