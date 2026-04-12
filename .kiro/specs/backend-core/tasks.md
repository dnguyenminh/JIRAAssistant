# Backend Core — Tasks

Status: ✅ All completed

# Backend Core — Tasks 1–9, 11, 13–17

## Task 1: Thiết lập Backend Server Module (Ktor)
- [x] 1.1 Tạo module `server` trong `settings.gradle.kts` và `server/build.gradle.kts` với dependencies Ktor server (core, netty, content-negotiation, auth-jwt, status-pages), Koin, shared module
- [x] 1.2 Tạo `server/src/main/kotlin/com/assistant/server/Application.kt` với Ktor Application.module() cài đặt ContentNegotiation (JSON), Authentication (JWT), StatusPages (error handlers), và CORS
- [x] 1.3 Tạo `server/src/main/kotlin/com/assistant/server/config/ServerConfig.kt` đọc cấu hình từ biến môi trường (JIRA_HOST, AI_PROVIDER_URL, DB_PATH, JWT_SECRET, PORT)
- [x] 1.4 Tạo `server/src/main/kotlin/com/assistant/server/di/ServerModule.kt` với Koin module đăng ký tất cả server dependencies (AuthService, RBACEngine, AIOrchestrator, KBRepository)
- [x] 1.5 Tạo endpoint `GET /health` trả về trạng thái kết nối Jira API, AI provider, và Knowledge_Base

## Task 2: Auth Service & JWT Authentication
- [x] 2.1 Tạo `shared/.../auth/AuthService.kt` interface và `AuthServiceImpl.kt` với authenticate(), generateJwt(), validateJwt(), invalidateSession()
- [x] 2.2 Triển khai generateJwt() tạo JWT token chứa user_id, email, role, project_key với thời hạn 24 giờ sử dụng HMAC256
- [x] 2.3 Triển khai validateJwt() giải mã và xác thực JWT token, trả về AuthenticatedUser hoặc null
- [x] 2.4 Triển khai authenticate() gọi Jira REST API `/rest/api/3/project` để xác thực credentials và lấy danh sách projects
- [x] 2.5 Tạo `server/.../routes/AuthRoutes.kt` với POST /api/auth/login và POST /api/auth/logout
- [x] 2.6 Cấu hình JWT middleware trong Ktor Authentication plugin, đọc JWT_SECRET từ biến môi trường
- [x] 2.7 ✅ Viết property test cho Property 1: JWT Generation/Validation Round-Trip (Kotest)

## Task 3: RBAC Engine — Phân quyền dựa trên Vai trò
- [x] 3.1 Tạo `shared/.../rbac/RBACEngine.kt` interface, `UserRole` enum, `Permission` enum, và `PermissionMatrix` object
- [x] 3.2 Triển khai `RBACEngineImpl.kt` với hasPermission(), getPermissions(), changeRole(), togglePermission()
- [x] 3.3 Tạo `server/.../middleware/RBACMiddleware.kt` — Ktor route interceptor kiểm tra quyền trước khi xử lý request
- [x] 3.4 Triển khai audit log: mỗi thay đổi role/permission ghi AuditLogEntry vào database
- [x] 3.5 Tạo `server/.../routes/UserRoutes.kt` với GET /api/users, PUT /api/users/{userId}/role, PUT /api/users/{userId}/permissions
- [x] 3.6 ✅ Viết property test cho Property 2: RBAC Permission Matrix Enforcement (Kotest)
- [x] 3.7 ✅ Viết property test cho Property 3: RBAC Audit Log Completeness (Kotest)

## Task 4: Knowledge Base — SQLDelight Persistence Layer
- [x] 4.1 Tạo SQLDelight schema file `shared/src/commonMain/sqldelight/com/assistant/db/KnowledgeBase.sq` với tables: kb_records, graph_data, users, audit_log, provider_configs
- [x] 4.2 Tạo `shared/.../kb/KBRepository.kt` interface và `KBRepositoryImpl.kt` triển khai findByTicketId(), save(), overwrite(), saveGraphData(), getGraphData()
- [x] 4.3 Triển khai JSON serialization cho các trường phức tạp (evolution_history, similar_ticket_refs) trong KBRepositoryImpl
- [x] 4.4 Triển khai retry logic (tối đa 3 lần) cho các thao tác ghi vào database
- [x] 4.5 Đăng ký KBRepository trong Koin module (shared/domain hoặc server module)
- [x] 4.6 ✅ Viết property test cho Property 6: KB Record Persistence Round-Trip (Kotest)
- [x] 4.7 ✅ Viết property test cho Property 7: Graph Data Persistence Round-Trip (Kotest)

## Task 5: AI Orchestrator — KB-First Strategy & Failover
- [x] 5.1 Tạo `shared/.../ai/AIOrchestrator.kt` interface và `AIOrchestratorImpl.kt` với analyzeTicket(), testProvider(), getProviderStatuses(), setFailoverOrder()
- [x] 5.2 Triển khai KB-First strategy: kiểm tra KBRepository trước, chỉ gọi AI agent khi KB miss hoặc forceReanalyze=true
- [x] 5.3 Triển khai failover logic: timeout 30s → chuyển sang provider tiếp theo theo priority, ghi log failover event
- [x] 5.4 Triển khai AI response parsing: tách kết quả thành RequirementSummary, EvolutionHistory, ComplexityAssessment
- [x] 5.5 Triển khai retry logic (tối đa 2 lần) khi AI trả về JSON không hợp lệ
- [x] 5.6 Tạo `server/.../routes/AnalysisRoutes.kt` với GET /api/analysis/{ticketId} và POST /api/analysis/{ticketId}/reanalyze
- [x] 5.7 Tạo `server/.../routes/EstimationRoutes.kt` với POST /api/estimation/estimate
- [x] 5.8 ✅ Viết property test cho Property 4: KB-First Strategy — Cache Hit Avoids AI Call (Kotest)
- [x] 5.9 ✅ Viết property test cho Property 13: AI Provider Failover Priority Selection (Kotest)

## Task 6: Scrum Point Scale Validation
- [x] 6.1 Cập nhật `ScrumEstimator.findClosestAllowedPoint()` để xử lý edge cases (NaN, Infinity, số âm rất lớn)
- [x] 6.2 ✅ Viết property test cho Property 5: Scrum Point Scale Invariant (Kotest)

## Task 7: Domain Object Serialization Round-Trip
- [x] 7.1 Tạo `KBRecord`, `EvolutionEntry`, `RequirementSummary`, `ComplexityAssessment`, `ProviderConfig` data classes với @Serializable annotations trong shared module
- [x] 7.2 Cấu hình Kotlinx Serialization Json instance với `ignoreUnknownKeys = true`, `encodeDefaults = true` cho toàn project
- [x] 7.3 ✅ Viết property test cho Property 8: Domain Object Serialization Round-Trip (Kotest)
- [x] 7.4 ✅ Viết property test cho Property 9: Missing Required Field Deserialization Error (Kotest)

## Task 8: Graph Engine — Force-Directed Layout & Clustering
- [x] 8.1 Tạo `shared/.../graph/GraphEngine.kt` interface và `ForceDirectedGraphEngine.kt` triển khai computeLayout() với thuật toán Fruchterman-Reingold (50-100 iterations)
- [x] 8.2 Triển khai detectClusters() sử dụng thuật toán connected components hoặc modularity-based clustering
- [x] 8.3 Triển khai filterNodes(query) cho graph search — lọc theo key hoặc summary (case-insensitive)
- [x] 8.4 Tạo `server/.../routes/GraphRoutes.kt` với GET /api/graph/{projectKey}
- [x] 8.5 ✅ Viết property test cho Property 10: Force-Directed Layout Bounds Invariant (Kotest)
- [x] 8.6 ✅ Viết property test cho Property 11: Cluster Detection Partitioning (Kotest)
- [x] 8.7 ✅ Viết property test cho Property 12: Graph Search Filter Correctness (Kotest)

## Task 9: Backend REST API Routes — Project & Integration
- [x] 9.1 Tạo `server/.../routes/ProjectRoutes.kt` với GET /api/projects, GET /api/projects/{key}/issues, GET /api/projects/{key}/analysis
- [x] 9.2 Tạo `server/.../routes/IntegrationRoutes.kt` với GET /api/integrations, POST /api/integrations/{providerId}/test, PUT /api/integrations/{providerId}/config
- [x] 9.3 Tạo `server/.../routes/Routing.kt` tổng hợp mount tất cả route groups vào Application
- [x] 9.4 Triển khai StatusPages error handling: 400 (validation), 401 (auth), 403 (RBAC), 404 (not found), 500 (internal)

## Task 11: Docker Deployment & CI/CD
- [x] 11.1 Tạo `Dockerfile` multi-stage: stage 1 (gradle:jdk17 build server + Compose HTML frontend), stage 2 (eclipse-temurin:17-jre runtime) copy server fat JAR + Compose HTML JS bundle vào static directory
- [x] 11.2 Tạo `docker-compose.yml` với 3 services: backend (server + frontend), ollama (local AI), và volume cho SQLite database
- [x] 11.3 Cấu hình air-gapped mode: docker-compose profile chỉ sử dụng Ollama, không yêu cầu internet
- [x] 11.4 Tạo `.github/workflows/ci.yml`: build → test (unit + property + e2e) → Docker build → push

## Task 13: Project Analysis — Sprint Analytics & Velocity Chart
- [x] 13.1 Cập nhật `server/.../routes/ProjectRoutes.kt`: mở rộng `GET /api/projects/{key}/analysis` trả về `ProjectAnalysisResponse` đầy đủ
- [x] 13.2 Tạo `SprintVelocity`, `BottleneckAlert` data classes trong shared module với @Serializable annotations
- [x] 13.3 Triển khai logic phân tích bottleneck trong AI_Orchestrator
- [x] 13.4 ~~Cập nhật frontend~~ → Superseded bởi Task 32.4
- [x] 13.5 ~~Cập nhật frontend~~ → Superseded bởi Task 32.4
- [x] 13.6 ~~Thêm nút "DIVE INTO REPORTS"~~ → Superseded bởi Task 32.4

## Task 14: Dashboard — Network Preview & Estimation Drift *(Superseded bởi Task 32.2)*
- [x] 14.1 ~~Superseded~~ → Task 32.2
- [x] 14.2 ~~Superseded~~ → Task 32.2

## Task 15: Integrations — Dynamic Status Dot Tooltips *(Superseded bởi Task 32.6)*
- [x] 15.1 ~~Superseded~~ → Task 32.6

## Task 16: Analysis Status Polling Endpoint
- [x] 16.1 Tạo endpoint `GET /api/analysis/{ticketId}/status` trả về `AnalysisStatus`
- [x] 16.2 Tạo `AnalysisStatus` data class trong shared module
- [x] 16.3 ~~Frontend~~ → Superseded bởi Task 32.5

## Task 17: Encryption at Rest — Provider API Keys
- [x] 17.1 Cập nhật `ServerConfig.kt`: thêm `encryptionKey` đọc từ biến môi trường `ENCRYPTION_KEY`
- [x] 17.2 Tạo `shared/.../security/CryptoUtils.kt` với `encryptAES256GCM()` và `decryptAES256GCM()`
- [x] 17.3 Cập nhật `ProviderConfigRepository`: mã hóa `api_key` bằng AES-256-GCM
- [x] 17.4 Cập nhật `docker-compose.yml`: thêm biến môi trường `ENCRYPTION_KEY`
