# Yêu cầu: Serenity Report Feature Hierarchy

## Tổng quan

Tổ chức lại cấu trúc thư mục feature files E2E test từ dạng phẳng sang phân cấp để kích hoạt Requirements Hierarchy trong Serenity BDD report.

## Yêu cầu

### 1. Tạo cấu trúc thư mục phân cấp

#### 1.1 Tạo 9 thư mục con theo domain
- **Bắt buộc**: Tạo các thư mục: `core/`, `dashboard/`, `analysis/`, `knowledge-graph/`, `integrations/`, `user-management/`, `settings/`, `ai-chat/`, `scanning/` trong `e2e-tests/src/test/resources/features/`

**Tiêu chí chấp nhận**:
- Chính xác 9 thư mục con tồn tại trong `features/`
- Tên thư mục khớp chính xác với danh sách trên (kebab-case)

#### 1.2 Di chuyển 15 feature files vào đúng thư mục con
- **Bắt buộc**: Di chuyển (move, không copy) mỗi file `.feature` vào thư mục con tương ứng theo bảng ánh xạ trong design document

**Tiêu chí chấp nhận**:
- Tất cả 15 file `.feature` tồn tại trong thư mục con đúng
- Không còn file `.feature` nào ở cấp `features/` gốc
- Tổng số file `.feature` trong toàn bộ cây thư mục = 15

#### 1.3 Bảo toàn nội dung feature files
- **Bắt buộc**: Nội dung (scenarios, steps, tags) của mỗi file không được thay đổi

**Tiêu chí chấp nhận**:
- Nội dung mỗi file sau khi di chuyển giống hệt trước khi di chuyển
- Không thêm, xóa, hoặc sửa bất kỳ dòng nào trong file `.feature`

### 2. Cập nhật Runner Classes

#### 2.1 Migrate 13 UI runner classes sang JUnit 5 và thêm @Feature annotation
- **Bắt buộc**: Migrate từ JUnit 4 `@RunWith(CucumberWithSerenity)` + `@CucumberOptions` sang JUnit 5 `@Suite` + `@IncludeEngines("cucumber")` + `@SelectClasspathResource`
- **Tùy chọn**: Thêm `@net.serenitybdd.annotations.Feature("<domain-name>")` annotation lên mỗi runner class. Lưu ý: annotation này hiện **không có tác dụng** với Cucumber Platform Engine — Serenity chỉ đọc `@Feature` khi dùng `SerenityJUnit5Extension` (JUnit 5 native tests). Giữ lại để tương thích nếu Serenity hỗ trợ trong tương lai.

**Tiêu chí chấp nhận**:
- 13 runner classes dùng JUnit 5 `@Suite` + `@IncludeEngines("cucumber")` (không còn deprecated `@RunWith(CucumberWithSerenity)`)
- Mỗi runner có `@SelectClasspathResource` trỏ đến đúng feature file
- Compile thành công, không có deprecation warnings từ Cucumber/Serenity

#### 2.2 Migrate CucumberTestRunner và ApiTestRunner sang JUnit 5
- **Bắt buộc**: Cả hai root runners cũng phải migrate sang JUnit 5 `@Suite` pattern

**Tiêu chí chấp nhận**:
- `CucumberTestRunner.kt` dùng `@Suite` + `@SelectClasspathResource("/features")` + filter `not @api and not @ui`
- `ApiTestRunner.kt` dùng `@Suite` + `@SelectClasspathResource("/features")` + filter `@api`
- Cucumber scan đệ quy tìm được tất cả 15 feature files

#### 2.3 Runner classes compile thành công
- **Bắt buộc**: Tất cả runner classes phải compile thành công sau khi cập nhật

**Tiêu chí chấp nhận**:
- `./gradlew :e2e-tests:compileTestKotlin` thành công (exit code 0)
- Không có deprecation warnings liên quan đến `CucumberWithSerenity` hoặc `CucumberOptions`

### 3. Cấu hình Serenity Properties

#### 3.1 Thêm requirement types
- **Bắt buộc**: Thêm `serenity.requirement.types=capability,feature` vào `serenity.properties`

**Tiêu chí chấp nhận**:
- File `serenity.properties` chứa dòng `serenity.requirement.types=capability,feature`
- Dòng mới nằm gần dòng `serenity.features.directory` (cùng nhóm cấu hình)

#### 3.2 Giữ nguyên cấu hình hiện có
- **Bắt buộc**: Các cấu hình khác trong `serenity.properties` không được thay đổi

**Tiêu chí chấp nhận**:
- `serenity.features.directory=src/test/resources/features` giữ nguyên
- Tất cả cấu hình khác (screenshots, browser, parallel) giữ nguyên

### 4. Serenity Report Hierarchy

#### 4.1 Report hiển thị Requirements Hierarchy
- **Bắt buộc**: Serenity HTML report phải hiển thị cấu trúc phân cấp Capability → Feature → Scenario trong tab "Requirements"

**Tiêu chí chấp nhận**:
- Tab "Requirements" có cấu trúc phân cấp từ Cucumber directory hierarchy
- 9 Capabilities hiển thị tương ứng với 9 thư mục con
- Mỗi Capability chứa đúng số Features theo bảng ánh xạ

#### 4.2 Tab "Features" và "Stories" hiển thị data từ directory hierarchy
- **Bắt buộc**: Tab "Features" trong Serenity report phải hiển thị danh sách features (từ `.feature` files) với test counts. Yêu cầu `requirementsBaseDir` và `serenity.features.directory` dùng absolute path trong `build.gradle.kts`.

**Tiêu chí chấp nhận**:
- Tab "Features" hiển thị 15 features tương ứng 15 file `.feature`
- Mỗi feature hiển thị số test cases, scenarios, và % pass

#### 4.3 Tổng số scenarios không thay đổi
- **Bắt buộc**: Tổng số scenarios trong report phải bằng tổng số trước khi tổ chức lại

**Tiêu chí chấp nhận**:
- Không mất scenario nào trong report
- Không có scenario trùng lặp

### 5. Build và Test Compatibility

#### 5.1 Gradle build thành công
- **Bắt buộc**: Build E2E tests module phải thành công

**Tiêu chí chấp nhận**:
- `./gradlew :e2e-tests:compileTestKotlin` thành công
- Không có warning liên quan đến feature file paths

#### 5.2 Serenity report generation thành công
- **Bắt buộc**: Serenity aggregate task phải tạo report thành công

**Tiêu chí chấp nhận**:
- `./gradlew :e2e-tests:aggregate` thành công
- HTML report được tạo trong `target/site/serenity/`
