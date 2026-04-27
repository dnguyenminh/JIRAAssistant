---
inclusion: always
---

# Post-Implementation Manual Testing Rule

## Quy tắc bắt buộc

Sau khi hoàn thành **bất kỳ** bug fix hoặc feature implementation nào ảnh hưởng đến UI hoặc API, agent **PHẢI tự thực hiện** toàn bộ quy trình sau (KHÔNG yêu cầu user làm):

1. **Compile** các module bị ảnh hưởng:
   - Frontend: `./gradlew :frontend:compileKotlinJs`
   - Backend: `./gradlew :server:compileKotlinJvm`
   - Shared: `./gradlew :shared:compileKotlinJvm` (nếu thay đổi shared module)

2. **Restart server** — agent tự stop server cũ và start lại:
   - Dùng `controlPwshProcess` action="stop" để stop server đang chạy
   - Dùng `controlPwshProcess` action="start" với command `./gradlew :server:jvmRun` để start server mới
   - Đợi server ready (check log "Application started" hoặc đợi 10s)

3. **Gọi @jira-master-agent** để test manual trên browser tại `localhost:3000`:
   - Mở browser, login, navigate đến trang bị ảnh hưởng
   - Test chức năng vừa fix/implement
   - Kiểm tra console errors (F12 → Console)
   - Chụp screenshot minh chứng
   - Báo cáo PASS/FAIL cho từng test case

4. **Nếu test FAIL** → fix ngay, recompile, restart server, test lại. Lặp lại cho đến khi PASS.

5. **Chỉ báo cáo hoàn thành cho user** khi tất cả tests PASS.

## Khi nào áp dụng

- Sau mỗi bug fix (frontend hoặc backend)
- Sau mỗi feature implementation
- Sau mỗi thay đổi code ảnh hưởng đến UI, API endpoints, hoặc data flow
- Sau mỗi thay đổi CSS/HTML templates

## Khi nào KHÔNG áp dụng

- Thay đổi chỉ ảnh hưởng đến tests (test files only)
- Thay đổi documentation/specs only
- Thay đổi steering rules/hooks

## Checklist test tối thiểu

- [ ] Trang load thành công, không JS errors trong console
- [ ] Chức năng vừa fix/implement hoạt động đúng
- [ ] Không có regression trên các chức năng liên quan
- [ ] Blocking overlay hiển thị/tắt đúng thời điểm (nếu có async operation)
- [ ] Button states đúng (enabled/disabled, text đúng)
- [ ] Data hiển thị đúng trong UI

## Quy trình báo cáo

```
### Test Report
- **Chức năng**: [mô tả ngắn]
- **Trang**: [URL]
- **Kết quả**: ✅ PASS / ❌ FAIL
- **Chi tiết**: [mô tả behavior observed]
- **Console errors**: Có / Không
- **Screenshot**: [tên file]
```
