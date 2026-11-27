# Danh mục thuốc ngoại trú — V21

## Phạm vi và nguồn

Danh mục gồm **400 công thức thuốc generic**, chọn theo phạm vi 9 chuyên khoa hiện có. Có **201 tập hoạt chất**, **33 dạng bào chế/đường dùng** và **26 công thức phối hợp**. Đây là bộ thuật ngữ tham khảo được lựa chọn theo nhóm thuốc ngoại trú; không phải số liệu xếp hạng tần suất sử dụng tại Việt Nam hoặc Suncare.

Nguồn chính: [FDA NDC qua openFDA](https://open.fda.gov/apis/drug/ndc/), [định nghĩa trường](https://open.fda.gov/apis/drug/ndc/searchable-fields/), [FDA NDC Directory](https://www.fda.gov/drugs/drug-approvals-and-databases/national-drug-code-directory). Tải ngày **2026-11-27**, metadata nguồn cập nhật **2026-11-25**. [Giấy phép dữ liệu openFDA](https://open.fda.gov/license/) công bố dữ liệu thuộc phạm vi public domain/CC0 trừ ngoại lệ được nêu; không sử dụng dữ liệu thiết bị GMDN.

FDA NDC chứa thông tin do đơn vị ghi nhãn nộp; có NDC không đồng nghĩa FDA đã kiểm chứng/phê duyệt từng sản phẩm. Bộ này lọc thành phẩm dùng cho người, đang được liệt kê, nhóm ANDA/NDA/NDA AUTHORIZED GENERIC/OTC MONOGRAPH DRUG; loại thuốc kiểm soát DEA, chế phẩm chẩn đoán C-13, đường tiêm và đường dùng hỗn hợp không rõ. Không suy rằng sản phẩm được đăng ký/cung ứng tại Việt Nam. is_active=1 là trạng thái chọn trong danh mục phần mềm, không phải xác nhận pháp lý hoặc khuyến nghị điều trị.

## Dữ liệu có thể đối chiếu

- File: [outpatient-drugs-openfda.json](../../backend/scripts/data/outpatient-drugs-openfda.json).
- Mỗi bản ghi có đủ 7 trường Drug và metadata nguồn: product_ndc, spl_id/set_id, generic_name gốc, toàn bộ active_ingredients/strength gốc, dạng/đường dùng/bao gói gốc, nhóm lưu hành, ngày nguồn, URL truy vấn từng NDC và DailyMed khi có set_id.
- SHA256 manifest: `44f7186a53b1ea3db0ed622224b339d008bee9af0d961e8ce83d5fe7df005b13`. Hash này được ghi trong migration. Bản ghi FDA có thể thay đổi sau ngày tải; file JSON giữ bằng chứng đã dùng khi tạo V21.
- specialty_scope chỉ là metadata lựa chọn để kiểm tra độ bao phủ, không phải chỉ định điều trị hay quan hệ Drug–Specialty trong DB. Không thêm bảng phân loại.

| Chuyên khoa hiện có | Số công thức được gắn phạm vi lựa chọn |
|---|---:|
| Nội tổng quát | 70 |
| Tim mạch | 88 |
| Nội tiết | 38 |
| Tiêu hóa | 52 |
| Da liễu | 55 |
| Tai Mũi Họng | 58 |
| Chấn thương chỉnh hình | 42 |
| Nhi tổng quát | 47 |
| Ngoại tổng quát | 33 |

Một công thức có thể phục vụ nhiều phạm vi, nên không cộng các dòng thành 400. Các tên truy vấn không có bản ghi phù hợp bộ lọc không được tự bổ sung dữ liệu thay thế; xem query_counts trong manifest.

## Quy tắc chuẩn hóa

- NFC Unicode, bỏ khoảng trắng dư, chuẩn hóa chữ hoa/thường cho tên generic lấy từ hoạt chất; không nhập tên thương mại hoặc dịch đoán tên hoạt chất. Giữ cách gọi generic của nguồn Hoa Kỳ, ví dụ Acetaminophen; chưa có từ điển alias Paracetamol/Salbutamol.
- Cặp hoạt chất–hàm lượng được sắp cùng thứ tự tên hoạt chất, nối bằng ` + `. Ví dụ `Amoxicillin + Clavulanate potassium` đi cùng `500 mg + 125 mg`; không mất hàm lượng thành phần thứ hai.
- Chuẩn hóa khối lượng về mg và nồng độ về một đơn vị mẫu số bằng phân số chính xác: `250 mg/5mL` và `1000 mg/20mL` cùng thành `50 mg/mL`; `0.5 g/1` thành `500 mg`. Không dùng float, không làm tròn tỷ lệ vô hạn; đơn vị IU/USP gốc được giữ. `/1` của nguồn là trên một đơn vị dạng thuốc, không phải liều điều trị.
- Không tự quy đổi muối/hydrat thành hoạt chất nền; tên hóa học khác được giữ đúng nguồn. Không tự hợp nhất alias hoặc muối khi thiếu bằng chứng tương đương hàm lượng.
- Dịch dạng bào chế từ bảng ánh xạ rõ ràng, giữ khác biệt viên thường/bao phim/giải phóng muộn/kéo dài và đường uống/hít/mũi/ngoài/trực tràng. Đơn vị là viên, g, mL, liều xịt/hít; bột pha hỗn dịch dùng chai chỉ khi bao gói nguồn ghi BOTTLE. Không tự đặt thể tích chai hoặc quy cách hộp.
- Khóa nhận diện: ingredient + strength + dosage_form + unit đã chuẩn hóa. Mã `SCG-` + 16 ký tự SHA256 của khóa, tối đa 20 ký tự. Cùng hoạt chất khác hàm lượng/dạng được giữ riêng; cùng công thức khác hãng/bao gói được gộp.
- Không có giá, tồn kho, liều mặc định, số lần/ngày hoặc thời gian điều trị.

## Migration không ghi đè

[V21__seed_outpatient_drug_catalog.sql](../../backend/src/main/resources/db/migration/V21__seed_outpatient_drug_catalog.sql) chỉ INSERT dữ liệu. Không thay schema V8 hoặc các migration đã áp dụng.

V21 bỏ qua mã đã tồn tại và công thức tương đương đã có, kể cả bản ghi inactive; không đổi tên, trạng thái, ID, rowversion hoặc snapshot đơn thuốc hiện có. Mã seed trùng nhưng thuộc công thức khác làm migration lỗi rõ ràng, không ghi đè. Flyway bao transaction. Không sửa checksum/repair/clean/reset để che lỗi.

DB test ban đầu rỗng nên thêm đúng400 dòng. DB chính có thể thêm ít hơn nếu có dữ liệu tương đương trước khi chạy; cần đối chiếu nguồn thay vì ép tổng bằng400.

## Kiểm tra và tái hiện

Từ `backend/`:

```powershell
python -X utf8 scripts/prepare_drug_catalog.py --validate
.\mvnw.cmd -o '-Dtest=DrugCatalogSeedIT#seed*,DrugCatalogIT,OnsiteAppointmentBookingIT#prescription*,*Tests,*Test' '-DargLine=-Xmx256m' '-Ddebug=false' test -q
```

Dùng Java21 và PATH có JDBC native-auth DLL như cấu hình local. DrugCatalogSeedIT bật Flyway trên profile migrationtest, khẳng định DB_NAME=SuncareClinicMigrationTest. Test kiểm đủ400 bản ghi/7trường so manifest,26 phối hợp,9phạm vi, Flyway validate/re-run0migration, seed chạy lại không nhân đôi/ghi đè bản ghi local inactive, mã xung đột bị chặn, API và hồi quy đơn thuốc.

Trong phiên này backend8080 đã chạy migrationmain+DevTools, nên lệnh kiểm thử thực tế chạy ở bản sao cô lập `backend/target/catalog-validation/`; không build vào `backend/target/classes` của tiến trình đó. Log cuối: `backend/target/catalog-validation/catalog-seed-final-tests.log`, surefire cùng thư mục con target. Kết quả thực tế nằm trong PROGRESS.md.

Script hỗ trợ `--fetch` để truy xuất và cache nguồn, `--emit-sql <path>` để dựng SQL từ manifest. **Không dùng để ghi đè V21 đã áp dụng**. Muốn cập nhật nguồn sau này cần manifest và migration phiên bản mới. Cache raw nằm ở ignored `backend/target/drug-catalog-source/`; validator không cần mạng.

## API tra cứu

- DOCTOR: `GET /api/doctor/drugs?search=amoxicillin`.
- Admin CATALOG_MANAGE: `GET /api/admin/drugs?search=SCG-...`.
- search tùy chọn, tối đa100 ký tự; tìm chuỗi con theo mã/tên/hoạt chất, không phân biệt hoa/thường hoặc dấu tiếng Việt. `%`, `_`, `[`, `~` được escape; không ghép giá trị vào SQL.
- Không truyền search giữ hợp đồng trả mảng như trước; bác sĩ chỉ nhận active, admin nhận cả inactive. Không thêm UI hoặc schema phân loại.

## Cập nhật DB chính bằng migrationmain

**Trước khi build: dừng phiên8080 đang chạy profile migrationmain+DevTools.** Không để profile này chạy thường xuyên vì resource mới có thể bị áp dụng tự động khi IDE/Maven build. Phiên hiện tại chỉ áp dụng V21 trên DB test; DB chính vẫn V20,0thuốc tại hậu kiểm.

Sau khi xem manifest/SQL, backup theo quy trình local và xác minh URL trong application-migrationmain.properties trỏ đúng SuncareClinic. Từ `backend/`, đặt JAVA_HOME/PATH theo máy rồi chạy một lần:

```powershell
$env:JAVA_HOME='C:/Users/ADMIN/.jdks/openjdk-21.0.2'
$env:Path="$env:JAVA_HOME/bin;D:/Nam_4/Project_1/jdbc-auth/13.4.0;"+$env:Path
.\mvnw.cmd '-Dspring-boot.run.profiles=migrationmain' '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false' '-Dspring-boot.run.arguments=--spring.main.web-application-type=none --suncare.bootstrap.admin.enabled=false' spring-boot:run
```

Chờ Flyway báo V21 thành công; nếu tiến trình còn chạy thì Ctrl+C. Không chạy đồng thời instance migration khác. Sau đó chạy API với cấu hình thường, Flyway mặc định false, bỏ profile migrationmain.

Đối chiếu trong database SuncareClinic:

```sql
SELECT DB_NAME() AS database_name;
SELECT version,description,checksum,success
FROM dbo.flyway_schema_history WHERE version='21';
SELECT COUNT(*) AS total, SUM(CASE WHEN is_active=1 THEN 1 ELSE 0 END) AS active_count
FROM dbo.Drug;
SELECT code,name,ingredient,strength,dosage_form,unit,is_active
FROM dbo.Drug WHERE code LIKE 'SCG-%' ORDER BY name,strength;
```

Kiểm tra tên/hoạt chất/mã qua API bằng đúng role, ví dụ `search=amoxicillin`, `search=metformin` và mã SCG trong kết quả. Đây là hướng dẫn chạy main, không phải xác nhận đã chạy main.
# Tách đơn vị liều dùng và cấp phát — V22

Yêu cầu ngày 2026-11-27 thay quyết định dùng chung Drug.unit: thuốc viên vẫn cấp phát theo viên; thuốc lỏng/bôi theo chai/tuýp khi nguồn xác định rõ; chưa thêm quy đổi. V21 và manifest nguồn cũ giữ nguyên.

- Drug thêm dose_unit và dispensing_unit (nvarchar50, nullable để giữ bản ghi chưa rõ). Cột unit cũ giữ để tương thích, không dùng làm đơn vị liều của đơn mới.
- PrescriptionItem thêm hai cột snapshot cùng tên. Không backfill hoặc sửa các dòng đơn cũ; tên/đơn vị/liều/số lượng/rowversion của bản đã phát hành giữ nguyên. API đơn mới lấy hai đơn vị từ Drug dưới khóa, không nhận snapshot do client tự gửi.
- API danh mục nhận/trả doseUnit và dispensingUnit. Quản trị viên cần nhập đủ khi lưu; thuốc đang active nhưng chưa đủ đơn vị sẽ bị chặn kê ở frontend/backend. DRAFT cũ phải mở sửa và lưu lại để xác nhận snapshot mới; ISSUED cũ chỉ đọc thông tin gốc, không suy từ danh mục hiện hành.
- Nguồn đối chiếu là packaging/dosage_form/unit trong scripts/data/outpatient-drugs-openfda.json đã lưu ở V21. scripts/prepare_drug_units.py sinh manifest bổ sung scripts/data/outpatient-drug-units.json, giữ code/product_ndc/URL/bao gói gốc và lý do. Thuốc viên dùng viên/viên theo quyết định chủ dự án; thuốc khác chỉ chọn loại chứa trực tiếp khi nhất quán giữa các bao gói nguồn, bỏ lớp hộp ngoài khi đã có chai/tuýp bên trong. Không chọn ngẫu nhiên dung tích hoặc quy cách, không lưu tỷ lệ quy đổi.
- Kết quả đối chiếu: dose_unit đủ400 (286 viên,67 mL,35 g,12 liều xịt); dispensing_unit đủ389 (286 viên,53 chai,30 tuýp,3 bình,2 hộp,7 gói,1 cốc,4 bình hít,1 túi,2 ống). 11 thuốc còn nhiều loại chứa hoặc chưa đối chiếu được để NULL, cần quản trị viên rà soát với bao gói/URL trong manifest. Không tự đổi trạng thái active hay bịa đơn vị còn thiếu.
- V22 chỉ cập nhật các cột mới của bản ghi nguồn có code/name/ingredient/strength/dosage_form/unit khớp chính xác; không ghi đè thuốc local đã đổi định danh/công thức, không thay trường unit cũ hoặc đơn thuốc đã có. Các bản ghi ngoài manifest chưa được điền đơn vị mới phải được rà soát.
- Liều mỗi lần tiếp tục là số dương theo dose_unit; quantity theo dispensing_unit. Chỉ gợi ý số lượng khi hai đơn vị trùng nhau và tần suất/ngày đủ rõ. mL/chai, g/tuýp, liều xịt/bình không có quy đổi nên bác sĩ nhập quantity; không tự làm tròn bao gói hoặc gợi liều điều trị.

Migration cần dùng: V22__separate_drug_dose_and_dispensing_units.sql. Chỉ chạy bằng Flyway sau kiểm migrationtest; không chạy trực tiếp SQL hoặc sửa V21. GO trong V22 là dấu tách batch SQL Server được Flyway xử lý. Generator từ chối ghi đè SQL có nội dung khác; cập nhật dữ liệu tiếp theo phải dùng version mới.

Hậu kiểm:

```sql
SELECT version,success FROM dbo.flyway_schema_history WHERE version='22';
SELECT dose_unit,dispensing_unit,COUNT(*) total FROM dbo.Drug GROUP BY dose_unit,dispensing_unit;
SELECT code,name,unit,dose_unit,dispensing_unit FROM dbo.Drug
WHERE dose_unit IS NULL OR dispensing_unit IS NULL ORDER BY code;
```

Trạng thái đã chạy thực tế và lệnh kiểm thử ghi tại docs/implementation/PROGRESS.md. Dừng tiến trình migrationmain/DevTools trước khi build nếu chỉ định chuẩn bị migration; chạy migrationmain có chủ đích một lần rồi dùng profile thường. Không dùng profile migrationmain để chạy API hằng ngày.

## V23 — chốt 11 đơn vị cấp phát theo chủ dự án (2026-11-27)

Chủ dự án cung cấp đơn vị cho đúng 11 bản ghi còn trống ở V22. Đây là quyết định danh mục nội bộ, không phải xác nhận quy cách bao gói từ FDA. Manifest bổ sung `backend/scripts/data/outpatient-drug-units-confirmed.json` lưu mã, tên, hoạt chất, hàm lượng, dạng và hai đơn vị; manifest nguồn V21/V22 giữ nguyên.

| Thuốc | Đơn vị cấp phát |
|---|---|
| Acetaminophen 32 mg/mL | chai |
| Albuterol sulfate .63 mg/3mL | ống |
| Aluminum hydroxide + Magnesium hydroxide 40 mg/mL + 40 mg/mL | chai |
| Clindamycin phosphate 10 mg/mL | lọ |
| Cyanocobalamin 0.5 mg, xịt mũi | bình xịt |
| Furosemide 10 mg/mL, uống | chai |
| Ipratropium bromide .5 mg/2.5mL | ống |
| Nitroglycerin 20 mg/g, thuốc mỡ | tuýp |
| Sucralfate 100 mg/mL | chai |
| Sulfamethoxazole + Trimethoprim 40 mg/mL + 8 mg/mL | chai |
| Tretinoin .2 mg/g, kem | tuýp |

`prepare_confirmed_drug_units.py` sinh manifest và `V23__complete_confirmed_drug_dispensing_units.sql`, từ chối ghi đè nội dung khác. V23 chỉ điền dispensing_unit đang NULL khi toàn bộ định danh/công thức/unit cũ/dose_unit khớp chính xác; không ghi đè đơn vị đã chỉnh, không đổi dose_unit/unit/is_active hoặc PrescriptionItem. Đích dữ liệu nguồn sau V23 là 400/400 đủ hai đơn vị. Không xác định dung tích, số liều trong bao gói, tỷ lệ quy đổi hay tự gợi ý lượng cấp phát giữa hai đơn vị khác nhau. Dữ liệu đã tùy chỉnh không khớp sẽ được giữ nguyên và phải hậu kiểm riêng.

