---
inclusion: fileMatch
fileMatchPattern: "frontend/**"
---

# Frontend Architecture — Kotlin/JS + HTML Templates

## Tech Stack

- **Kotlin/JS** — Frontend logic viết bằng Kotlin, compile sang JavaScript
- **HTML Templates** — Tách biệt hoàn toàn khỏi Kotlin code, nằm trong `src/jsMain/resources/templates/`
- **CSS Files** — Obsidian Kinetic design system, nằm trong `src/jsMain/resources/styles/` và `src/jsMain/resources/`
- **Vite** — Bundler + dev server với HMR, `publicDir: 'src/jsMain/resources'`
- **Kotlin Multiplatform shared module** — Chia sẻ data models, DTOs giữa frontend và backend

## Project Structure

```
frontend/
├── build.gradle.kts              # Kotlin/JS plugin config
├── package.json                  # Vite + npm dependencies
├── vite.config.js                # Vite config (publicDir: src/jsMain/resources)
├── index.html                    # SPA entry point (<script src="/frontend.js">)
├── src/jsMain/
│   ├── kotlin/com/assistant/frontend/
│   │   ├── App.kt               # Entry point, router setup
│   │   ├── api/ApiClient.kt     # HTTP client (ktor-client-js), JWT
│   │   ├── router/Router.kt     # Hash-based SPA routing
│   │   ├── components/
│   │   │   ├── Shell.kt         # App shell (sidebar + navbar + content)
│   │   │   ├── Sidebar.kt       # Navigation sidebar
│   │   │   ├── Navbar.kt        # Top navbar
│   │   │   └── AIChatSidebar.kt # AI Chat panel
│   │   ├── pages/               # Page controllers (logic only, NO HTML)
│   │   │   ├── DashboardPage.kt
│   │   │   ├── AnalysisPage.kt
│   │   │   ├── KnowledgeGraphPage.kt
│   │   │   ├── TicketIntelligencePage.kt
│   │   │   ├── IntegrationsPage.kt
│   │   │   ├── UserManagementPage.kt
│   │   │   ├── SettingsPage.kt
│   │   │   ├── LoginPage.kt
│   │   │   └── ProjectSelectPage.kt
│   │   └── charts/              # SVG chart renderers
│   └── resources/
│       ├── master_style.css     # Global Obsidian Kinetic CSS
│       ├── dashboard.css        # Page-specific CSS
│       ├── analysis.css
│       ├── knowledge-graph.css
│       ├── ticket-intelligence.css
│       ├── integrations.css
│       ├── user-management.css
│       ├── styles/
│       │   └── components.css   # Shared component CSS
│       └── templates/           # HTML templates (VIEW layer)
│           ├── dashboard.html
│           ├── analysis.html
│           ├── knowledge-graph.html
│           ├── ticket-intelligence.html
│           ├── integrations.html
│           ├── user-management.html
│           ├── settings.html
│           └── ai-chat-sidebar.html
```

---

## ⛔ QUY TẮC BẮT BUỘC — TÁCH BIỆT HTML VÀ LOGIC

### KHÔNG BAO GIỜ tạo HTML trong Kotlin code

```kotlin
// ❌ CẤM — Không bao giờ viết HTML string trong Kotlin
private fun buildPageHtml(): String = """
    <div class="glass-card">
        <h1>Dashboard</h1>
    </div>
""".trimIndent()

// ❌ CẤM — Không dùng innerHTML với HTML string dài
container.innerHTML = "<div class='glass-card'><h1>Title</h1></div>"

// ❌ CẤM — Không dùng kotlinx.html DSL để generate layout
div { h1 { +"Dashboard" } }
```

### LUÔN LUÔN dùng HTML template files

```kotlin
// ✅ ĐÚNG — Load HTML từ template file
fun render(container: Element) {
    scope.launch {
        val html = ApiClient.loadTemplate("analysis")  // loads /templates/analysis.html
        container.innerHTML = html
        bindEvents()    // Bind click handlers, event listeners
        loadData()      // Fetch API data và populate DOM elements
    }
}

// ✅ ĐÚNG — Populate data qua DOM APIs
private fun renderMetrics(data: AnalysisResponse) {
    document.getElementById("val-total-tickets")?.textContent = "${data.totalTickets}"
    document.getElementById("val-resolution-rate")?.textContent = "${data.resolutionRate}%"
}
```

### Nguyên tắc VIEW / CONTROLLER

| Layer | Nơi đặt | Chứa gì | KHÔNG chứa |
|-------|---------|---------|-------------|
| **VIEW** | `resources/templates/*.html` + `resources/*.css` | HTML structure, CSS classes, layout, placeholders (`id="..."`) | Logic, event handlers, API calls |
| **CONTROLLER** | `kotlin/.../pages/*.kt` | Event binding, API calls, DOM manipulation (`getElementById`, `textContent`, `classList`) | HTML strings, inline styles dài, layout structure |

### Quy tắc cụ thể

1. **Mỗi page PHẢI có HTML template tương ứng** trong `resources/templates/`
2. **Kotlin code chỉ được thao tác DOM** qua `getElementById()`, `querySelector()`, `textContent`, `classList`, `style` (cho dynamic values)
3. **Inline styles trong Kotlin chỉ cho dynamic values** (ví dụ: `bar.style.height = "${percent}%"` cho chart bars). Layout styles phải nằm trong CSS files
4. **Tạo DOM elements động** (ví dụ: list items, chart bars) dùng `document.createElement()` + CSS classes, KHÔNG dùng innerHTML với HTML string
5. **HTML templates dùng `id` attributes** làm hook cho Kotlin code bind data và events
6. **Dynamic repeated elements ƯU TIÊN dùng `<template>` clone pattern** — layout nằm trong HTML, Kotlin chỉ clone và gán data

### `<template>` clone pattern cho dynamic elements

Khi cần tạo nhiều elements cùng cấu trúc (list items, cards, rows, alerts), ƯU TIÊN dùng `<template>` tag trong HTML template file thay vì `document.createElement()` nhiều lần.

```html
<!-- ✅ ĐÚNG — Template trong HTML file (resources/templates/analysis.html) -->
<template id="tmpl-alert">
    <div class="bottleneck-alert">
        <span class="alert-icon"></span>
        <div>
            <h4 class="alert-title"></h4>
            <p class="alert-desc"></p>
            <span class="alert-severity"></span>
        </div>
    </div>
</template>
```

```kotlin
// ✅ ĐÚNG — Kotlin chỉ clone + populate data
private fun createAlertElement(alert: BottleneckAlert): HTMLElement {
    val tmpl = document.getElementById("tmpl-alert") as HTMLTemplateElement
    val el = tmpl.content.firstElementChild!!.cloneNode(true) as HTMLElement
    el.querySelector(".alert-icon")!!.textContent = if (alert.type == "RISK") "⚠️" else "🚀"
    el.querySelector(".alert-title")!!.textContent = alert.title
    el.querySelector(".alert-desc")!!.textContent = alert.description
    el.querySelector(".alert-severity")!!.textContent = alert.severity
    return el
}
```

```kotlin
// ❌ CẤM — innerHTML với HTML string (XSS risk, layout trong Kotlin)
el.innerHTML = """
    <span style="font-size:20px;">$icon</span>
    <div><h4>${HtmlUtils.escapeHtml(alert.title)}</h4></div>
""".trimIndent()
```

Lợi ích:
- **Layout nằm hoàn toàn trong HTML file** — designer có thể sửa mà không đụng Kotlin
- **`textContent` tự động XSS-safe** — không cần gọi `HtmlUtils.escapeHtml()` thủ công
- **CSS classes thay vì inline styles** — dễ maintain, consistent với design system

---

## ⛔ KHÔNG TẠO FILE LEGACY

### KHÔNG tạo thư mục con HTML/CSS/JS trong `frontend/`

```
# ❌ CẤM — Không tạo các thư mục này
frontend/dashboard/index.html
frontend/dashboard/script.js
frontend/dashboard/style.css
frontend/analysis/index.html
frontend/integrations/script.js
```

### Lý do
- Frontend dùng kiến trúc **Kotlin/JS + HTML Templates**
- HTML templates nằm trong `src/jsMain/resources/templates/`
- CSS nằm trong `src/jsMain/resources/` và `src/jsMain/resources/styles/`
- Logic nằm trong `src/jsMain/kotlin/.../pages/`
- **KHÔNG CÓ** thư mục con per-page ở root `frontend/`

### Cấu trúc root `frontend/` chỉ chứa

```
frontend/
├── build.gradle.kts    # Build config
├── index.html          # SPA entry point
├── package.json        # NPM config
├── package-lock.json   # NPM lock
├── vite.config.js      # Vite config
├── webpack.config.d/   # Webpack config
├── src/                # Source code (Kotlin + resources)
├── build/              # Build output (auto-generated)
└── node_modules/       # NPM packages (auto-generated)
```

---

## API Client

- Sử dụng `ktor-client-js` cho HTTP requests
- JWT token lưu trong `sessionStorage`
- Shared data models từ `:shared` module — type-safe end-to-end
- `loadTemplate(name)` — fetch HTML template từ `/templates/$name.html`

## Routing

- Hash-based: `#dashboard`, `#knowledge_graph`, `#analysis`, etc.
- Login và Project Select là standalone pages (không render trong Shell)
- Tất cả pages khác render trong Shell (sidebar + navbar + content)

## Vite Integration

- `publicDir: 'src/jsMain/resources'` — Vite serve static files từ resources
- Dev: `./gradlew :frontend:jsBrowserDevelopmentRun` hoặc `npx vite`
- Build: `./gradlew :frontend:jsBrowserProductionWebpack`
- JS bundle output: `frontend.js` (referenced in index.html as `/frontend.js`)


---

## ⛔ QUY TẮC UX BẮT BUỘC

### Mọi thao tác PHẢI có feedback rõ ràng cho user

| Trạng thái | UI PHẢI hiển thị | Ví dụ |
|-----------|-----------------|-------|
| **Loading** | Spinner hoặc skeleton + text mô tả | "Loading projects...", "Connecting to Jira..." |
| **Empty data** | Message giải thích + hành động gợi ý | "No tickets found. Verify project has issues in Jira." |
| **Error** | Message lỗi cụ thể + hành động khắc phục | "Connection failed. Check Integrations settings." + nút "Go to Integrations" |
| **Success** | Confirmation ngắn gọn | "✓ Settings saved", toast notification |
| **In progress** | Progress bar + label phần trăm + mô tả bước hiện tại | "Scanning... 15/42 — 35%" |
| **No permission** | Message + giải thích role cần thiết | "Access Denied — Administrator role required" |

### KHÔNG BAO GIỜ fail silently

```kotlin
// ❌ CẤM — Fail silently, user không biết gì
} catch (e: Exception) {
    console.log("Error: ${e.message}")
    // User thấy màn hình trống hoặc data cũ
}

// ✅ ĐÚNG — Hiển thị lỗi cho user
} catch (e: Exception) {
    console.log("[PageName] Error: ${e.message}")
    showErrorState("Failed to load data. Please try again.")
}
```

### Empty state PHẢI có hành động

```kotlin
// ❌ CẤM — Chỉ hiện text trống
container.textContent = "No data"

// ✅ ĐÚNG — Giải thích + gợi ý hành động
showEmptyState(
    icon = "📭",
    title = "No tickets found",
    description = "This project has no issues in Jira.",
    actionLabel = "Switch Project",
    actionHandler = { Router.navigateTo("project_select") }
)
```

### Mọi API call PHẢI handle 3 trạng thái

Mỗi API call trong page controller PHẢI xử lý:
1. **Loading state** — hiển thị trước khi gọi API
2. **Success state** — hiển thị data hoặc confirmation
3. **Error state** — hiển thị message lỗi cụ thể với hành động khắc phục

### Scan/Long operation PHẢI có thông tin chi tiết

Khi scan hoặc operation dài hoàn tất với kết quả bất thường (0 items, partial failure):
- Hiển thị message giải thích nguyên nhân
- Hiển thị scan log với chi tiết
- Gợi ý hành động tiếp theo (switch project, check config, retry)

### Toast notifications

- Success: tự biến mất sau 3 giây
- Error: giữ lại cho đến khi user dismiss
- Warning: giữ lại 5 giây
- Mỗi toast PHẢI có icon + message rõ ràng

---

## ⛔ QUY TẮC BẮT BUỘC — BLOCKING OVERLAY CHO ASYNC OPERATIONS

### Mọi async operation PHẢI có BlockingOverlay

Bất kỳ thao tác nào gọi API (SAVE, TEST, DELETE, START, STOP, ANALYZE, SCAN, v.v.) PHẢI sử dụng `BlockingOverlay` để:
1. Chặn user tương tác trong khi đang xử lý (ngăn double-click)
2. Hiển thị spinner + message mô tả hành động
3. Tự động gỡ overlay khi hoàn tất (kể cả khi lỗi)

### Cách sử dụng

```kotlin
// ✅ ĐÚNG — BlockingOverlay bao quanh async operation
import com.assistant.frontend.components.BlockingOverlay

private fun save() {
    BlockingOverlay.show("container-id", "Saving...")
    scope.launch {
        try {
            val resp = ApiClient.post("/api/...", data)
            // handle success
        } catch (e: Exception) {
            // handle error
        } finally {
            BlockingOverlay.remove("container-id")  // LUÔN trong finally
        }
    }
}
```

```kotlin
// ❌ CẤM — Async operation KHÔNG có blocking overlay
private fun save() {
    scope.launch {
        val resp = ApiClient.post("/api/...", data)
        // User có thể click SAVE nhiều lần → duplicate requests
    }
}
```

### Quy tắc cụ thể

1. **`BlockingOverlay.show()` PHẢI được gọi TRƯỚC `scope.launch`** — đảm bảo overlay hiển thị ngay lập tức
2. **`BlockingOverlay.remove()` PHẢI nằm trong block `finally`** — đảm bảo overlay luôn được gỡ
3. **Container element PHẢI có `position: relative`** — để overlay position absolute hoạt động đúng
4. **Message PHẢI mô tả hành động cụ thể** — "Saving...", "Testing connection...", "Removing...", "Analyzing...", KHÔNG dùng message chung chung như "Please wait"
5. **Áp dụng cho TẤT CẢ các nơi có async operation** — modals, cards, forms, sidebars

### Danh sách operations BẮT BUỘC có BlockingOverlay

| Operation | Container | Message |
|-----------|-----------|---------|
| Save config (bất kỳ modal nào) | Modal content div | "Saving..." |
| Test connection | Modal content div hoặc card | "Testing connection..." |
| Delete/Remove | Card hoặc list item | "Removing..." |
| Start/Stop server | Card | "Starting..." / "Stopping..." |
| Analyze ticket | Analysis container | "Analyzing..." |
| Scan project | Scan container | "Starting scan..." |
| Login | Login form | "Signing in..." |
| Upload file | Upload area | "Uploading..." |


---

## ⛔ QUY TẮC THỰC HIỆN SONG SONG

### Khi có thể, thực hiện song song requirements + design + implementation

Khi user yêu cầu thay đổi UI/UX hoặc feature nhỏ (không cần spec workflow đầy đủ):
1. Cập nhật requirements document (nếu cần)
2. Cập nhật design document (nếu cần)
3. Thực hiện implementation
4. Tất cả 3 bước trên PHẢI được thực hiện trong cùng 1 lượt, KHÔNG chờ user confirm từng bước

### Khi nào áp dụng
- Bug fixes
- UX improvements (layout, sorting, paging, responsive)
- CSS/styling changes
- Thêm/sửa component nhỏ (checkbox, button, modal)
- Refactor code structure

### Khi nào KHÔNG áp dụng (cần spec workflow)
- Feature mới lớn (MCP Runtime, Batch Scan Engine, AI Chat)
- Thay đổi kiến trúc (database schema, API design)
- Thay đổi ảnh hưởng nhiều module


---

## ⛔ QUY TẮC BẮT BUỘC — BROWSER MEMORY MANAGEMENT

### Dữ liệu tích lũy (logs, lists, polling results) PHẢI được quản lý memory

Khi frontend tích lũy dữ liệu qua polling hoặc append (scan logs, chat history, notification lists), PHẢI tuân thủ:

1. **Dedup IDs lưu trong sessionStorage** — KHÔNG giữ Set/Map lớn trong JS heap
2. **Cap DOM nodes** — Giới hạn số DOM elements hiển thị (max 500 cho logs, max 100 cho lists). Khi vượt, xóa nodes cũ nhất
3. **Cap storage size** — Giới hạn số IDs lưu trong sessionStorage (max 2000). Khi vượt, chỉ giữ entries gần nhất
4. **Reset khi bắt đầu mới** — Khi user bắt đầu operation mới (START SCAN, new conversation), xóa cả sessionStorage key và DOM nodes

```kotlin
// ❌ CẤM — Tích lũy vô hạn trong memory
private val renderedIds = mutableSetOf<Long>() // grows forever
for (entry in entries) {
    renderedIds.add(entry.id)
    container.appendChild(createLine(entry)) // DOM grows forever
}

// ✅ ĐÚNG — SessionStorage + DOM cap
private const val STORAGE_KEY = "scanlog_rendered_ids"
private const val MAX_VISIBLE = 500

fun render(entries: List<ScanLogEntryDTO>) {
    val seen = loadFromSessionStorage(STORAGE_KEY)
    for (entry in entries) {
        if (entry.id in seen) continue
        seen.add(entry.id)
        container.appendChild(createLine(entry))
    }
    trimOldNodes(container, MAX_VISIBLE)
    saveToSessionStorage(STORAGE_KEY, seen.takeLast(2000))
}
```

### Quy tắc cụ thể

| Loại dữ liệu | Storage | DOM Cap | ID Cap | Reset khi |
|--------------|---------|---------|--------|-----------|
| Scan log entries | sessionStorage | 500 nodes | 2000 IDs | START SCAN |
| Chat messages | sessionStorage | 200 nodes | 500 IDs | New conversation |
| Notification list | memory (nhỏ) | 50 nodes | N/A | Page navigate |
| Polling results | không lưu | N/A | N/A | Mỗi poll replace |

### Tại sao không dùng localStorage?
- `sessionStorage` tự xóa khi đóng tab → không tích lũy data cũ
- `localStorage` persist vĩnh viễn → cần cleanup logic phức tạp
- Scan log chỉ relevant cho session hiện tại → sessionStorage phù hợp hơn


---

## ⛔ QUY TẮC BẮT BUỘC — NATIVE FORM ELEMENTS ON DARK THEME

### Browser native elements (`<select>`, `<option>`, `<input>`) PHẢI có dark theme override

Browser native form elements sử dụng OS theme mặc định (thường là light). Trên Obsidian Kinetic dark theme, chúng sẽ hiện nền trắng + text đen → không đọc được.

```html
<!-- ❌ CẤM — Native select không có dark override -->
<select style="padding:8px;">
    <option value="1">Option 1</option>
</select>
<!-- Kết quả: nền trắng, text đen, dropdown options trắng → không thấy gì -->

<!-- ✅ ĐÚNG — Explicit dark background + light text -->
<select style="background:rgba(12,14,22,0.95);color:var(--primary);border:1px solid var(--glass-border);border-radius:6px;-webkit-appearance:none;appearance:none;">
    <option value="1">Option 1</option>
</select>
```

### Quy tắc cụ thể

1. **Mọi `<select>` element** PHẢI có `background: rgba(12,14,22,0.95)` và `color: var(--text-main)` hoặc `var(--primary)`
2. **Mọi `<option>` element** PHẢI inherit dark background từ parent `<select>` — đã có global CSS rule trong `components.css`
3. **`-webkit-appearance: none; appearance: none;`** — bỏ native styling để custom arrow icon
4. **Custom dropdown arrow** — dùng SVG background-image với fill color matching theme
5. **Focus state** — `border-color: var(--primary)` + subtle glow
6. **`<input>` elements** — đã có class `.field-input` trong design system, LUÔN dùng class này thay vì bare `<input>`
7. **KHÔNG BAO GIỜ** dùng `background: rgba(255,255,255,0.04)` cho native `<select>` — browser sẽ override với OS theme cho dropdown popup

### Checklist khi thêm form elements

- [ ] `<select>` có explicit dark background?
- [ ] `<option>` elements readable trên dark background?
- [ ] Focus state có neon glow?
- [ ] Custom appearance (no native OS styling)?
- [ ] Text color contrast đủ trên dark background?
