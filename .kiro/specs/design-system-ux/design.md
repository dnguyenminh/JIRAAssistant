# Design System & UX — Design

# Thiết kế Obsidian Kinetic Design System (CSS + Kotlin/JS)

Frontend_App tuân thủ design system Obsidian Kinetic (Luminous V3). Styling sử dụng:
- **CSS files**: Cho tất cả styles — glassmorphism, animations, hover effects, pseudo-elements, backdrop-filter
- **Kotlin/JS DOM manipulation**: Cho dynamic styles phụ thuộc vào state (classList.add/remove/toggle, element.style)
- **HTML templates**: Cho layout structure với CSS classes đã định nghĩa sẵn

## Theme — CSS Custom Properties

Design system quản lý color palette và typography qua CSS custom properties (variables):

```css
/* obsidian-kinetic.css */
:root {
    /* Color Palette */
    --bg-deep-nebula: #0a0a1a;
    --surface: rgba(255, 255, 255, 0.03);
    --surface-border: rgba(255, 255, 255, 0.06);
    --primary: #25fecf;           /* Neon cyan */
    --accent: #3386ff;            /* Electric blue */
    --secondary: #be9dff;         /* Soft purple */
    --text-primary: rgba(255, 255, 255, 0.9);
    --text-secondary: rgba(255, 255, 255, 0.6);
    --text-muted: rgba(255, 255, 255, 0.3);
    --danger: #ff4757;
    --warning: #ffa502;
    --success: #25fecf;

    /* Typography */
    --font-main: 'Be Vietnam Pro', -apple-system, BlinkMacSystemFont, sans-serif;
    --font-mono: 'JetBrains Mono', 'Fira Code', monospace;
}
```

## Typography

- **Font chính:** `Be Vietnam Pro` với weights: 100 (Thin), 300 (Light), 400 (Regular), 600 (SemiBold), 700 (Bold)
- **Font monospace:** `JetBrains Mono` cho Neural Console, code snippets, và log entries

```css
/* obsidian-kinetic.css */
body {
    font-family: var(--font-main);
    color: var(--text-primary);
    background: var(--bg-deep-nebula);
    margin: 0;
    padding: 0;
}

.mono-text {
    font-family: var(--font-mono);
    color: var(--primary);
}

.heading-large {
    font-family: var(--font-main);
    font-weight: 100;
    font-size: 48px;
    letter-spacing: 2px;
}

.heading-medium {
    font-family: var(--font-main);
    font-weight: 300;
    font-size: 24px;
}

.label-small {
    font-family: var(--font-main);
    font-weight: 600;
    font-size: 10px;
    letter-spacing: 2px;
    text-transform: uppercase;
}
```

Google Fonts khai báo trong `index.html`:
```html
<link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@100;300;400;600;700&display=swap" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;700&display=swap" rel="stylesheet">
```

*(Validates: Req 17.2)*

## Glass Card Component

CSS class cho glassmorphism cards:

```css
/* obsidian-kinetic.css */
.glass-card {
    background: rgba(255, 255, 255, 0.03);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 16px;
    padding: 32px;
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.3s ease;
}

.glass-card:hover {
    transform: translateY(-5px) scale(1.02);
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
}
```

HTML template sử dụng:
```html
<!-- dashboard.html -->
<div class="glass-card">
    <div class="label-small">PROJECT AI HEALTH</div>
    <div class="stat-value" id="stat-ai-health">--</div>
</div>
```

Kotlin/JS controller bind dữ liệu:
```kotlin
// DashboardPage.kt
fun renderMetrics(metrics: ProjectAnalysisResponse) {
    document.getElementById("stat-ai-health")?.textContent = "${metrics.aiVelocity}%"
}
```

*(Validates: Req 17.3)*

## Glass-Styled Tooltips

Tooltip hiển thị qua CSS class `.glass-tooltip` kết hợp Kotlin/JS event handlers:

```css
/* components.css */
.tooltip-wrapper {
    position: relative;
}

.glass-tooltip {
    position: absolute;
    bottom: calc(100% + 8px);
    left: 50%;
    transform: translateX(-50%);
    padding: 8px 16px;
    background: rgba(15, 15, 35, 0.85);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 8px;
    color: rgba(255, 255, 255, 0.9);
    font-size: 11px;
    white-space: nowrap;
    z-index: 1000;
    pointer-events: none;
    opacity: 0;
    transition: opacity 0.2s ease;
}

.glass-tooltip.visible {
    opacity: 1;
}
```

Kotlin/JS toggle visibility:
```kotlin
// IntegrationsPage.kt
fun setupTooltips() {
    document.querySelectorAll(".status-dot").asList().forEach { dot ->
        val tooltip = (dot as HTMLElement).querySelector(".glass-tooltip") as? HTMLElement
        dot.addEventListener("mouseenter", { tooltip?.classList?.add("visible") })
        dot.addEventListener("mouseleave", { tooltip?.classList?.remove("visible") })
    }
}
```

Áp dụng cho tất cả metric cards, status dots, và provider cards. *(Validates: Req 17.4)*

## Progress Bar & Status Ticker (NeuralLoader)

Khi hệ thống xử lý tác vụ nặng, Frontend hiển thị progress bar và status ticker:

```css
/* components.css */
.neural-loader {
    height: 3px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 2px;
    overflow: hidden;
}

.neural-progress {
    height: 100%;
    background: linear-gradient(90deg, #25fecf, #3386ff);
    border-radius: 2px;
    transition: width 0.3s ease;
    box-shadow: 0 0 10px #25fecf;
    width: 0%;
}

.status-ticker {
    font-family: var(--font-main);
    font-weight: 600;
    font-size: 10px;
    letter-spacing: 2px;
    text-transform: uppercase;
    color: var(--text-secondary);
}
```

HTML template:
```html
<!-- Dùng trong onboarding.html, ticket-intelligence.html -->
<div class="neural-loader">
    <div class="neural-progress" id="progress-bar"></div>
</div>
<span class="status-ticker" id="status-text"></span>
```

Kotlin/JS controller cập nhật progress:
```kotlin
// OnboardingPage.kt
fun updateProgress(percent: Int, statusText: String) {
    (document.getElementById("progress-bar") as? HTMLElement)?.style?.width = "${percent}%"
    document.getElementById("status-text")?.textContent = statusText
}

// Ví dụ: cập nhật theo phase
updateProgress(30, "HANDSHAKING WITH JIRA...")
updateProgress(60, "VALIDATING API TOKENS...")
updateProgress(90, "FETCHING PROJECT METADATA...")
```

- **Status ticker:** Text element cập nhật theo phase qua Kotlin/JS `element.textContent`
- Áp dụng cho: Onboarding (3 phase), Ticket Analysis (3 phase), Integration TEST LINK, User Management IAM SYNC

*(Validates: Req 17.5)*

## Glassmorphism Specifications

Tất cả panel trên dashboard áp dụng glassmorphism qua CSS class `.glass-card` và `.glass-panel`:

- **Transparency range:** 0.03–0.08 cho background (tương đương opacity 0.7–0.8 khi kết hợp với deep nebula background)
- **Backdrop blur:** 10px–20px (default 16px)
- **Border:** 1px solid rgba(255,255,255, 0.06–0.1)
- **Border radius:** 12px–20px tùy kích thước component

```css
/* obsidian-kinetic.css */
.glass-panel {
    background: rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 12px;
    padding: 24px;
}
```

Deep nebula background:

```css
/* obsidian-kinetic.css */
.living-void {
    position: fixed;
    inset: 0;
    background: radial-gradient(ellipse at 20% 50%, rgba(37, 254, 207, 0.03) 0%, transparent 50%),
                radial-gradient(ellipse at 80% 20%, rgba(51, 134, 255, 0.03) 0%, transparent 50%),
                radial-gradient(ellipse at 50% 80%, rgba(190, 157, 255, 0.02) 0%, transparent 50%),
                #0a0a1a;
    z-index: -1;
}
```

HTML template sử dụng:
```html
<!-- index.html -->
<body>
    <div class="living-void"></div>
    <div class="master-container" id="root">
        <!-- App content injected by Kotlin/JS -->
    </div>
</body>
```

*(Validates: Req 17.6)*

## Vibrant Button

```css
/* components.css */
.btn-vibrant {
    background: linear-gradient(135deg, #25fecf, #3386ff);
    color: white;
    border: none;
    border-radius: 8px;
    padding: 12px 24px;
    font-family: var(--font-main);
    font-weight: 600;
    font-size: 12px;
    letter-spacing: 1px;
    text-transform: uppercase;
    cursor: pointer;
    transition: all 0.3s ease;
}

.btn-vibrant:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(37, 254, 207, 0.3);
}

.btn-vibrant:disabled {
    opacity: 0.3;
    cursor: not-allowed;
    pointer-events: none;
}
```

## Status Dot

```css
/* components.css */
.status-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    display: inline-block;
    cursor: pointer;
}

.status-dot.active {
    background: var(--success);
    box-shadow: 0 0 8px var(--success);
}

.status-dot.standby {
    background: var(--warning);
    box-shadow: 0 0 8px var(--warning);
}

.status-dot.offline {
    background: var(--danger);
    box-shadow: 0 0 8px var(--danger);
}
```

Kotlin/JS cập nhật trạng thái:
```kotlin
// IntegrationsPage.kt
fun updateStatusDot(dotElement: HTMLElement, status: String) {
    dotElement.classList.remove("active", "standby", "offline")
    dotElement.classList.add(status.lowercase())
}
```

## Neural Console

```css
/* components.css */
.neural-console {
    background: rgba(0, 0, 0, 0.3);
    border: 1px solid rgba(255, 255, 255, 0.04);
    border-radius: 8px;
    padding: 16px;
    font-family: var(--font-mono);
    font-size: 11px;
    max-height: 200px;
    overflow-y: auto;
}

.console-line {
    padding: 4px 0;
    color: var(--text-secondary);
}

.console-time {
    color: var(--text-muted);
    margin-right: 8px;
}

.console-tag {
    color: var(--primary);
    margin-right: 8px;
    font-weight: 700;
}
```
