---
inclusion: always
---

# Parallel Task Execution Rule

## Quy tắc

Khi thực thi các tasks trong spec, agent PHẢI tối ưu tốc độ bằng cách:

1. **Chạy song song** các task độc lập (không phụ thuộc lẫn nhau) càng nhiều càng tốt
2. **Gộp các task liên quan** vào cùng một lần delegate để giảm overhead
3. **Không chờ đợi không cần thiết** — nếu task A không phụ thuộc task B, chạy cả hai cùng lúc
4. **Ưu tiên tốc độ** — chọn cách tiếp cận nhanh nhất có thể

## Ví dụ

- Task "implement fix" và "write tests" có thể chạy song song nếu tests không cần code đã fix
- Task "compile" và "run tests" phải chạy tuần tự (tests cần compile trước)
- Nhiều file edits độc lập nên được thực hiện song song trong cùng một turn

## Áp dụng

- Khi user yêu cầu "run all tasks" hoặc "execute tasks"
- Khi thực thi spec tasks
- Khi có nhiều thay đổi code độc lập cần thực hiện
