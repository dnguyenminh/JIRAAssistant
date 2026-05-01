# Knowledge Graph 3D — Requirements

---

## Yêu cầu 3: Knowledge Graph — Mạng lưới Quan hệ Ticket (MH3)

**User Story:** Là một Product Owner, tôi muốn xem đồ thị trực quan mạng lưới quan hệ giữa các ticket, để nhận diện nhanh các cụm tính năng và phụ thuộc ẩn.

### Tiêu chí chấp nhận

1. THE Graph_Engine SHALL hiển thị các Jira ticket dưới dạng node hình lục giác (hexagonal) với ticket key làm nhãn
2. THE Graph_Engine SHALL phân biệt node theo màu sắc: Cyan cho Feature, Blue cho Dependency, Violet cho UI Module
3. THE Graph_Engine SHALL phân biệt đường nối: nét liền (solid) cho quan hệ tường minh từ Jira, nét đứt (dashed) cho quan hệ ngữ nghĩa do AI phát hiện
4. WHEN người dùng hover lên một node, THE Frontend_App SHALL phóng to node 1.1x với viền trắng highlight
5. WHEN người dùng nhấn vào một node, THE Frontend_App SHALL hiển thị panel chi tiết 300px bên phải gồm: Ticket Key, Summary, Description, và nút "OPEN IN JIRA"
6. THE Frontend_App SHALL cung cấp ô tìm kiếm để lọc node theo ticket key hoặc nội dung summary
7. THE Graph_Engine SHALL triển khai thuật toán force-directed layout để tự động sắp xếp vị trí node, đảm bảo các node liên quan nằm gần nhau
8. WHILE đồ thị đang hiển thị, THE Graph_Engine SHALL hỗ trợ thao tác zoom (phóng to/thu nhỏ) và pan (kéo di chuyển) bằng chuột
9. THE Graph_Engine SHALL render đồ thị với tối đa 100 node trong thời gian dưới 3 giây trên trình duyệt Chrome phiên bản hiện hành
10. WHEN đồ thị chứa từ 2 cụm tính năng trở lên, THE Graph_Engine SHALL phân biệt các cụm bằng màu sắc riêng biệt và đường viền bao quanh

### Hiển thị Dữ liệu Tăng dần (Progressive Display)

11. WHILE Batch_Scan_Engine đang quét dự án, THE Graph_Engine SHALL hiển thị các node và edge đã được phân tích lên đồ thị ngay khi có dữ liệu mới, thay vì chờ toàn bộ quá trình quét hoàn tất
12. WHEN một ticket mới được phân tích xong trong quá trình quét, THE Graph_Engine SHALL thêm node và các edge liên quan vào đồ thị hiện tại với hiệu ứng fadeIn, đồng thời cập nhật lại force-directed layout
13. THE Frontend_App SHALL hiển thị chỉ số tổng số node hiện tại trên đồ thị và trạng thái quét (đang quét / đã hoàn tất) ở góc trên của trang Knowledge Graph

### Trực quan hóa 3D Perspective SVG (3D Visualization)

14. THE Graph_Engine SHALL tính toán tọa độ 3 chiều (x, y, z) cho mỗi node trong force-directed layout, trong đó z đại diện cho chiều sâu không gian
15. THE Frontend_App SHALL chiếu (project) tọa độ 3D sang 2D SVG sử dụng phép biến đổi phối cảnh (perspective projection): `screenX = x * focalLength / (z + focalLength)`, `screenY = y * focalLength / (z + focalLength)`
16. WHEN node có giá trị z lớn hơn (xa hơn), THE Frontend_App SHALL render node nhỏ hơn và mờ hơn (depth cue) để tạo cảm giác chiều sâu
17. WHEN người dùng kéo chuột (mouse drag) trên đồ thị, THE Frontend_App SHALL xoay toàn bộ đồ thị quanh trục Y (kéo ngang) và trục X (kéo dọc) với animation mượt qua requestAnimationFrame
18. WHEN người dùng cuộn chuột (mouse wheel) trên đồ thị, THE Frontend_App SHALL điều chỉnh khoảng cách phối cảnh (focal length) để zoom in/out, thay thế cơ chế zoom viewBox cũ
19. THE Frontend_App SHALL render các edge dưới dạng SVG line với gradient opacity dựa trên z-depth trung bình của source và target node
20. THE Frontend_App SHALL áp dụng level-of-detail (LOD) cho đồ thị 1800+ node: chỉ hiển thị label cho node gần camera (z thấp) hoặc node lớn, node xa chỉ render dưới dạng chấm nhỏ
21. THE Frontend_App SHALL render một lưới tham chiếu (reference grid) tại mặt phẳng z=0 với opacity thấp để tạo cảm giác không gian 3D
22. THE Frontend_App SHALL sắp xếp thứ tự render SVG elements theo z-depth (painter's algorithm): node xa render trước, node gần render sau để đảm bảo occlusion đúng
23. THE Frontend_App SHALL áp dụng hiệu ứng neon glow cho node (CSS filter hoặc SVG filter) với cường độ glow tỷ lệ nghịch với z-depth
24. THE Graph_Engine SHALL render đồ thị 3D với 1800+ node đạt tối thiểu 30fps khi xoay trên trình duyệt Chrome phiên bản hiện hành
25. WHEN người dùng nhấn vào một node trong chế độ 3D, THE Frontend_App SHALL vẫn hiển thị panel chi tiết 300px bên phải (giữ nguyên chức năng hiện tại)

### Mạng lưới Quan hệ Dựa trên Dữ liệu Jira (Data-Driven Network)

26. THE FeatureNetworkMapper SHALL trích xuất quan hệ tường minh (explicit edges) từ Jira issue links (`issuelinks` field): blocks, relates to, duplicates, clones, causes — mỗi link tạo một edge với type `link:{linkTypeName}`
27. THE FeatureNetworkMapper SHALL trích xuất quan hệ phân cấp (hierarchy edges) từ `parent` và `subtasks` fields: mỗi cặp parent-child tạo một edge với type `parent_child`
28. THE FeatureNetworkMapper SHALL trích xuất quan hệ heuristic (keyword edges) từ summary: tickets có chung từ khóa quan trọng (>3 ký tự, loại stop words) được nối với type `keyword_similarity`
29. THE JiraRestClient SHALL request các fields bổ sung `description,parent,subtasks,issuelinks,attachment` khi fetch issues để cung cấp dữ liệu cho network graph
30. THE FeatureNetworkMapper SHALL chỉ tạo edge giữa các node thuộc cùng project (cả source và target phải nằm trong danh sách issues đã fetch)

### Zoom Controls UI

31. THE Frontend_App SHALL hiển thị 3 nút điều khiển zoom ở góc dưới phải đồ thị: Zoom In (+), Zoom Out (−), và Reset View (⟲)
32. WHEN người dùng nhấn nút Reset View, THE Frontend_App SHALL trả rotation và focal length về giá trị mặc định (rotationX=0.3, rotationY=0, focalLength=800)

---

**Tổng: 32 tiêu chí chấp nhận**
