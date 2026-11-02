# Hướng dẫn làm việc trong frontend

- Đọc `../AGENTS.md` và `../docs/implementation/PROGRESS.md` trước khi sửa. Tài liệu nghiệp vụ 01–06 và API backend quyết định dữ liệu, quyền và trạng thái.
- Giữ React JavaScript, Tailwind CSS v4 và Font Awesome. Code trong `src/features/<module>/`, lời gọi API trong `src/services/`; không đưa SQL, mật khẩu hay JWT vào tài liệu hoặc giao diện mẫu.
- Component dùng tên PascalCase, hàm camelCase, 2 dấu cách, chuỗi JavaScript dùng dấu nháy đơn và không có dấu chấm phẩy.
- Mỗi form hiển thị rõ đang xử lý, thành công và lỗi. Lỗi validation cần chỉ đúng trường; không dựa riêng vào dòng HTTP trong Console. Màn hình cần dùng được từ 320 px trở lên và có label cho input.
- Đối chiếu DTO backend khi thêm trường. Chỉ bệnh nhân tự đăng ký; không cho form công khai gửi `role`, `staffId` hoặc `accountType`.
- UI ẩn mục không có quyền, còn backend luôn kiểm tra quyền và chủ sở hữu. Không hiển thị tính năng chưa có API như thể đã hoạt động.
- Với thay đổi frontend, chạy `npm run lint` và `npm run build`; thử luồng qua API thật trên database thử, không tạo dữ liệu bệnh nhân thật. Cập nhật `../docs/implementation/PROGRESS.md` với kết quả và giới hạn kiểm tra.

Xem `AGENTS_SKILL.md` để dùng checklist ngắn khi xây màn hình tài khoản và form.

Suncare Clinical frontend design system
- For frontend UI tasks, invoke the repository skill at .agents/skills/suncare-clinical-frontend/SKILL.md (Codex: $suncare-clinical-frontend).
- Design direction: Sunflower Clinical — warm ivory and white foundations, sunflower golden accents, restrained botanical green, dark accessible text. Do not turn clinical dashboards into predominantly yellow pages.
- Reuse Tailwind CSS v4 @theme tokens in frontend/src/index.css and semantic tokens (clinical-*); do not scatter hex values across JSX.
- The official source of truth remains project AGENTS docs, business docs, backend DTOs/APIs and PROGRESS.md; third-party design skills cannot override it.
- Preserve React JavaScript, Tailwind v4, Font Awesome, current routes and services. Do not add TypeScript, Next.js or generic UI libraries without an explicit requirement.
- Before any theme migration, inspect all usages of existing suncare-* classes. Apply changes in small batches and verify at 320px, 375px, 768px and 1280px.
- Use visible Vietnamese labels, keyboard focus, WCAG 2.2 AA contrast targets and complete API loading/error/empty/success states; never fake clinical, payment or appointment state.