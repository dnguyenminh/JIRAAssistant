Kotlin là một lựa chọn tuyệt vời cho AI Agent, đặc biệt khi bạn đã có nền tảng Java. Kotlin giúp code ngắn gọn hơn, xử lý `null-safety` tốt và đặc biệt mạnh mẽ khi kết hợp với **Coroutines** để xử lý các tác vụ bất đồng bộ (I/O bound) như gọi API Jira hoặc xử lý file.

Dưới đây là kiến trúc và cách triển khai Agent phân tích Jira bằng Kotlin.

### 1. Cấu trúc bộ nhớ "Workspace" (Structured Memory)
Trong bài toán này, Agent cần một bộ nhớ có cấu trúc để phân loại dữ liệu thay vì lưu trữ hỗn độn.

```kotlin
data class JiraContextMemory(
    val ticketId: String,
    var summary: String = "",
    var description: String = "",
    val comments: MutableList<String> = mutableListOf(),
    val attachmentsData: MutableList<String> = mutableListOf(), // Text extracted from files/images
    val linkedTickets: MutableMap<String, String> = mutableMapOf(),
    var businessGoals: String = ""
)
```

---

### 2. Định nghĩa các "Cánh tay" (Tools) trong Kotlin
Sử dụng thư viện **LangChain4j** bản Kotlin-friendly hoặc gọi trực tiếp qua Ktor/Retrofit.

```kotlin
class JiraAgentTools {
    
    @Tool("Đọc thông tin cơ bản và comment của Jira ticket")
    fun fetchJiraDetails(issueKey: String): String {
        // Gọi Jira REST API (Summary, Desc, Comments)
        return "Nội dung ticket..."
    }

    @Tool("Lấy danh sách các ticket liên quan (Linked Issues)")
    fun getLinkedIssues(issueKey: String): List<String> {
        // Trả về danh sách key như ["PROJ-123", "PROJ-456"]
        return listOf()
    }

    @Tool("Trích xuất nội dung từ file đính kèm (PDF, Image, Log)")
    fun processAttachment(attachmentId: String): String {
        // Sử dụng OCR hoặc Vision Model để đọc ảnh/file
        return "Dữ liệu từ file..."
    }
}
```

---

### 3. Triển khai Vòng lặp Agent (The Thinking Loop)
Sử dụng Coroutines để Agent có thể thực hiện nhiều bước thu thập dữ liệu song song (Parallel Tool Calling).

```kotlin
interface JiraAnalystAgent {
    @SystemMessage("""
        Bạn là một chuyên gia Business Analyst. 
        Nhiệm vụ của bạn:
        1. Sử dụng công cụ để thu thập TẤT CẢ thông tin từ Jira ticket và các ticket liên kết.
        2. Phân tích các file đính kèm để hiểu luồng nghiệp vụ (Flow) hoặc UI/UX.
        3. Tổng hợp lại thành một siêu Prompt (Master Prompt) để tạo BRD, FSD và Slide.
    """)
    fun analyze(userRequirement: String): String
}
```

---

### 4. Cách Agent "Xây dựng Trí nhớ" để tạo Master Prompt
Đây là phần quan trọng nhất: Cách Agent sử dụng bộ nhớ để tạo ra Output cuối cùng.

**Quy trình tư duy của Agent:**
1.  **Bước 1 (Collect):** Gọi `fetchJiraDetails`. Lưu vào `Memory.summary`.
2.  **Bước 2 (Expand):** Thấy có Linked Tickets? Gọi `getLinkedIssues`. Với mỗi ticket, gọi `fetchJiraDetails` tiếp.
3.  **Bước 3 (Visualize):** Thấy có ảnh đính kèm? Gọi `processAttachment` để hiểu mockup.
4.  **Bước 4 (Synthesize):** Dùng dữ liệu trong `JiraContextMemory` để render ra 3 Master Prompts.



---

### 5. Tại sao Kotlin lại phù hợp cho Agent này?

* **Coroutines & Flows:** Khi Agent đọc 10 ticket liên kết, bạn có thể dùng `async/await` để fetch dữ liệu cực nhanh mà không tốn tài nguyên như Thread truyền thống.
* **Sealed Classes:** Dùng để quản lý các trạng thái của Agent (e.g., `Searching`, `Analyzing`, `GeneratingPrompt`, `Error`).
* **Extension Functions:** Bạn có thể viết thêm các hàm mở rộng cho `String` hoặc `JsonElement` để làm sạch dữ liệu thô từ Jira API trước khi đưa vào Memory.

### 6. Cấu trúc Output (Master Prompt) mà Agent sẽ trả về
Sau khi chạy xong, Agent sẽ không trả về BRD ngay (để tránh giới hạn token), mà trả về một cấu trúc như sau:

> **[PROMPT FOR BRD]**
> "Dựa trên các thông tin sau: {Summary}, {Description}, {Comments}. Hãy viết BRD tập trung vào mục tiêu kinh doanh..."
>
> **[PROMPT FOR FSD]**
> "Dựa trên technical constraints tìm thấy ở {Linked_Ticket_Technical}, hãy viết FSD chi tiết về API và Database Schema..."
>
> **[PROMPT FOR SLIDE]**
> "Tóm tắt 5 ý chính từ {Summary} và {Attachments} để tạo slide thuyết trình cho Stakeholders..."

Bạn muốn tôi viết chi tiết phần code xử lý **Coroutines** để Agent gọi nhiều Tool cùng lúc nhằm tăng tốc độ thu thập thông tin không?