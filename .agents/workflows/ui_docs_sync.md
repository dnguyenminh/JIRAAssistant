---
description: To synchronize UI design, technical documentation, and user guides after a feature update.
---

# UI & Documentation Synchronization Workflow

## Goal
Ensure the feature is delivered with aligned requirements, architecture, UI mockups, documentation, and QA coverage.

## Roles
- **BA**: cập nhật yêu cầu và đặc tả.
- **Architect**: cập nhật thiết kế kỹ thuật.
- **Frontend-designer**: tạo và hiệu chỉnh mockup bằng Stitch/MCP.
- **Technical-writer**: viết hướng dẫn sử dụng tiếng Việt.
- **QA**: đồng bộ kịch bản kiểm thử và Gherkin.

## Role Checklist
- **BA**
  - [ ] Xác nhận yêu cầu mới và phân loại phạm vi thay đổi.
  - [ ] Cập nhật `docs/BA/SRS.md` và `docs/BA/BRD.md`.
  - [ ] Ghi rõ điều kiện chấp nhận (acceptance criteria).
- **Architect**
  - [ ] Xác minh yêu cầu BA và kiểm tra tính khả thi kỹ thuật.
  - [ ] Cập nhật `docs/Architect/TechnicalDesign.md`.
  - [ ] Liệt kê các thay đổi về module, API, luồng dữ liệu và UI.
- **Frontend-designer**
  - [ ] Chọn Stitch project phù hợp qua `mcp_stitch_list_projects`.
  - [ ] Tạo mockup ban đầu bằng `mcp_stitch_generate_screen_from_text`.
  - [ ] Tinh chỉnh bằng `mcp_stitch_edit_screens`.
  - [ ] Lưu trữ link/asset và ghi chú trong tài liệu.
- **Technical-writer**
  - [ ] Viết hoặc cập nhật `docs/Guides/UserGuide_StepByStep_VN.md`.
  - [ ] Nhúng mockup và giải thích rõ từng bước.
  - [ ] Đảm bảo nội dung phù hợp với người dùng cuối.
- **QA**
  - [ ] Cập nhật `docs/QA/TestScenarios.md`.
  - [ ] Đồng bộ Gherkin feature trong `e2e-tests/src/test/resources/features/`.
  - [ ] Chạy test và xác nhận kết quả pass.

## Workflow Steps

- [ ] **Requirement Update (BA)**
  - [ ] Cập nhật `docs/BA/SRS.md` và `docs/BA/BRD.md` với yêu cầu mới.
  - [ ] Mô tả rõ: mục tiêu tính năng, người dùng, luồng nghiệp vụ, điều kiện chấp nhận.
  - [ ] Xác nhận nội dung tài liệu phản ánh đúng yêu cầu và phạm vi thay đổi.

- [ ] **Architecture Alignment (Architect)**
  - [ ] Cập nhật `docs/Architect/TechnicalDesign.md`.
  - [ ] Ghi rõ: cấu trúc module, luồng dữ liệu, tích hợp với Jira, sự thay đổi trong UI/UX.
  - [ ] Đảm bảo thiết kế kỹ thuật đủ để developer triển khai và reviewer chấp nhận.

- [ ] **Mockup Generation (Frontend-designer)**
  - [ ] Dùng Stitch/MCP với server `@stitch` trong `.vscode/mcp.json`.
  - [ ] Chọn Stitch project phù hợp qua `mcp_stitch_list_projects`.
  - [ ] Tạo mockup ban đầu bằng `mcp_stitch_generate_screen_from_text`.
  - [ ] Tinh chỉnh mockup bằng `mcp_stitch_edit_screens`.
  - [ ] Ghi chú mockup vào tài liệu và tham chiếu đúng asset/desktop screen.
  - [ ] Hoàn thành khi mockup phản ánh yêu cầu và có thể dùng cho user guide.

- [ ] **User Guide Construction (Technical-writer)**
  - [ ] Cập nhật `docs/Guides/UserGuide_StepByStep_VN.md` bằng tiếng Việt.
  - [ ] Nhúng mockup từ bước 3.
  - [ ] Viết rõ các bước sử dụng (B01, B02, B03, B04).
  - [ ] Đảm bảo guide mô tả chính xác quy trình người dùng và dễ hiểu.

- [ ] **QA Synchronization (QA)**
  - [ ] Cập nhật `docs/QA/TestScenarios.md` cho đúng với User Guide.
  - [ ] Cập nhật file feature trong `e2e-tests/src/test/resources/features/`.
  - [ ] Chạy kiểm thử:
      - [ ] `./gradlew e2e-tests:test`
      - [ ] `./gradlew e2e-tests:aggregate`
  - [ ] Hoàn thành khi các kịch bản test khớp với Guide và trạng thái test pass.

- [ ] **Final Review**
  - [ ] Kiểm tra tính nhất quán giữa:
      - [ ] `docs/BA/SRS.md`, `docs/BA/BRD.md`
      - [ ] `docs/Architect/TechnicalDesign.md`
      - [ ] `docs/Guides/UserGuide_StepByStep_VN.md`
      - [ ] `docs/QA/TestScenarios.md`
      - [ ] `e2e-tests` feature files
  - [ ] Đảm bảo mockup và tài liệu đều tham chiếu đúng nhau.
  - [ ] Hoàn thành khi mọi bên đều thỏa thuận và mọi tài liệu đã được cập nhật.

## Notes
- Đây là workflow thủ công, không phải script tự động.
- Mỗi bước nên có người chịu trách nhiệm rõ ràng.
- Nếu cần, bổ sung thêm checklist riêng cho mỗi role.
