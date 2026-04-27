# Bugfix Requirements Document

## Introduction

Sau khi user chọn project từ trang Project Select, tên project trên navbar không được cập nhật — badge vẫn hiển thị "Select Project" thay vì project key đã chọn. Đồng thời, breadcrumb hiển thị sai route "PROJECT_SELECT / PROJECT_SELECT" thay vì route của trang đích (ví dụ: "DASHBOARD / OVERVIEW"). Nguyên nhân gốc: `NavbarDropdown.renderProjectSelector()` chỉ đọc project key một lần lúc render ban đầu và không bao giờ cập nhật lại khi project thay đổi. Router navigate sang dashboard nhưng Navbar component không được refresh.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN user chọn một project từ ProjectSelectPage và hệ thống navigate sang dashboard THEN project badge trên navbar vẫn hiển thị "Select Project" thay vì project key đã chọn (ví dụ: "[PROJ]")

1.2 WHEN user chọn một project từ ProjectSelectPage và hệ thống navigate sang dashboard THEN breadcrumb hiển thị "PROJECT_SELECT / PROJECT_SELECT" thay vì "DASHBOARD / OVERVIEW" vì breadcrumb không được cập nhật sau khi route thay đổi

1.3 WHEN user đã chọn project, navigate sang trang khác (ví dụ: analysis, knowledge_graph) rồi quay lại dashboard THEN project badge vẫn hiển thị giá trị cũ từ lần render đầu tiên của navbar, không phản ánh project key hiện tại trong sessionStorage

1.4 WHEN user thay đổi project (chọn project khác qua "Change Project" dropdown) THEN project badge trên navbar không cập nhật sang project key mới vì `renderProjectSelector()` không được gọi lại

### Expected Behavior (Correct)

2.1 WHEN user chọn một project từ ProjectSelectPage và hệ thống navigate sang dashboard THEN the system SHALL cập nhật project badge trên navbar hiển thị project key đã chọn (ví dụ: "[PROJ]") ngay khi trang đích được render

2.2 WHEN user chọn một project từ ProjectSelectPage và hệ thống navigate sang dashboard THEN the system SHALL cập nhật breadcrumb hiển thị đúng route đích "DASHBOARD / OVERVIEW"

2.3 WHEN user navigate giữa các trang trong app THEN the system SHALL đảm bảo project badge luôn phản ánh project key hiện tại từ sessionStorage

2.4 WHEN user thay đổi project (chọn project khác) THEN the system SHALL cập nhật project badge trên navbar hiển thị project key mới ngay sau khi navigate hoàn tất

### Unchanged Behavior (Regression Prevention)

3.1 WHEN chưa có project nào được chọn (project key rỗng hoặc null trong sessionStorage) THEN the system SHALL CONTINUE TO hiển thị "Select Project" trên badge và click vào badge sẽ navigate đến trang project_select

3.2 WHEN user click vào project selector dropdown và chọn "Change Project" THEN the system SHALL CONTINUE TO auto-pause scan nếu đang scanning, sau đó navigate đến trang project_select

3.3 WHEN user click vào user widget dropdown (Account Settings, App Settings, Sign Out) THEN the system SHALL CONTINUE TO hoạt động bình thường không bị ảnh hưởng bởi fix

3.4 WHEN user đang ở trang standalone (login, project_select) THEN the system SHALL CONTINUE TO render trang đó không có Shell (sidebar + navbar)

3.5 WHEN user navigate giữa các trang bình thường (không liên quan đến project selection) THEN the system SHALL CONTINUE TO cập nhật breadcrumb đúng route hiện tại và sidebar active state

---

## Bug Condition Derivation

### Bug Condition Function

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type NavigationEvent
  OUTPUT: boolean
  
  // Returns true when navigation happens after a project selection
  // or when project key in sessionStorage changes between renders
  RETURN X.previousRoute = "project_select" 
    AND X.targetRoute ≠ "project_select"
    AND sessionStorage.getItem("jira_assistant_project") ≠ null
END FUNCTION
```

### Property Specification — Fix Checking

```pascal
// Property: Fix Checking — Project Badge Update After Selection
FOR ALL X WHERE isBugCondition(X) DO
  navbarBadge ← document.querySelector(".project-badge")
  projectKey ← sessionStorage.getItem("jira_assistant_project")
  breadcrumb ← document.querySelector(".breadcrumb")
  
  ASSERT navbarBadge.textContent = "[" + projectKey + "]"
    AND breadcrumb contains routeSection(X.targetRoute)
    AND breadcrumb contains routeName(X.targetRoute)
END FOR
```

### Preservation Goal

```pascal
// Property: Preservation Checking — Non-Selection Navigation Unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
  // Navigation between pages without project change,
  // standalone page rendering, user dropdown behavior,
  // and empty-project-key display all remain identical
END FOR
```
