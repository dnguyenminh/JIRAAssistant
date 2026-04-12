# Design System & UX Standards — Requirements

# Yêu cầu 17: Giao diện Obsidian Kinetic Design System

**User Story:** Là một người dùng, tôi muốn giao diện ứng dụng nhất quán với design system Obsidian Kinetic, để có trải nghiệm sử dụng chuyên nghiệp và hiện đại.

## Tiêu chí chấp nhận

1. THE Frontend_App SHALL sử dụng design system Obsidian Kinetic (Luminous V3) với glassmorphism, deep nebula backgrounds, và neon accents cho tất cả các màn hình, triển khai qua CSS files (master_style.css và các CSS file riêng cho từng màn hình)
2. THE Frontend_App SHALL sử dụng font chính Be Vietnam Pro (100/300/400/600/700) và font monospace JetBrains Mono cho các phần tử console, khai báo qua Google Fonts link trong HTML template gốc
3. THE Frontend_App SHALL áp dụng hiệu ứng hover cho tất cả glass card qua CSS pseudo-class `:hover`: translateY -5px, scale 1.02 với transition mượt mà, định nghĩa trong CSS file của design system
4. THE Frontend_App SHALL hiển thị glass-styled tooltip qua CSS class `.glass-tooltip` kết hợp Kotlin/JS event handlers (addEventListener cho mouseenter/mouseleave) để toggle visibility trên DOM elements
5. WHILE hệ thống đang xử lý tác vụ nặng (phân tích AI, kết nối provider, đồng bộ KB), THE Frontend_App SHALL hiển thị progress bar và status ticker trong HTML template, cập nhật qua Kotlin/JS DOM manipulation (element.style.width, element.textContent)
6. THE Frontend_App SHALL áp dụng glassmorphism (transparency 0.7-0.8 với backdrop-blur 10px-20px) cho tất cả panel trên dashboard, triển khai qua CSS classes trong file design system (.glass-card, .glass-panel)
7. THE Frontend_App SHALL được viết theo kiến trúc Kotlin/JS + HTML Templates: HTML files (.html) cho layout và structure của từng màn hình, CSS files cho styling và animations, Kotlin/JS files cho business logic và data binding qua DOM APIs, và Vite làm bundler/dev server với HMR (Hot Module Replacement)
8. THE Frontend_App SHALL chia sẻ domain models (data classes, DTOs) với Backend_Server thông qua shared module Kotlin Multiplatform, đảm bảo type-safety end-to-end giữa Kotlin/JS controller layer và Backend

---

# Yêu cầu 20: UX Standards — Error Handling & User Feedback

## Giới thiệu

Mọi thao tác trong ứng dụng phải cung cấp feedback rõ ràng cho người dùng. Không được fail silently — mọi lỗi, trạng thái trống, và kết quả bất thường phải được hiển thị với message cụ thể và hành động gợi ý.

---

## Tiêu chí chấp nhận

**20.1** WHEN bất kỳ API call nào thất bại, THE Frontend_App SHALL hiển thị error banner với message lỗi cụ thể, nút RETRY, và nút điều hướng đến trang liên quan (ví dụ: "GO TO INTEGRATIONS" khi Jira lỗi).

**20.2** WHEN một trang hiển thị dữ liệu trống (0 items), THE Frontend_App SHALL hiển thị message giải thích nguyên nhân và gợi ý hành động (ví dụ: "No tickets found. This project may be empty — try switching to a different project.").

**20.3** WHEN scan hoàn tất với 0 tickets, THE Backend_Server SHALL ghi log entry giải thích nguyên nhân vào scan_log, và THE Frontend_App SHALL hiển thị message rõ ràng thay vì chỉ "Scan completed."

**20.4** THE Backend_Server SHALL KHÔNG BAO GIỜ trả về empty result mà không log nguyên nhân. Mọi catch block phải log error message trước khi trả empty.

**20.5** WHEN người dùng chưa chọn project (project key trống), THE Frontend_App SHALL hiển thị message "No project selected" thay vì fail silently.

**20.6** WHEN scan đang chạy, THE Frontend_App SHALL hiển thị scan log với timestamp readable (HH:mm:ss), ticket ID, status badge (COMPLETED/FAILED/ANALYZING), và message chi tiết.

**20.7** WHEN scan bắt đầu mới, THE Backend_Server SHALL xóa scan log cũ của project trước khi tạo log entries mới.

**20.8** WHEN scan hoàn tất thành công, THE Frontend_App SHALL hiển thị "Scan completed — N tickets processed." với số tickets cụ thể.

### Database Schema Migration

**20.9** WHEN SQLDelight schema thay đổi (thêm bảng mới như `chat_messages`), THE Backend_Server SHALL tự động tạo bảng mới khi khởi động nếu chưa tồn tại, HOẶC yêu cầu xóa DB file cũ để tạo lại. Lỗi `no such table` PHẢI được log rõ ràng với hướng dẫn khắc phục.

### Blocking Overlay — Async Operation Feedback

**20.10** WHEN bất kỳ async operation nào được kích hoạt (SAVE, TEST, DELETE, START, STOP, hoặc bất kỳ API call nào mất hơn 200ms), THE Frontend_App SHALL hiển thị blocking overlay trên container chứa action đó. Overlay PHẢI bao gồm: spinner animation, message mô tả hành động đang thực hiện (ví dụ: "Saving...", "Testing connection...", "Removing..."), và PHẢI chặn mọi tương tác của user với container bên dưới cho đến khi operation hoàn tất hoặc thất bại.

**20.11** THE Frontend_App SHALL sử dụng component `BlockingOverlay` (tại `components/BlockingOverlay.kt`) cho tất cả async operations. Gọi `BlockingOverlay.show(containerId, message)` TRƯỚC khi bắt đầu operation và `BlockingOverlay.remove(containerId)` trong block `finally` để đảm bảo overlay luôn được gỡ bỏ kể cả khi có exception.

**20.12** THE Frontend_App SHALL KHÔNG BAO GIỜ cho phép user click cùng một action button nhiều lần trong khi operation đang chạy. BlockingOverlay đảm bảo điều này bằng cách phủ lên toàn bộ container, ngăn chặn mọi click event đến các element bên dưới.

---

**Tổng: 12 tiêu chí chấp nhận**
