# Cách dùng skill khi làm frontend Suncare

File này hướng dẫn chọn và phối hợp skill cho công việc trong `frontend/`. Nó không thay thế `../AGENTS.md`, `AGENTS.md`, tài liệu nghiệp vụ, API đang chạy hay sổ tiến độ. Không sao chép toàn bộ nội dung skill vào đây; đọc skill gốc khi công việc cần dùng.

## Nguồn nào quyết định điều gì

| Nguồn | Trách nhiệm |
|---|---|
| Yêu cầu hiện tại của chủ dự án | Phạm vi và ưu tiên của lượt làm việc. |
| `../AGENTS.md`, `AGENTS.md`, `../docs/implementation/PROGRESS.md` | Quy trình, ranh giới việc Pxx, cách kiểm tra và bàn giao. |
| `../docs/01-...` đến `06-...`, API/DTO và code hiện tại | Nghiệp vụ cần đạt và trạng thái triển khai thực tế. Nếu mâu thuẫn ảnh hưởng nghiệp vụ, nêu rõ và hỏi; không tự sửa tài liệu để khớp code. |
| `../.agents/skills/suncare-clinical-frontend/SKILL.md` | Hệ màu Sunflower Clinical, phân cấp thông tin theo vai trò, trạng thái UI và giới hạn frontend Suncare. |
| Các skill thiết kế, audit và hiệu năng khác | Gợi ý trong chuyên môn riêng; chỉ áp dụng phần phù hợp với các nguồn phía trên. |

Mỗi nguồn giữ nội dung của mình. Không ghi đè file skill, tài liệu nghiệp vụ, migration, API contract hoặc thay đổi đang có của người dùng chỉ để các nguồn trông giống nhau. Khi sửa một file, đọc trạng thái Git và diff trước; chỉ thay phần thuộc phạm vi việc hiện tại.

## Chọn skill theo việc

- **Xây hoặc chỉnh màn hình Suncare:** đọc `suncare-clinical-frontend/SKILL.md` và hai reference `design-system.md`, `clinical-screens.md` liên quan. Đây là skill chính cho React JavaScript, Tailwind v4, Font Awesome và luồng theo vai trò.
- **Thiết kế mới hoặc đổi bố cục đáng kể:** dùng thêm `frontend-design/SKILL.md` để lập hướng màu, chữ, bố cục và tự phê bình thiết kế. Giữ token và quy tắc lâm sàng của Suncare; không áp một mẫu giao diện chung lên mọi vai trò.
- **Người dùng yêu cầu audit UI, UX hoặc accessibility:** dùng `web-design-guidelines/SKILL.md`. Skill này yêu cầu lấy bản guideline mới nhất từ URL ghi trong skill trước mỗi lần audit; báo phát hiện theo file và dòng. Không xem kết quả audit là quyền tự sửa ngoài phạm vi được giao.
- **Viết hoặc tối ưu React:** dùng `vercel-react-best-practices/SKILL.md`, đọc các rule liên quan. Chỉ dùng quy tắc phù hợp với ứng dụng Vite chạy client; ví dụ Next.js, RSC và gợi ý thêm thư viện không tự trở thành yêu cầu của Suncare.

Không cần gọi cả bốn skill cho mọi thay đổi nhỏ. Nếu skill đưa ra lựa chọn khác nhau, giữ đúng nghiệp vụ, API, quyền, khả năng truy cập và phạm vi việc trước; ghi ngắn gọn lý do chọn cách triển khai.

## Checklist cho một việc frontend

1. Đọc quy ước và `PROGRESS.md`; xem `git status --short`, màn hình, CSS, route, service và DTO/API liên quan. Xác định đúng một việc Pxx hoặc phạm vi người dùng chỉ định.
2. Trước khi sửa, nêu mục tiêu, file dự kiến và cách kiểm tra. Với trang dùng API, liệt kê tải, rỗng, thành công, lỗi validation, lỗi mạng, hết phiên, cấm truy cập và xung đột khi áp dụng.
3. Chỉ hiển thị dữ liệu có nguồn xác nhận. Không đặt tên khoa/bác sĩ, trạng thái thanh toán, lịch hẹn hay thao tác giả như dữ liệu/chức năng thật. Giữ lời gọi API trong `src/services/`; backend quyết định quyền và nghiệp vụ.
4. Kiểm tra nhãn form, focus bàn phím, tương phản, giảm chuyển động và không tràn ngang ở 320, 375, 768, 1280 px. Với theme, rà mọi lớp `suncare-*` đang dùng trước khi đổi token.
   - Form, dialog, menu và popup khi xuất hiện hoặc đóng nên có hiệu ứng nhẹ, ngắn và có chủ đích. Có cả chiều mở lẫn chiều đóng; chờ hiệu ứng đóng xong mới tháo phần tử khỏi DOM. Giữ focus và thao tác bàn phím đúng; tắt hoặc rút ngắn hiệu ứng khi `prefers-reduced-motion: reduce`. Không để hiệu ứng làm chậm hoặc che thông báo lỗi quan trọng.
5. Chạy `npm run lint` và `npm run build` trong `frontend/`. Thử luồng thực tế với API và database thử khi khả dụng; ghi rõ lệnh, kết quả và phần chưa thử, không suy PASS từ đọc code hoặc build.
6. Cập nhật `../docs/implementation/PROGRESS.md` theo quy tắc repo sau mốc đáng kể; bàn giao đúng một việc tiếp theo. Không tự commit hoặc push.
