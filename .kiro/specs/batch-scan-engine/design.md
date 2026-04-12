# Batch Scan Engine — Design

# Batch Scan Engine — Thiết kế Chi tiết

## Tổng quan

Batch Scan Engine là thành phần backend trong shared module, chịu trách nhiệm điều phối quá trình quét hàng loạt ticket trong một dự án Jira. Engine sử dụng coroutine-based orchestration, quản lý trạng thái quét per-project qua state machine, và tích hợp với `AIOrchestrator` (KB-First strategy) và `KBRepository` để lưu kết quả phân tích.

## State Machine

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> SCANNING : START
    SCANNING --> PAUSED : PAUSE
    SCANNING --> COMPLETED : All tickets processed
    SCANNING --> CANCELLED : CANCEL
    SCANNING --> PAUSED : Server restart recovery
    PAUSED --> SCANNING : RESUME
    PAUSED --> CANCELLED : CANCEL
    CANCELLED --> IDLE : START (new scan)
    COMPLETED --> IDLE : START (new scan)
```

Trạng thái hợp lệ:
- **IDLE**: Chưa có scan nào hoặc scan trước đã reset
- **SCANNING**: Đang quét tuần tự từng ticket
- **PAUSED**: Tạm dừng, lưu vị trí hiện tại để RESUME
- **COMPLETED**: Đã quét xong toàn bộ ticket
- **CANCELLED**: Đã hủy, giữ lại kết quả đã phân tích

## Kiến trúc

```mermaid
graph TB
    subgraph "BatchScanEngine (shared module)"
        BSE[BatchScanEngine]
        SSR[ScanStateRepository]
        SLR[ScanLogRepository]
    end

    subgraph "Existing Components"
        AI[AIOrchestrator<br>KB-First strategy]
        KB[KBRepository]
        JIRA[JiraClient<br>factory pattern]
        FNM[FeatureNetworkMapper]
    end

    subgraph "Storage"
        DB[(SQLite<br>scan_states + scan_log)]
    end

    BSE --> AI
    BSE --> KB
    BSE --> JIRA
    BSE --> FNM
    BSE --> SSR
    BSE --> SLR
    SSR --> DB
    SLR --> DB
```

## Interface & Class Signatures

### ScanStatus Enum

```kotlin
// shared/.../scan/ScanStatus.kt
@Serializable
enum class ScanStatus {
    IDLE, SCANNING, PAUSED, COMPLETED, CANCELLED
}
```

### ScanState Data Class

```kotlin
// shared/.../scan/ScanState.kt
@Serializable
data class ScanState(
    val projectKey: String,
    val status: ScanStatus,
    val totalTickets: Int,
    val processedCount: Int,
    val currentTicketId: String?,
    val ticketIds: List<String>,       // Toàn bộ ticket IDs cần quét
    val startedAt: String,             // ISO-8601
    val updatedAt: String              // ISO-8601
) {
    val progressPercent: Int
        get() = if (totalTickets > 0) ((processedCount.toDouble() / totalTickets) * 100).toInt() else 0
}
```

### ScanLogEntry Data Class

```kotlin
// shared/.../scan/ScanLogEntry.kt
@Serializable
data class ScanLogEntry(
    val id: Long = 0,
    val projectKey: String,
    val ticketId: String,
    val status: ScanLogStatus,         // COMPLETED, FAILED
    val message: String,
    val timestamp: String              // ISO-8601
)

@Serializable
enum class ScanLogStatus {
    ANALYZING, COMPLETED, FAILED
}
```

### ScanStateRepository Interface

```kotlin
// shared/.../scan/ScanStateRepository.kt
interface ScanStateRepository {
    suspend fun findByProjectKey(projectKey: String): ScanState?
    suspend fun save(state: ScanState): Boolean
    suspend fun delete(projectKey: String): Boolean
}
```

### ScanLogRepository Interface

```kotlin
// shared/.../scan/ScanLogRepository.kt
interface ScanLogRepository {
    suspend fun addEntry(entry: ScanLogEntry): Boolean
    suspend fun getByProjectKey(projectKey: String, limit: Int = 50): List<ScanLogEntry>
    suspend fun deleteByProjectKey(projectKey: String): Boolean
}
```

### BatchScanEngine Class

```kotlin
// shared/.../scan/BatchScanEngine.kt
class BatchScanEngine(
    private val aiOrchestrator: AIOrchestrator,
    private val kbRepository: KBRepository,
    private val jiraClientProvider: () -> JiraClient,
    private val featureNetworkMapper: FeatureNetworkMapper,
    private val scanStateRepository: ScanStateRepository,
    private val scanLogRepository: ScanLogRepository
) {
    // Active scan jobs per project — max 1 concurrent scan per project
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /**
     * Start a new scan for the given project.
     * @throws ConflictException if a scan is already running for this project
     */
    suspend fun startScan(projectKey: String): ScanState

    /**
     * Pause an active scan. Saves current position to DB.
     */
    suspend fun pauseScan(projectKey: String): ScanState

    /**
     * Resume a paused scan from the last saved position.
     */
    suspend fun resumeScan(projectKey: String): ScanState

    /**
     * Cancel an active or paused scan. Keeps analyzed results.
     */
    suspend fun cancelScan(projectKey: String): ScanState

    /**
     * Get current scan status for a project.
     */
    suspend fun getStatus(projectKey: String): ScanState

    /**
     * Get scan log entries for a project.
     */
    suspend fun getLog(projectKey: String, limit: Int = 50): List<ScanLogEntry>

    /**
     * Recovery on server restart: transition SCANNING → PAUSED.
     * Called during application startup.
     */
    suspend fun recoverOnStartup()

    // --- Internal ---

    /**
     * Core scan loop: iterates through ticket list starting from offset.
     * Runs in a coroutine, checks for cancellation between tickets.
     */
    private suspend fun scanLoop(projectKey: String, startIndex: Int)

    /**
     * Process a single ticket: call AIOrchestrator.analyzeTicket(),
     * update KB, update FeatureNetworkMapper, log result.
     * On error: log failure, skip ticket, continue.
     */
    private suspend fun processTicket(projectKey: String, ticketId: String): Boolean
}
```

## Luồng xử lý chi tiết

### START Scan

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant API as ScanRoutes
    participant BSE as BatchScanEngine
    participant JIRA as JiraClient
    participant DB as ScanStateRepo
    participant AI as AIOrchestrator
    participant KB as KBRepository
    participant FNM as FeatureNetworkMapper

    Client->>API: POST /api/projects/{key}/scan/start
    API->>BSE: startScan(projectKey)
    BSE->>DB: findByProjectKey(key)
    alt Already SCANNING
        BSE-->>API: 409 Conflict
    else IDLE/COMPLETED/CANCELLED
        BSE->>JIRA: getIssues(projectKey)
        JIRA-->>BSE: List<JiraIssue>
        BSE->>DB: save(ScanState(SCANNING, ticketIds))
        BSE-->>API: ScanState(SCANNING)
        API-->>Client: 200 OK + ScanState

        Note over BSE: Coroutine scan loop starts
        loop For each ticket
            BSE->>AI: analyzeTicket(ticketId, forceReanalyze=false)
            alt KB Hit (already analyzed)
                AI-->>BSE: result (KB_CACHE)
            else KB Miss
                AI-->>BSE: result (FRESH_AI)
            end
            BSE->>KB: save(result)
            BSE->>FNM: updateNetwork(ticketId, result)
            BSE->>DB: save(ScanState(processedCount++))
        end
        BSE->>DB: save(ScanState(COMPLETED))
    end
```

### PAUSE / RESUME Flow

```mermaid
sequenceDiagram
    participant Client as Frontend
    participant BSE as BatchScanEngine
    participant DB as ScanStateRepo

    Note over Client,DB: PAUSE
    Client->>BSE: pauseScan(projectKey)
    BSE->>BSE: activeJobs[key].cancel()
    BSE->>DB: save(ScanState(PAUSED, currentIndex))
    BSE-->>Client: ScanState(PAUSED)

    Note over Client,DB: RESUME
    Client->>BSE: resumeScan(projectKey)
    BSE->>DB: findByProjectKey(key)
    DB-->>BSE: ScanState(PAUSED, processedCount=N)
    BSE->>DB: save(ScanState(SCANNING))
    BSE->>BSE: launch scanLoop(key, startIndex=N)
    BSE-->>Client: ScanState(SCANNING)
```

## Concurrency Control

- `ConcurrentHashMap<String, Job>` lưu coroutine Job cho mỗi project đang scan
- Trước khi start: kiểm tra `activeJobs[projectKey]?.isActive` → nếu true, trả 409 Conflict
- Pause: `activeJobs[projectKey]?.cancel()` → coroutine kiểm tra `isActive` giữa mỗi ticket
- Cancel: tương tự Pause nhưng set status = CANCELLED
- Max 1 scan per project, nhiều project có thể scan đồng thời

## KB-First Strategy trong Batch Scan

Engine sử dụng cùng chiến lược KB-First của `AIOrchestrator`:
- Gọi `analyzeTicket(ticketId, forceReanalyze=false)` → AIOrchestrator tự kiểm tra KB trước
- Ticket đã có trong KB → trả về ngay (KB_CACHE), không gọi AI
- Ticket chưa có → gọi AI agent, lưu vào KB
- Kết quả: scan nhanh hơn cho các ticket đã phân tích trước đó

## Error Handling

| Tình huống | Hành vi |
|---|---|
| Ticket phân tích lỗi (AI timeout, parse error) | Log FAILED vào scan_log, skip ticket, tiếp tục ticket tiếp theo |
| JiraClient lỗi khi lấy danh sách ticket | Trả về error 502, không tạo scan |
| DB write lỗi khi lưu ScanState | Retry 3 lần (pattern từ KBRepositoryImpl), log error |
| Server restart khi đang SCANNING | `recoverOnStartup()` chuyển SCANNING → PAUSED |
| Start scan khi đã có scan đang chạy | Trả về 409 Conflict |

## Server Restart Recovery

Khi server khởi động, `BatchScanEngine.recoverOnStartup()` được gọi:

```kotlin
suspend fun recoverOnStartup() {
    // Query all scan_states with status = 'SCANNING'
    // Update each to PAUSED
    // User can manually RESUME later
}
```

Đăng ký trong Koin module hoặc Application startup:

```kotlin
// ServerModule.kt hoặc Application.module()
val batchScanEngine = get<BatchScanEngine>()
runBlocking { batchScanEngine.recoverOnStartup() }
```

*(Validates: Req 18.1–18.6, 18.13–18.17)*

---

## Multi-Project Scan Visibility

### Tổng quan

Mở rộng Batch Scan Engine để hỗ trợ hiển thị tiến trình quét đồng thời nhiều project. Backend cung cấp endpoint tổng hợp `GET /api/scan/active`, frontend hiển thị stacked progress bars và xử lý graceful 409 Conflict.

### Kiến trúc

```mermaid
graph TB
    subgraph "Frontend (Dashboard)"
        DSC[DashboardScanControl]
        MPSC[MultiProjectScanControl<br>stacked progress bars]
    end

    subgraph "Backend (Ktor)"
        SR[ScanRoutes<br>GET /api/scan/active]
    end

    subgraph "Shared Module"
        BSE[BatchScanEngine]
        SSR[ScanStateRepository<br>+ findAllActive]
    end

    subgraph "Storage"
        DB[(SQLite<br>scan_states)]
    end

    DSC --> MPSC
    DSC -->|POST start → 409| SR
    MPSC -->|GET /api/scan/active<br>poll 5s| SR
    SR --> BSE
    BSE --> SSR
    SSR --> DB
```

### Backend: `GET /api/scan/active` Endpoint

Endpoint mới nằm ngoài route `/api/projects/{key}/scan` vì nó trả về data cross-project.

```kotlin
// server/.../routes/ScanRoutes.kt — thêm vào scanRoutes()
route("/api/scan") {
    withPermission(Permission.VIEW_ANALYSIS) {  // Reader+
        get("/active") {
            val activeScans = batchScanEngine.getActiveScans()
            call.respond(HttpStatusCode.OK, activeScans.map { it.toResponse() })
        }
    }
}
```

**Response format:**

```kotlin
// Response: List<ScanStatusResponse>
// Mỗi entry chứa: projectKey, status, totalTickets, processedCount, progressPercent
// Chỉ trả về projects có status = SCANNING
```

### Backend: `ScanStateRepository.findAllActive()`

Thêm method mới vào interface và implementation. Tái sử dụng `findAllScanning()` đã có — rename hoặc alias.

```kotlin
// shared/.../scan/ScanStateRepository.kt
interface ScanStateRepository {
    // ... existing methods ...
    suspend fun findAllScanning(): List<ScanState>  // ĐÃ CÓ — dùng cho cả recovery và active scans
}
```

`findAllScanning()` đã tồn tại trong codebase (dùng cho `recoverOnStartup()`). Endpoint `GET /api/scan/active` sẽ gọi trực tiếp method này qua `BatchScanEngine`.

### Backend: `BatchScanEngine.getActiveScans()`

```kotlin
// shared/.../scan/BatchScanEngine.kt — thêm method mới
/**
 * Get all projects with active (SCANNING) scans.
 * Used by GET /api/scan/active endpoint.
 */
suspend fun getActiveScans(): List<ScanState> {
    return scanStateRepository.findAllScanning()
}
```

### Frontend: Multi-Project Progress Bar Architecture

```mermaid
sequenceDiagram
    participant User
    participant DSC as DashboardScanControl
    participant MPSC as MultiProjectScanControl
    participant API as GET /api/scan/active

    User->>DSC: Click START SCAN
    DSC->>API: POST /api/projects/{key}/scan/start
    alt 200 OK
        DSC->>MPSC: addProgressBar(scanState)
        MPSC->>MPSC: startActivePolling()
    else 409 Conflict
        DSC->>API: GET /api/scan/active
        API-->>DSC: List<ScanStatusResponse>
        DSC->>MPSC: renderAllProgressBars(activeScans)
        MPSC->>MPSC: startActivePolling()
    end

    loop Mỗi 5 giây (khi có ≥1 active scan)
        MPSC->>API: GET /api/scan/active
        API-->>MPSC: List<ScanStatusResponse>
        MPSC->>MPSC: updateAllProgressBars()
        alt Không còn active scan
            MPSC->>MPSC: stopActivePolling()
        end
    end
```

### Frontend: Stacked Progress Bars Layout

Thêm container `#multi-scan-progress` vào `dashboard.html` bên trong scan control panel:

```html
<!-- Trong scan-control-panel, sau progress bar hiện tại -->
<div id="multi-scan-progress" style="display: none; margin-top: 12px;">
    <!-- Dynamic: mỗi active scan 1 progress bar -->
    <!-- Template per scan:
    <div class="scan-progress-item" data-project="{KEY}">
        <div class="scan-progress-item-label">
            [{PROJECT_KEY}] Scanning... {processed}/{total} — {percent}%
        </div>
        <div class="neural-loader" style="height: 4px;">
            <div class="neural-progress" style="width: {percent}%;"></div>
        </div>
    </div>
    -->
</div>
```

### Frontend: `DashboardMultiScanProgress.kt`

File mới tách biệt logic multi-project scan (tuân thủ SRP, ≤200 dòng):

```kotlin
// frontend/.../pages/dashboard/DashboardMultiScanProgress.kt
internal object DashboardMultiScanProgress {
    private var activePollingJob: Job? = null

    /** Render stacked progress bars cho tất cả active scans */
    fun renderActiveScans(scans: List<ScanStatusResponse>)

    /** Start polling GET /api/scan/active mỗi 5s */
    fun startActivePolling()

    /** Stop polling khi không còn active scan */
    fun stopActivePolling()

    /** Format label: "[{KEY}] Scanning... {processed}/{total} — {percent}%" */
    fun formatProgressLabel(scan: ScanStatusResponse): String
}
```

### Frontend: 409 Conflict Graceful Handling

Cập nhật `DashboardScanControl.scanAction("start")`:

```kotlin
// Trong scanAction("start"):
val response = ApiClient.post("/api/projects/$projectKey/scan/start")
when (response.status.value) {
    200 -> {
        val status = json.decodeFromString<ScanStatusResponse>(body)
        updateScanUI(status)
        DashboardMultiScanProgress.startActivePolling()
    }
    409 -> {
        // Seamless UX: fetch active scans và hiển thị progress
        val activeResponse = ApiClient.get("/api/scan/active")
        val activeScans = json.decodeFromString<List<ScanStatusResponse>>(activeBody)
        DashboardMultiScanProgress.renderActiveScans(activeScans)
        DashboardMultiScanProgress.startActivePolling()
        // KHÔNG hiển thị error — user thấy progress bar ngay lập tức
    }
}
```

### Polling Strategy

| Aspect | Giá trị |
|--------|---------|
| Endpoint | `GET /api/scan/active` |
| Interval | 5 giây |
| Start condition | ≥1 active scan (sau start thành công hoặc 409) |
| Stop condition | Response trả về empty list (không còn SCANNING) |
| Scope | `DashboardPage.scope` — auto-cancel khi navigate away |
| Cleanup | `DashboardPage.cleanup()` gọi `DashboardMultiScanProgress.stopActivePolling()` |

### Tương tác với Polling hiện tại

Hiện tại `DashboardScanControl` poll `GET /api/projects/{key}/scan/status` mỗi 3s cho project hiện tại. Với multi-project:

- **Single-project polling (3s)**: Giữ nguyên cho scan controls (PAUSE/RESUME/CANCEL) của project hiện tại
- **Multi-project polling (5s)**: Thêm mới cho stacked progress bars — hiển thị tất cả active scans
- Khi project hiện tại đang scan: cả 2 polling chạy song song (không conflict vì update DOM elements khác nhau)

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Active scans filter chỉ trả về SCANNING states

*For any* tập hợp ScanState objects với các status khác nhau (IDLE, SCANNING, PAUSED, COMPLETED, CANCELLED) được lưu trong repository, `findAllScanning()` SHALL chỉ trả về các ScanState có status = SCANNING, và mỗi entry trong kết quả phải chứa đầy đủ: projectKey, status, totalTickets, processedCount, progressPercent.

**Validates: Requirements 18.18**

### Property 2: Progress label format chứa đầy đủ thông tin

*For any* ScanStatusResponse với projectKey không rỗng, processedCount ≥ 0, totalTickets > 0, và progressPercent trong [0, 100], `formatProgressLabel()` SHALL trả về string chứa projectKey, processedCount, totalTickets, và progressPercent theo format "[{PROJECT_KEY}] Scanning... {processed}/{total} — {percent}%".

**Validates: Requirements 18.19**

## Error Handling (Multi-Project Scan Visibility)

| Tình huống | Hành vi |
|---|---|
| `GET /api/scan/active` trả lỗi 500 | Frontend log error, giữ progress bars hiện tại, retry ở poll tiếp theo |
| `POST start` trả 409 Conflict | Frontend fetch active scans, hiển thị progress bar seamlessly |
| Polling response trả empty list | Stop polling, ẩn multi-scan container |
| Network error khi polling | Log error, tiếp tục polling (không stop) — retry ở interval tiếp theo |
| JWT expired khi polling | `handleUnauthorized()` redirect về login, stop polling |

## Testing Strategy (Multi-Project Scan Visibility)

### Unit Tests (Example-based)
- `GET /api/scan/active` trả về đúng format khi có 0, 1, 3 active scans
- 409 Conflict handler: verify frontend gọi `GET /api/scan/active` thay vì hiển thị error
- Polling start/stop: verify polling bắt đầu khi có active scans, dừng khi empty

### Property Tests
- **Property 1**: `findAllScanning()` filter — fast-check với random ScanState sets, min 100 iterations
  - Tag: **Feature: multi-project-scan-visibility, Property 1: Active scans filter chỉ trả về SCANNING states**
- **Property 2**: `formatProgressLabel()` format — fast-check với random ScanStatusResponse, min 100 iterations
  - Tag: **Feature: multi-project-scan-visibility, Property 2: Progress label format chứa đầy đủ thông tin**

### E2E Tests
- API test: `GET /api/scan/active` với JWT Reader+ → 200, không JWT → 401
- UI test: Stacked progress bars hiển thị khi nhiều scans active

*(Validates: Req 18.18–18.22)*

---

# Scan API Routes — Thiết kế Chi tiết

## Tổng quan

Thiết kế các REST API endpoints cho Batch Scan Engine, bao gồm request/response DTOs, RBAC, và error handling.

## Route Table

| Method | Endpoint | Mô tả | RBAC |
|---|---|---|---|
| `POST` | `/api/projects/{key}/scan/start` | Khởi động quét hàng loạt | Administrator, Neural_Architect |
| `POST` | `/api/projects/{key}/scan/pause` | Tạm dừng quét | Administrator, Neural_Architect |
| `POST` | `/api/projects/{key}/scan/resume` | Tiếp tục quét đã tạm dừng | Administrator, Neural_Architect |
| `POST` | `/api/projects/{key}/scan/cancel` | Hủy bỏ quét | Administrator, Neural_Architect |
| `GET` | `/api/projects/{key}/scan/status` | Truy vấn trạng thái quét | Reader+ |
| `GET` | `/api/projects/{key}/scan/log` | Truy vấn log chi tiết | Reader+ |

## Sequence Diagram

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant JWT as JWT Middleware
    participant RBAC as RBAC Middleware
    participant SR as ScanRoutes
    participant BSE as BatchScanEngine

    FE->>JWT: POST /api/projects/PROJ/scan/start<br>[Bearer JWT]
    JWT->>RBAC: Validate token → UserPrincipal
    RBAC->>RBAC: checkPermission(role, ANALYZE_AI)
    alt Forbidden
        RBAC-->>FE: 403 Forbidden
    else Allowed
        RBAC->>SR: Route handler
        SR->>BSE: startScan("PROJ")
        alt Conflict (scan already running)
            BSE-->>SR: ConflictException
            SR-->>FE: 409 Conflict
        else Success
            BSE-->>SR: ScanState
            SR-->>FE: 200 OK + ScanStatusResponse
        end
    end
```

## Request/Response DTOs

### ScanStatusResponse

```kotlin
@Serializable
data class ScanStatusResponse(
    val projectKey: String,
    val status: ScanStatus,            // IDLE, SCANNING, PAUSED, COMPLETED, CANCELLED
    val totalTickets: Int,
    val processedCount: Int,
    val progressPercent: Int,
    val currentTicketId: String?,
    val startedAt: String?,
    val updatedAt: String?,
    val recentLog: List<ScanLogEntryResponse> = emptyList()  // Last 5 entries (for status endpoint)
)
```

### ScanLogEntryResponse

```kotlin
@Serializable
data class ScanLogEntryResponse(
    val ticketId: String,
    val status: String,                // COMPLETED, FAILED
    val message: String,
    val timestamp: String
)
```

### ScanLogResponse

```kotlin
@Serializable
data class ScanLogResponse(
    val projectKey: String,
    val entries: List<ScanLogEntryResponse>,
    val totalEntries: Int
)
```

### Error Response (reuse existing)

```kotlin
@Serializable
data class ErrorResponse(val error: String)
```

## Route Implementation

```kotlin
// server/.../routes/ScanRoutes.kt
fun Routing.scanRoutes() {
    val batchScanEngine by inject<BatchScanEngine>()

    route("/api/projects/{key}/scan") {
        // START — requires ANALYZE_AI permission (Neural_Architect+)
        withPermission(Permission.ANALYZE_AI) {
            post("/start") {
                val projectKey = call.parameters["key"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                try {
                    val state = batchScanEngine.startScan(projectKey)
                    call.respond(HttpStatusCode.OK, state.toResponse())
                } catch (e: ConflictException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Scan already running"))
                }
            }

            post("/pause") {
                val projectKey = call.parameters["key"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val state = batchScanEngine.pauseScan(projectKey)
                call.respond(HttpStatusCode.OK, state.toResponse())
            }

            post("/resume") {
                val projectKey = call.parameters["key"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val state = batchScanEngine.resumeScan(projectKey)
                call.respond(HttpStatusCode.OK, state.toResponse())
            }

            post("/cancel") {
                val projectKey = call.parameters["key"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val state = batchScanEngine.cancelScan(projectKey)
                call.respond(HttpStatusCode.OK, state.toResponse())
            }
        }

        // STATUS & LOG — requires VIEW_ANALYSIS permission (Reader+)
        withPermission(Permission.VIEW_ANALYSIS) {
            get("/status") {
                val projectKey = call.parameters["key"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val state = batchScanEngine.getStatus(projectKey)
                val recentLog = batchScanEngine.getLog(projectKey, limit = 5)
                call.respond(HttpStatusCode.OK, state.toResponse(recentLog))
            }

            get("/log") {
                val projectKey = call.parameters["key"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val entries = batchScanEngine.getLog(projectKey, limit)
                call.respond(HttpStatusCode.OK, ScanLogResponse(
                    projectKey = projectKey,
                    entries = entries.map { it.toResponse() },
                    totalEntries = entries.size
                ))
            }
        }
    }
}

// Extension functions for DTO mapping
private fun ScanState.toResponse(recentLog: List<ScanLogEntry> = emptyList()) = ScanStatusResponse(
    projectKey = projectKey,
    status = status,
    totalTickets = totalTickets,
    processedCount = processedCount,
    progressPercent = progressPercent,
    currentTicketId = currentTicketId,
    startedAt = startedAt,
    updatedAt = updatedAt,
    recentLog = recentLog.map { it.toResponse() }
)

private fun ScanLogEntry.toResponse() = ScanLogEntryResponse(
    ticketId = ticketId,
    status = status.name,
    message = message,
    timestamp = timestamp
)
```

## RBAC Mapping

| Permission | Roles | Endpoints |
|---|---|---|
| `ANALYZE_AI` | Administrator, Neural_Architect | start, pause, resume, cancel |
| `VIEW_ANALYSIS` | Administrator, Neural_Architect, Reader | status, log |

Sử dụng `withPermission()` middleware đã có trong `AnalysisRoutes.kt`.

## Error Handling

| HTTP Status | Tình huống |
|---|---|
| 200 | Thao tác thành công |
| 400 | Thiếu project key |
| 401 | JWT missing/expired |
| 403 | Không đủ quyền RBAC |
| 404 | Project không tồn tại hoặc chưa có scan state |
| 409 | Đã có scan đang chạy cho project này |
| 502 | Lỗi kết nối Jira khi lấy danh sách ticket |

## Koin Registration

```kotlin
// ServerModule.kt — thêm vào serverModule()
single<ScanStateRepository> { ScanStateRepositoryImpl(get()) }
single<ScanLogRepository> { ScanLogRepositoryImpl(get()) }
single { BatchScanEngine(get(), get(), { get<JiraClient>() }, get(), get(), get()) }
```

## Route Registration

```kotlin
// Application.kt — thêm vào configureRouting()
routing {
    // ... existing routes
    scanRoutes()
}
```

*(Validates: Req 18.7–18.12, 18.15)*
