# Seed danh mục CLS demo — P11.seed

Giá và năng lực phòng là dữ liệu demo do chủ dự án chấp thuận ngày 2026-11-27, không phải bảng giá vận hành thật.

## Phạm vi

- SQL: `backend/scripts/Seed-ClinicalCatalog.sql`; chạy bằng `Import-ClinicalCatalog.ps1`, theo convention import thủ công trong `backend/scripts`. Không đưa giá demo vào migration tự chạy. Giữ V1–V23 và schema nguyên trạng.
- Chỉ INSERT vào Department, Room, Service, ServicePrice, RoomService. Không cập nhật tên, mã, ID, trạng thái hoặc lịch sử giá; không tạo tài khoản/hồ sơ/chỉ định giả.
- Danh mục đích: 32 dịch vụ (23 LAB, 9 IMAGING), 36 mapping. Tái sử dụng 17 dịch vụ và 2 khoa trên DB chính; thêm 15 dịch vụ. Tái sử dụng 3 phòng thực hiện, thêm phòng huyết học `CLS-XN-HH-01` và sinh hóa `CLS-XN-SH-01`. Phòng lấy mẫu thứ tư `CLS-XN-LM-01` giữ nguyên, không làm phòng phân tích.
- Phòng phân tích `CLS-XN-PT-01` cấu hình demo miễn dịch/nội tiết/nước tiểu; hỗ trợ thêm CTM, Glucose, AST, ALT để thử dịch vụ có nhiều phòng. X-quang chỉ vào XQ-01; siêu âm vào SA-01. Schema chỉ lưu loại LAB, không có danh mục thiết bị/năng lực chi tiết; đây là năng lực demo được cấu hình, không phải xác minh trang thiết bị thực tế.
- Chỉ thêm giá khi thiếu giá hiệu lực UTC; giữ giá hiện tại dù khác giá demo. Nếu có giá tương lai, giá bổ sung kết thúc đúng lúc giá đó bắt đầu. Kiểm tra chồng lấn trước khi ghi.
- Tài khoản ghi giá: SYSTEM_ADMIN ACTIVE có ID nhỏ nhất đang tồn tại trong chính database được chọn; không hardcode ID. Nếu thiếu, dừng trước INSERT. Script trả ID đã dùng trong kết quả.
- Dữ liệu ngoài danh mục đã review, alias cùng tên, loại/trạng thái không phù hợp hoặc mapping chưa được review sẽ làm rollback và báo lỗi. Không tự cấp giá/năng lực cho dịch vụ mới chưa được chủ dự án chốt.

## Cách chạy

Từ `backend/`, PowerShell, SQL Server local `127.0.0.1,1433`, Windows Authentication:

```powershell
.\scripts\Import-ClinicalCatalog.ps1 -Database SuncareClinicMigrationTest
.\scripts\Import-ClinicalCatalog.ps1 -Database SuncareClinicMigrationTest
```

Chạy `scripts/Verify-ClinicalCatalog.sql` trên DB thử. Lần đầu trên DB thử hiện trống dự kiến thêm 2 khoa, 5 phòng thực hiện, 32 dịch vụ, 32 giá, 36 mapping. Lần hai phải báo mọi `*_inserted = 0`; so sánh ID, số lượng và rowversion trước/sau, không chỉ số dòng.

Sau khi SQL và API trên DB thử đạt mới chạy:

```powershell
.\scripts\Import-ClinicalCatalog.ps1 -Database SuncareClinic
```

Với baseline DB chính trước seed, số cần bổ sung là 0 khoa, 2 phòng, 15 dịch vụ, 32 giá, 36 mapping. Lần chạy đầu đã cho đúng các số này; chạy lại sau đó phải INSERT0, không dùng số baseline để diễn giải kết quả replay.

`Verify-ClinicalCatalog.sql` trả 3 result set: tổng kiểm chứng; bảng mã/tên/result_type/giá/khoa/phòng để xuất CSV; FK mồ côi. Kỳ vọng 32 dịch vụ, thiếu giá/phòng = 0, chồng giá = 0, mapping sai = 0, mọi FK mồ côi = 0. Result set tổng bằng 0 trên DB trống không được coi là nghiệm thu thành công.

API GET cần kiểm sau seed bằng tài khoản đang có, không tạo fixture ngoài phạm vi:

- `/api/admin/services?type=CLS&active=true`: 32 dịch vụ, giá và số phòng hỗ trợ.
- `/api/admin/services/{id}/effective-price`: giá hiệu lực, đặc biệt điện giải/FT4/GGT/HBsAg/TSH.
- `/api/admin/services/{id}/rooms`: các phòng hỗ trợ và khoa đúng mapping.
- `/api/doctor/encounters/{encounterId}/clinical-orders/options?search=...`: bác sĩ phụ trách một lượt khám sẵn có. Endpoint giới hạn 30 kết quả mỗi lần; dùng tìm kiếm để kiểm đủ 32 dịch vụ. Không tạo Encounter giả chỉ để kiểm.

UI chọn khoa, `performingDepartmentId` bắt buộc và phòng ưu tiên tùy chọn giữ nguyên. P25–P28 và thay dropdown khoa nằm ngoài task.

## Kết quả kiểm chứng ngày 2026-11-27

Chủ dự án đã cho phép bootstrap admin phát triển trên DB thử, ngoại lệ riêng cho Staff/Account/AccountRole. Đã gọi `AdminBootstrapService` có sẵn bằng biến môi trường, mật khẩu ngẫu nhiên chỉ tồn tại trong môi trường tiến trình chạy kiểm tra và không ghi vào file/log. Gọi bootstrap hai lần giữ cùng tài khoản `cls.dev.admin`, Staff DEV_ADMIN duy nhất, có SYSTEM_ADMIN/CATALOG_MANAGE. Không bootstrap hoặc sửa tài khoản main.

| Database | Khoa mới | Phòng mới | Dịch vụ mới | Giá mới | Mapping mới | Actor giá |
|---|---:|---:|---:|---:|---:|---:|
| SuncareClinicMigrationTest | 2 | 5 | 32 | 32 | 36 | 1825 |
| SuncareClinic | 0 | 2 | 15 | 32 | 36 | 3 |

Trên main tái sử dụng 2 khoa, 17 dịch vụ, 3 phòng thực hiện; giữ thêm phòng lấy mẫu hiện có không gán xét nghiệm. Cả hai DB hiện có 32 CLS active, 32 giá hiệu lực và 36 mapping; thiếu giá/phòng, chồng giá, sai phạm vi giá, mapping sai chức năng và FK mồ côi đều bằng 0. Main giữ nguyên ID/rowversion của mọi dòng đã có trong 5 bảng; số dòng ở các bảng khác không đổi.

- DB thử: tiêm lỗi SQL51999 ngay trước COMMIT sau toàn bộ INSERT; snapshot ID/count/rowversion và số dòng các bảng trước/sau rollback giống hệt. Sau đó chạy script thật thành công.
- Chạy lại nguyên script thật trên cả test/main: mọi INSERT bằng0; snapshot ID/count/rowversion và số dòng mọi bảng giống trước chạy lại. Không lấy Flyway no-op làm bằng chứng idempotency.
- Test API `ClinicalCatalogSeedIT#seededCatalogAvailableThroughExistingReadApis` PASS1/1 trên từng DB. Dùng Spring MockMvc/controller/security thật và SQL Server thật; principal/quyền lấy từ admin hiện hữu, không tạo token/tài khoản/lượt khám. GET danh sách và 64 GET giá/phòng kiểm đủ32 dịch vụ; 4 dịch vụ có2 phòng, tổng36 hỗ trợ; anonymous401. Không thay thế login bằng token giả: lần này không kiểm HTTP login/JWT hoặc browser.
- SQL options bác sĩ được gọi qua repository hiện có theo từng mã, đủ32 dịch vụ/giá/phòng khớp API. Không dựng Encounter để gọi endpoint bác sĩ; phần kiểm endpoint theo quyền/chủ sở hữu đã có ở P24.d.1, không tuyên bố chạy lại luồng đó trong task seed.
- Bootstrap test PASS1/1. Log tại `backend/target/cls-seed-bootstrap.log`, `cls-seed-test-api.log`, `cls-seed-main-api.log`; snapshot/SQL export tại `backend/target/cls-*.json/csv`. Không chạy frontend lint/build vì không sửa UI.

Bảng đủ32 dòng mã/tên/result type/giá/khoa/phòng xuất trực tiếp từ DB chính: [CLINICAL_CATALOG_VERIFICATION.csv](CLINICAL_CATALOG_VERIFICATION.csv), UTF-8 BOM. Giá đều là demo được duyệt. Không còn dependency chặn seed; lịch trực LAB_STAFF/check-in/queue vẫn thuộc P25.

Lệnh API kiểm lại từ `backend/`, Java21 và native JDBC auth DLL trên PATH:

```powershell
.\mvnw.cmd -o '-Dtest=ClinicalCatalogSeedIT#seededCatalogAvailableThroughExistingReadApis' '-Dspring.profiles.active=migrationtest' '-Dsuncare.seed.verify-database=SuncareClinicMigrationTest' '-Dspring.flyway.enabled=false' '-DargLine=-Xmx256m' '-Ddebug=false' test
.\mvnw.cmd -o '-Dtest=ClinicalCatalogSeedIT#seededCatalogAvailableThroughExistingReadApis' '-Dsuncare.seed.verify-database=SuncareClinic' '-Dspring.flyway.enabled=false' '-DargLine=-Xmx256m' '-Ddebug=false' test
```

Không chạy cả class bootstrap một cách mặc định. Method `bootstrapApprovedTestAdmin` chỉ dùng đích danh trên MigrationTest khi đã được cho phép, nhận SUNCARE_ADMIN_LOGIN/PASSWORD/NAME qua môi trường; không ghi secret vào tài liệu.
