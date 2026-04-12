# Jira Assistant

Nền tảng quản lý dự án Agile tích hợp AI, xây dựng trên Kotlin Multiplatform + Ktor + Kotlin/JS frontend.

## Yêu cầu hệ thống

- JDK 17+
- Gradle 8+ (hoặc dùng `./gradlew` wrapper đi kèm)
- Docker & Docker Compose (cho deployment)
- Ollama (cho AI local) với 2 models:
  - `gemma4:e2b` — model chat/inference cho phân tích ticket
  - `nomic-embed-text` — model embedding cho semantic search attachment content
- Node.js 18+ (cho frontend Vite dev server và MCP servers)

### Cài đặt Ollama models

```bash
ollama pull gemma4:e2b
ollama pull nomic-embed-text
```

### Chạy Ollama (tối ưu cho scan)

```bash
# Giữ cả 2 models trong VRAM đồng thời (tránh swap chậm)
set OLLAMA_MAX_LOADED_MODELS=2
set OLLAMA_NUM_PARALLEL=2
ollama serve
```

RTX 4060 8GB VRAM đủ chứa cả 2 models đồng thời (gemma4 ~4-5GB + nomic ~0.3GB ≈ 5.3GB).
Nếu không set `OLLAMA_MAX_LOADED_MODELS=2`, Ollama sẽ swap models qua lại mỗi lần chuyển giữa inference và embedding → chậm đáng kể.

## Chạy nhanh với Docker

```bash
docker compose up --build

# Truy cập: http://localhost:8080
# Health check: http://localhost:8080/health
```

Cấu hình qua biến môi trường (tạo file `.env` hoặc truyền trực tiếp):

```env
JIRA_HOST=https://your-team.atlassian.net
JWT_SECRET=your-secure-secret-here
ENCRYPTION_KEY=your-encryption-key-here
AI_PROVIDER_URL=http://localhost:11434
PORT=8080
```

`AI_PROVIDER_URL` trỏ tới Ollama server đang chạy riêng. Mặc định `http://host.docker.internal:11434` (kết nối từ Docker container tới Ollama trên máy host).

Sau khi đăng nhập, bạn có thể cấu hình lại kết nối AI providers (Ollama, Gemini, LM Studio) trực tiếp trên giao diện tại trang Integrations — bao gồm thay đổi endpoint, test kết nối, và chọn model.

> **Lần chạy đầu tiên:** Nếu chưa cấu hình settings, Administrator sẽ được tự động chuyển đến trang App Settings để thiết lập. Các biến môi trường ở trên là giá trị mặc định — sau khi lưu qua UI, giá trị trong DB sẽ được ưu tiên hơn env vars (trừ `DB_PATH` và `STATIC_DIR` luôn đọc từ env).

## Chạy development (không Docker)

### 1. Build shared module

```bash
./gradlew :shared:jvmJar
```

### 2. Chạy server

```bash
# Set biến môi trường (Windows PowerShell)
$env:JIRA_HOST="https://your-team.atlassian.net"
$env:JWT_SECRET="dev-secret"
$env:AI_PROVIDER_URL="http://localhost:11434"
$env:DB_PATH="./data/jira-assistant.db"

./gradlew :server:jvmRun
```

Server khởi động tại `http://localhost:8080`.

### 3. Frontend (Kotlin/JS + Vite)

Frontend viết bằng Kotlin/JS, compile sang JavaScript, bundle bởi Webpack (tích hợp trong Gradle).

```bash
# Build Kotlin/JS webpack bundle (bắt buộc trước khi chạy Vite)
./gradlew :frontend:jsBrowserDevelopmentWebpack

# Install npm dependencies (chạy lần đầu hoặc khi thêm dependency mới)
cd frontend && npm install

# Chạy Vite dev server (HMR, proxy /api → backend)
npx vite
```

Vite dev server chạy tại `http://localhost:3000`, tự động proxy API calls tới backend port 8080.

> **Lưu ý:** Mỗi khi sửa code Kotlin/JS, cần chạy lại `./gradlew :frontend:jsBrowserDevelopmentWebpack` để rebuild bundle. Vite chỉ serve file tĩnh, không tự compile Kotlin.

Hoặc build production bundle:

```bash
./gradlew :frontend:jsBrowserProductionWebpack
```

Output JS bundle nằm tại `frontend/build/kotlin-webpack/js/productionExecutable/`.

## Chạy tests

```bash
# Unit + property tests (shared module)
./gradlew :shared:jvmTest

# Unit + property tests (server module)
./gradlew :server:jvmTest

# Tất cả tests
./gradlew :shared:jvmTest :server:jvmTest
```

## Build fat JAR

```bash
./gradlew :server:fatJar
# Output: server/build/libs/jira-assistant-server-all.jar

java -jar server/build/libs/jira-assistant-server-all.jar
```

## Air-gapped deployment (không internet)

Chỉ sử dụng Ollama local, không gọi cloud AI:

```bash
ollama serve
AI_PROVIDER_URL=http://host.docker.internal:11434 docker compose up --build
```

## Biến môi trường

| Biến | Mô tả | Mặc định |
|------|--------|----------|
| `JIRA_HOST` | Jira instance URL | `https://jira.example.com` |
| `AI_PROVIDER_URL` | Ollama server URL | `http://host.docker.internal:11434` |
| `DB_PATH` | Đường dẫn SQLite database | `./data/jira-assistant.db` |
| `JWT_SECRET` | Secret cho JWT HMAC256 | `dev-secret-change-in-production` |
| `ENCRYPTION_KEY` | Key mã hóa AES-256-GCM | `dev-encryption-key-change-in-production` |
| `PORT` | Port server | `8080` |
| `STATIC_DIR` | Thư mục static files | `./static` |

## Cấu trúc dự án

```
├── shared/              # Business logic (KMP) — AI, Jira client, KB, RBAC, Graph
├── server/              # Ktor REST API server — routes, middleware, DI
├── frontend/            # Kotlin/JS + Vite frontend (SPA)
│   ├── index.html       # SPA entry point
│   ├── build.gradle.kts # Kotlin/JS IR compiler config
│   ├── vite.config.js   # Vite bundler + dev proxy
│   ├── master_style.css # Obsidian Kinetic design system CSS
│   └── src/jsMain/kotlin/com/assistant/frontend/
│       ├── App.kt           # Entry point, router setup
│       ├── router/Router.kt # Hash-based SPA routing
│       ├── api/ApiClient.kt # ktor-client-js, JWT management
│       ├── components/      # Shell, Sidebar, Navbar
│       └── pages/           # 8 page controllers (Dashboard, Graph, Analysis, Settings, etc.)
├── e2e-tests/           # Serenity BDD E2E tests
├── Dockerfile           # Multi-stage build (server + frontend)
└── docker-compose.yml
```

## Kiến trúc Frontend

Frontend sử dụng kiến trúc VIEW/CONTROLLER phân tách:

- VIEW: HTML inline trong Kotlin/JS (`buildPageHtml()`) + CSS files (Obsidian Kinetic design system)
- CONTROLLER: Kotlin/JS page objects với DOM manipulation (`getElementById`, `createElementNS` cho SVG, `addEventListener`)
- Shared Module: Type-safe domain models từ `:shared` KMP module — không cần manual JSON parsing
- Routing: Hash-based SPA (`#dashboard`, `#analysis`, `#knowledge_graph`, etc.)
- API: `ktor-client-js` với JWT từ `sessionStorage`

8 màn hình: Onboarding, Dashboard, Knowledge Graph, Project Analysis, Ticket Intelligence, Integrations, User Management, App Settings.

## API Endpoints

| Endpoint | Method | Auth | Mô tả |
|----------|--------|------|--------|
| `/health` | GET | — | Health check |
| `/api/auth/login` | POST | — | Đăng nhập Jira |
| `/api/auth/logout` | POST | JWT | Đăng xuất |
| `/api/projects` | GET | JWT | Danh sách dự án |
| `/api/projects/{key}/issues` | GET | JWT | Tickets của dự án |
| `/api/projects/{key}/analysis` | GET | JWT | Phân tích sprint |
| `/api/analysis/{ticketId}` | GET | JWT | Phân tích AI ticket |
| `/api/analysis/{ticketId}/reanalyze` | POST | JWT | Re-analyze ticket |
| `/api/analysis/{ticketId}/status` | GET | JWT | Trạng thái phân tích |
| `/api/estimation/estimate` | POST | JWT | Ước lượng Scrum point |
| `/api/graph/{projectKey}` | GET | JWT | Dữ liệu đồ thị |
| `/api/users` | GET | JWT | Danh sách users |
| `/api/users/{id}/role` | PUT | JWT | Đổi role |
| `/api/users/{id}/permissions` | PUT | JWT | Toggle permission |
| `/api/integrations` | GET | JWT | Danh sách AI providers |
| `/api/integrations/{id}/test` | POST | JWT | Test provider |
| `/api/integrations/{id}/config` | PUT | JWT | Cập nhật config |
| `/api/settings/status` | GET | — | Kiểm tra app đã cấu hình chưa |
| `/api/settings` | GET | JWT + Admin | Đọc settings (sensitive fields masked) |
| `/api/settings` | PUT | JWT + Admin | Cập nhật settings |
