# Backend Core — Design

# Thành phần & Giao diện (Components and Interfaces)

## 1. Backend Server Module (`server`)

Module Ktor mới, thêm vào `settings.gradle.kts` với `include(":server")`.

```kotlin
// server/src/main/kotlin/com/assistant/server/Application.kt
fun Application.module() {
    install(ContentNegotiation) { json() }
    install(Authentication) { jwt("auth-jwt") { /* HMAC256 config */ } }
    install(StatusPages) { /* error handlers */ }
    
    configureRouting()  // mount all route groups
}
```

**Route Groups:**

| Route Group | Endpoints | Auth | RBAC |
|---|---|---|---|
| `/api/auth` | `POST /login`, `POST /logout` | None / JWT | None |
| `/api/projects` | `GET /`, `GET /{key}/issues`, `GET /{key}/analysis` | JWT | Reader+ |
| `/api/analysis` | `GET /{ticketId}`, `POST /{ticketId}/reanalyze` | JWT | Neural_Architect+ |
| `/api/estimation` | `POST /estimate` | JWT | Neural_Architect+ |
| `/api/graph` | `GET /{projectKey}` | JWT | Reader+ |
| `/api/users` | `GET /`, `PUT /{userId}/role`, `PUT /{userId}/permissions` | JWT | Administrator |
| `/api/integrations` | `GET /`, `POST /{providerId}/test`, `PUT /{providerId}/config` | JWT | Administrator (write), Reader+ (read) |
| `/health` | `GET /` | None | None |

## 2. Auth Service

```kotlin
// shared/.../auth/AuthService.kt
interface AuthService {
    suspend fun authenticate(domain: String, apiToken: String): AuthResult
    fun generateJwt(user: AuthenticatedUser): String
    fun validateJwt(token: String): AuthenticatedUser?
    fun invalidateSession(userId: String)
}

data class AuthenticatedUser(
    val userId: String,
    val email: String,
    val role: UserRole,
    val projectKey: String,
    val jiraDomain: String
)

// ServerConfig bổ sung ENCRYPTION_KEY cho mã hóa provider API keys
data class ServerConfig(
    val jiraHost: String,          // env: JIRA_HOST
    val aiProviderUrl: String,     // env: AI_PROVIDER_URL
    val jwtSecret: String,         // env: JWT_SECRET
    val encryptionKey: String,     // env: ENCRYPTION_KEY (AES-256-GCM)
    val port: Int                  // env: PORT
)

sealed class AuthResult {
    data class Success(val user: AuthenticatedUser, val jwt: String, val projects: List<JiraProject>) : AuthResult()
    data class Failure(val code: Int, val message: String) : AuthResult()
}
```

## 3. RBAC Engine

```kotlin
// shared/.../rbac/RBACEngine.kt
enum class UserRole { ADMINISTRATOR, NEURAL_ARCHITECT, READER }

enum class Permission {
    VIEW_DASHBOARD, VIEW_GRAPH, VIEW_ANALYSIS,
    ANALYZE_AI, VIEW_KB, RE_ANALYZE,
    CONFIG_INTEGRATIONS, TEST_PROVIDER,
    MANAGE_USERS, TOGGLE_PERMISSIONS, SIGN_OUT
}

interface RBACEngine {
    fun hasPermission(role: UserRole, permission: Permission): Boolean
    fun getPermissions(role: UserRole): Set<Permission>
    suspend fun changeRole(adminId: String, targetUserId: String, newRole: UserRole): RBACResult
    suspend fun togglePermission(adminId: String, targetUserId: String, permission: Permission, enabled: Boolean): RBACResult
}

// Ma trận phân quyền cứng (hardcoded permission matrix)
object PermissionMatrix {
    private val matrix = mapOf(
        UserRole.ADMINISTRATOR to Permission.entries.toSet(),
        UserRole.NEURAL_ARCHITECT to setOf(
            Permission.VIEW_DASHBOARD, Permission.VIEW_GRAPH, Permission.VIEW_ANALYSIS,
            Permission.ANALYZE_AI, Permission.VIEW_KB, Permission.RE_ANALYZE,
            Permission.TEST_PROVIDER, Permission.SIGN_OUT
        ),
        UserRole.READER to setOf(
            Permission.VIEW_DASHBOARD, Permission.VIEW_GRAPH, Permission.VIEW_ANALYSIS,
            Permission.VIEW_KB, Permission.SIGN_OUT
        )
    )
    fun check(role: UserRole, permission: Permission): Boolean = matrix[role]?.contains(permission) ?: false
}
```

## 4. AI Orchestrator

```kotlin
// shared/.../ai/AIOrchestrator.kt
interface AIOrchestrator {
    suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean = false): AnalysisResult
    suspend fun testProvider(providerId: String): ProviderTestResult
    suspend fun getProviderStatuses(): List<ProviderStatus>
    fun setFailoverOrder(providerIds: List<String>)
}

data class AnalysisResult(
    val ticketId: String,
    val context: RequirementSummary,
    val evolution: List<EvolutionEntry>,
    val complexity: ComplexityAssessment,
    val source: AnalysisSource  // KB_CACHE or FRESH_AI
)

enum class AnalysisSource { KB_CACHE, FRESH_AI }

data class ProviderStatus(
    val providerId: String,
    val name: String,
    val status: ConnectionStatus,  // ACTIVE, STANDBY, OFFLINE
    val latencyMs: Long?,
    val lastChecked: String?
)
```

## 5. KB Repository

```kotlin
// shared/.../kb/KBRepository.kt
interface KBRepository {
    suspend fun findByTicketId(ticketId: String): KBRecord?
    suspend fun save(record: KBRecord): Boolean
    suspend fun overwrite(record: KBRecord): Boolean
    suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean
    suspend fun getGraphData(projectKey: String): NetworkGraph?
}

@Serializable
data class KBRecord(
    val ticketId: String,
    val requirementSummary: String,
    val evolutionHistory: List<EvolutionEntry>,
    val scrumPoints: Double,
    val confidenceScore: Double,
    val rationale: String,
    val similarTicketRefs: List<String>,
    val timestamp: String  // ISO-8601
)
```

## 6. Graph Engine

```kotlin
// shared/.../graph/GraphEngine.kt
interface GraphEngine {
    fun computeLayout(graph: NetworkGraph, width: Double, height: Double): GraphLayout
    fun detectClusters(graph: NetworkGraph): List<Cluster>
}

data class GraphLayout(
    val positions: Map<String, Position>,  // nodeId -> (x, y)
    val bounds: Bounds
)

data class Position(val x: Double, val y: Double)
data class Cluster(val id: Int, val nodeIds: List<String>, val color: String)
```

Thuật toán: **Fruchterman-Reingold** force-directed layout
- Lực đẩy (repulsion) giữa tất cả các cặp node: `F_rep = k² / d`
- Lực hút (attraction) giữa các node có edge: `F_att = d² / k`
- Với `k = C * sqrt(area / |V|)`, `C = 0.5`
- Chạy 50-100 iterations, giảm dần temperature

## 7. Batch Scan Engine

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
    private val activeJobs = ConcurrentHashMap<String, Job>()

    suspend fun startScan(projectKey: String): ScanState
    suspend fun pauseScan(projectKey: String): ScanState
    suspend fun resumeScan(projectKey: String): ScanState
    suspend fun cancelScan(projectKey: String): ScanState
    suspend fun getStatus(projectKey: String): ScanState
    suspend fun getLog(projectKey: String, limit: Int = 50): List<ScanLogEntry>
    suspend fun recoverOnStartup()
}
```

Chi tiết thiết kế: xem #[[file:design/batch-scan-engine.md]]

## 8. Scan State Repository

```kotlin
// shared/.../scan/ScanStateRepository.kt
interface ScanStateRepository {
    suspend fun findByProjectKey(projectKey: String): ScanState?
    suspend fun save(state: ScanState): Boolean
    suspend fun delete(projectKey: String): Boolean
    suspend fun findByStatus(status: ScanStatus): List<ScanState>
}
```

## 9. Scan Routes

| Route Group | Endpoints | Auth | RBAC |
|---|---|---|---|
| `/api/projects/{key}/scan` | `POST /start`, `POST /pause`, `POST /resume`, `POST /cancel` | JWT | Neural_Architect+ |
| `/api/projects/{key}/scan` | `GET /status`, `GET /log` | JWT | Reader+ |

Chi tiết thiết kế: xem #[[file:design/scan-api-routes.md]]

---

# Mô hình Dữ liệu (Data Models)

## Mô hình hiện có (đã triển khai trong shared module)

| Model | File | Mô tả |
|---|---|---|
| `JiraProject` | `JiraClient.kt` | id, key, name, projectTypeKey? |
| `JiraIssue` | `JiraClient.kt` | id, key, fields (summary, description, status, resolution, created, updated) |
| `NetworkGraph` | `NetworkModels.kt` | nodes: List\<TicketNode\>, edges: List\<TicketEdge\> |
| `TicketNode` | `NetworkModels.kt` | id, key, summary, status, featureName? |
| `TicketEdge` | `NetworkModels.kt` | fromId, toId, relationshipType, isSemantic |
| `ScrumEstimation` | `EstimationModels.kt` | suggestedPoints, confidenceScore, rationale, similarHistoricalTickets |
| `SimilarTicket` | `EstimationModels.kt` | ticketKey, summary, actualPoints, similarityScore |
| `AIResult` | `AIAgent.kt` | sealed: Success(response, tokens?) / Failure(error) |
| `AIContext` | `AIAgent.kt` | tickets: List\<JiraTicketSummary\>, metadata: Map |

## Mô hình mới cần tạo

### KBRecord (Knowledge Base Record)
```kotlin
@Serializable
data class KBRecord(
    val ticketId: String,
    val requirementSummary: String,
    val evolutionHistory: List<EvolutionEntry>,
    val scrumPoints: Double,
    val confidenceScore: Double,
    val rationale: String,
    val similarTicketRefs: List<String>,
    val timestamp: String
)

@Serializable
data class EvolutionEntry(
    val version: String,
    val date: String,
    val description: String,
    val changeType: String  // ORIGIN, UPDATE, CURRENT
)
```

### User & RBAC Models
```kotlin
@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val avatarUrl: String?,
    val customPermissions: Set<Permission> = emptySet()
)

@Serializable
data class AuditLogEntry(
    val timestamp: String,
    val actorId: String,
    val targetUserId: String,
    val action: String,
    val oldValue: String,
    val newValue: String,
    val tag: String  // IAM_SYNC, USER_LOGIN, etc.
)
```

### Analysis Response Models
```kotlin
@Serializable
data class RequirementSummary(
    val unified: String,
    val affectedModules: List<AffectedModule>
)

@Serializable
data class AffectedModule(
    val name: String,
    val colorCategory: String  // PRIMARY, ACCENT, SECONDARY
)

@Serializable
data class ComplexityAssessment(
    val scrumPoints: Double,
    val description: String,
    val kbReferences: List<KBReference>
)

@Serializable
data class KBReference(
    val ticketId: String,
    val similarityPercent: Double
)
```

### Sprint Analysis Response Models
```kotlin
@Serializable
data class ProjectAnalysisResponse(
    val projectKey: String,
    val totalTickets: Int,
    val resolutionRate: Double,
    val cycleTimeDays: Double,
    val aiVelocity: Double,
    val velocityTrend: List<SprintVelocity>,
    val bottlenecks: List<BottleneckAlert>,
    val providerStatuses: List<ProviderStatus>
)

@Serializable
data class SprintVelocity(
    val sprintName: String,
    val storyPoints: Double
)

@Serializable
data class BottleneckAlert(
    val type: String,        // "RISK" or "OPTIMIZATION"
    val severity: String,    // "HIGH", "MEDIUM", "LOW"
    val title: String,
    val description: String
)

@Serializable
data class AnalysisStatus(
    val ticketId: String,
    val phase: String,       // "METADATA", "AI_ANALYZING", "KB_SYNCING", "COMPLETE"
    val progressPercent: Int  // 0-100
)
```

### Provider Configuration Models
```kotlin
@Serializable
data class ProviderConfig(
    val providerId: String,
    val name: String,
    val type: ProviderType,
    val endpoint: String,
    val apiKey: String? = null,
    val model: String? = null,
    val priority: Int,
    val status: ConnectionStatus
)

@Serializable
enum class ProviderType { JIRA, OLLAMA, GEMINI, LM_STUDIO, GEMINI_CLI }

@Serializable
enum class ConnectionStatus { ACTIVE, STANDBY, OFFLINE }
```

## Mô hình mới — Batch Scan Engine

### ScanStatus Enum

```kotlin
@Serializable
enum class ScanStatus {
    IDLE, SCANNING, PAUSED, COMPLETED, CANCELLED
}
```

### ScanState Data Class

```kotlin
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
@Serializable
data class ScanLogEntry(
    val id: Long = 0,
    val projectKey: String,
    val ticketId: String,
    val status: ScanLogStatus,
    val message: String,
    val timestamp: String              // ISO-8601
)

@Serializable
enum class ScanLogStatus {
    ANALYZING, COMPLETED, FAILED
}
```

### TicketAnalysisStatus Data Class (cho Combobox)

```kotlin
@Serializable
data class TicketAnalysisStatus(
    val ticketId: String,
    val ticketSummary: String,
    val analysisState: TicketAnalysisState,
    val lastAnalyzedAt: String?,       // ISO-8601, null nếu chưa phân tích
    val ticketUpdatedAt: String?       // ISO-8601, từ Jira
)

@Serializable
enum class TicketAnalysisState {
    NOT_ANALYZED,      // Chưa phân tích
    ANALYZED,          // Đã phân tích
    HAS_UPDATES,       // Có cập nhật mới từ Jira sau lần phân tích cuối
    ANALYZING          // Đang phân tích
}
```

### Scan API Response DTOs

```kotlin
@Serializable
data class ScanStatusResponse(
    val projectKey: String,
    val status: ScanStatus,
    val totalTickets: Int,
    val processedCount: Int,
    val progressPercent: Int,
    val currentTicketId: String?,
    val startedAt: String?,
    val updatedAt: String?,
    val recentLog: List<ScanLogEntryResponse> = emptyList()
)

@Serializable
data class ScanLogEntryResponse(
    val ticketId: String,
    val status: String,
    val message: String,
    val timestamp: String
)

@Serializable
data class ScanLogResponse(
    val projectKey: String,
    val entries: List<ScanLogEntryResponse>,
    val totalEntries: Int
)
```

## SQLDelight Schema

```sql
-- shared/src/commonMain/sqldelight/com/assistant/db/KnowledgeBase.sq

CREATE TABLE kb_records (
    ticket_id TEXT NOT NULL PRIMARY KEY,
    requirement_summary TEXT NOT NULL,
    evolution_history TEXT NOT NULL,  -- JSON serialized List<EvolutionEntry>
    scrum_points REAL NOT NULL,
    confidence_score REAL NOT NULL,
    rationale TEXT NOT NULL,
    similar_ticket_refs TEXT NOT NULL,  -- JSON serialized List<String>
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE graph_data (
    project_key TEXT NOT NULL PRIMARY KEY,
    graph_json TEXT NOT NULL,  -- JSON serialized NetworkGraph
    updated_at TEXT NOT NULL
);

CREATE TABLE users (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL DEFAULT 'READER',
    avatar_url TEXT,
    custom_permissions TEXT NOT NULL DEFAULT '[]'
);

CREATE TABLE audit_log (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    target_user_id TEXT NOT NULL,
    action TEXT NOT NULL,
    old_value TEXT NOT NULL,
    new_value TEXT NOT NULL,
    tag TEXT NOT NULL
);

CREATE TABLE provider_configs (
    provider_id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    api_key TEXT,
    model TEXT,
    priority INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'OFFLINE'
);

-- Batch Scan tables (NEW)
CREATE TABLE scan_states (
    project_key TEXT NOT NULL PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'IDLE',
    total_tickets INTEGER NOT NULL DEFAULT 0,
    processed_count INTEGER NOT NULL DEFAULT 0,
    current_ticket_id TEXT,
    ticket_ids TEXT NOT NULL DEFAULT '[]',  -- JSON serialized List<String>
    started_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE scan_log (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    project_key TEXT NOT NULL,
    ticket_id TEXT NOT NULL,
    status TEXT NOT NULL,               -- ANALYZING, COMPLETED, FAILED
    message TEXT NOT NULL,
    timestamp TEXT NOT NULL
);

CREATE INDEX idx_scan_log_project ON scan_log(project_key, timestamp DESC);

-- Queries (existing)
findKBRecordByTicketId:
SELECT * FROM kb_records WHERE ticket_id = ?;

insertOrReplaceKBRecord:
INSERT OR REPLACE INTO kb_records VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

findGraphByProjectKey:
SELECT * FROM graph_data WHERE project_key = ?;

insertOrReplaceGraph:
INSERT OR REPLACE INTO graph_data VALUES (?, ?, ?);

getAllUsers:
SELECT * FROM users;

findUserById:
SELECT * FROM users WHERE id = ?;

updateUserRole:
UPDATE users SET role = ? WHERE id = ?;

insertAuditLog:
INSERT INTO audit_log (timestamp, actor_id, target_user_id, action, old_value, new_value, tag) VALUES (?, ?, ?, ?, ?, ?, ?);

getRecentAuditLogs:
SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT ?;

getAllProviders:
SELECT * FROM provider_configs ORDER BY priority ASC;

updateProviderStatus:
UPDATE provider_configs SET status = ? WHERE provider_id = ?;

-- Scan State queries (NEW)
findScanStateByProjectKey:
SELECT * FROM scan_states WHERE project_key = ?;

insertOrReplaceScanState:
INSERT OR REPLACE INTO scan_states VALUES (?, ?, ?, ?, ?, ?, ?, ?);

deleteScanState:
DELETE FROM scan_states WHERE project_key = ?;

findScanningScanStates:
SELECT * FROM scan_states WHERE status = 'SCANNING';

-- Scan Log queries (NEW)
insertScanLogEntry:
INSERT INTO scan_log (project_key, ticket_id, status, message, timestamp) VALUES (?, ?, ?, ?, ?);

getScanLogByProjectKey:
SELECT * FROM scan_log WHERE project_key = ? ORDER BY timestamp DESC LIMIT ?;

deleteScanLogByProjectKey:
DELETE FROM scan_log WHERE project_key = ?;
```

---

# Correctness Properties

*Một property là một đặc tính hoặc hành vi phải đúng trong mọi lần thực thi hợp lệ của hệ thống — về bản chất là một phát biểu hình thức về những gì hệ thống phải làm. Properties đóng vai trò cầu nối giữa đặc tả dễ đọc cho con người và đảm bảo tính đúng đắn có thể kiểm chứng bằng máy.*

## Property 1: JWT Generation/Validation Round-Trip

*For any* valid `AuthenticatedUser` (với user_id, email, role, project_key hợp lệ), việc gọi `generateJwt(user)` rồi `validateJwt(token)` SHALL trả về đối tượng `AuthenticatedUser` có cùng user_id, email, role, và project_key với đối tượng ban đầu, và token phải có thời hạn 24 giờ.

**Validates: Requirements 1.7, 10.1, 10.3**

## Property 2: RBAC Permission Matrix Enforcement

*For any* cặp `(UserRole, Permission)`, kết quả `hasPermission(role, permission)` SHALL trả về `true` khi và chỉ khi permission nằm trong tập quyền được định nghĩa cho role đó trong ma trận phân quyền. Cụ thể: Reader không có ANALYZE_AI, RE_ANALYZE, CONFIG_INTEGRATIONS, TEST_PROVIDER, MANAGE_USERS, TOGGLE_PERMISSIONS; Neural_Architect không có CONFIG_INTEGRATIONS, MANAGE_USERS, TOGGLE_PERMISSIONS.

**Validates: Requirements 7.2, 7.8, 11.1, 11.2, 11.3**

## Property 3: RBAC Audit Log Completeness

*For any* thao tác thay đổi vai trò hoặc quyền hạn người dùng, RBAC_Engine SHALL tạo một bản ghi audit log chứa đầy đủ: actor_id, target_user_id, action, old_value, new_value, và timestamp hợp lệ (ISO-8601).

**Validates: Requirements 7.6, 16.4**

## Property 4: KB-First Strategy — Cache Hit Avoids AI Call

*For any* ticketId đã tồn tại trong Knowledge_Base, khi gọi `analyzeTicket(ticketId, forceReanalyze=false)`, AI_Orchestrator SHALL trả về kết quả từ KB cache với `source = KB_CACHE` mà không gọi bất kỳ AI agent nào.

**Validates: Requirements 5.2, 9.3**

## Property 5: Scrum Point Scale Invariant

*For any* giá trị số thực `points` (bao gồm số âm, số rất lớn, NaN-safe), hàm `findClosestAllowedPoint(points)` SHALL trả về một giá trị nằm trong tập `{0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40}`.

**Validates: Requirements 5.6**

## Property 6: KB Record Persistence Round-Trip

*For any* `KBRecord` hợp lệ, việc gọi `save(record)` rồi `findByTicketId(record.ticketId)` SHALL trả về bản ghi có cùng ticketId, requirementSummary, scrumPoints, confidenceScore, rationale, và similarTicketRefs. Tương tự, `overwrite(newRecord)` rồi `findByTicketId` SHALL trả về bản ghi mới với timestamp được cập nhật.

**Validates: Requirements 9.1, 9.4**

## Property 7: Graph Data Persistence Round-Trip

*For any* `NetworkGraph` hợp lệ và `projectKey`, việc gọi `saveGraphData(projectKey, graph)` rồi `getGraphData(projectKey)` SHALL trả về `NetworkGraph` có cùng số lượng nodes và edges, với mỗi node và edge có giá trị bằng bản gốc.

**Validates: Requirements 9.8**

## Property 8: Domain Object Serialization Round-Trip

*For any* đối tượng domain hợp lệ thuộc các kiểu `ScrumEstimation`, `NetworkGraph`, `AIResult` (cả Success và Failure), `JiraIssue`, và `KBRecord`, việc serialize thành JSON string rồi deserialize lại SHALL tạo ra đối tượng có giá trị bằng đối tượng ban đầu.

**Validates: Requirements 14.1, 14.2, 14.3, 14.4, 14.6**

## Property 9: Missing Required Field Deserialization Error

*For any* kiểu serializable có trường bắt buộc, và *for any* JSON string hợp lệ của kiểu đó mà một trường bắt buộc bị xóa, việc deserialize SHALL ném exception chứa tên trường bị thiếu trong thông báo lỗi.

**Validates: Requirements 14.5**

## Property 10: Force-Directed Layout Bounds Invariant

*For any* `NetworkGraph` hợp lệ (1-100 nodes) và kích thước canvas `(width, height)`, thuật toán `computeLayout(graph, width, height)` SHALL đặt tất cả node tại vị trí `(x, y)` nằm trong phạm vi `[0, width] × [0, height]`, và khoảng cách trung bình giữa các cặp node có edge SHALL nhỏ hơn khoảng cách trung bình giữa tất cả các cặp node.

**Validates: Requirements 3.7**

## Property 11: Cluster Detection Partitioning

*For any* `NetworkGraph` hợp lệ có ít nhất 2 node, `detectClusters(graph)` SHALL trả về danh sách clusters mà: (a) mỗi node thuộc đúng một cluster, (b) tổng số node trong tất cả clusters bằng tổng số node trong graph, và (c) không có cluster nào rỗng.

**Validates: Requirements 3.10**

## Property 12: Graph Search Filter Correctness

*For any* chuỗi tìm kiếm `query` và `NetworkGraph`, kết quả lọc SHALL chỉ chứa các node mà `node.key` hoặc `node.summary` chứa `query` (case-insensitive), và SHALL chứa tất cả node thỏa mãn điều kiện này.

**Validates: Requirements 3.6**

## Property 13: AI Provider Failover Priority Selection

*For any* danh sách `ProviderConfig` với các priority và status khác nhau, AI_Orchestrator SHALL chọn provider có priority cao nhất (số nhỏ nhất) đang ở trạng thái ACTIVE. Nếu provider đang dùng chuyển sang OFFLINE, orchestrator SHALL tự động chọn provider ACTIVE tiếp theo theo thứ tự priority.

**Validates: Requirements 12.2, 12.3**
