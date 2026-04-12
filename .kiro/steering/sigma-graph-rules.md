---
inclusion: fileMatch
fileMatchPattern: "frontend/**/graph/**"
---

# Graph Rendering — Cytoscape.js Quy tắc bắt buộc

## ⛔ KHÔNG dùng Sigma.js

Sigma.js đã bị loại bỏ do các vấn đề không fix được: node drag coordinate conversion sai, filter rebuild mất positions, camera fit hidden nodes. Dùng **Cytoscape.js** thay thế.

## ⛔ Filter PHẢI dùng hide/show, KHÔNG rebuild graph

```kotlin
// ❌ CẤM — Rebuild graph khi filter (mất positions, gây collapse)
graph.clear()
for (node in visibleNodes) graph.addNode(node.id, attrs)

// ✅ ĐÚNG — Cytoscape native hide/show
cy.nodes().show()  // show all first
cy.nodes().filter { it !in filteredIds }.hide()  // hide non-matching
cy.layout(layoutOptions).run()  // re-layout visible only
cy.fit(cy.nodes(":visible"), 40)  // fit camera to visible
```

## ⛔ Node drag PHẢI dùng Cytoscape built-in

Cytoscape.js hỗ trợ node drag native — KHÔNG cần custom mouse event handling.

```kotlin
// ❌ CẤM — Custom drag qua mousemove (coordinate bugs)
container.addEventListener("mousemove", { e ->
    val coords = sigma.viewportToGraph(e)
    graph.setNodeAttribute(nodeId, "x", coords.x)
})

// ✅ ĐÚNG — Cytoscape native (tự động, không cần code)
// Node drag hoạt động mặc định khi grabbable: true
```

## ⛔ Auto-layout PHẢI dùng Cytoscape layout engine

```kotlin
// ✅ ĐÚNG — Cytoscape built-in layouts
// Force-directed cho full graph:
cy.layout(js("({name:'cose', animate:true, animationDuration:300})")).run()

// Circle cho small filtered sets:
cy.layout(js("({name:'circle', animate:true})")).run()
```

## ⛔ Camera fit PHẢI dùng cy.fit()

```kotlin
// ❌ CẤM — Manual camera animation (coordinate system mismatch)
camera.animate({x: cx, y: cy, ratio: 0.3})

// ✅ ĐÚNG — Cytoscape native fit
cy.fit(cy.nodes(":visible"), 40)  // 40px padding
```

## ⛔ Hover highlight dùng Cytoscape events

```kotlin
// ✅ ĐÚNG
cy.on("mouseover", "node") { e -> highlightNode(e.target) }
cy.on("mouseout", "node") { e -> resetHighlight() }
```

## ⛔ Gradle build cho frontend

```bash
# Compile only:
./gradlew :frontend:compileKotlinJs

# Compile + webpack (cần cho Vite dev server):
./gradlew :frontend:jsBrowserDevelopmentWebpack

# PHẢI chạy webpack sau mỗi thay đổi code để Vite serve bundle mới
```

## Checklist khi sửa graph code

- [ ] Dùng Cytoscape.js, KHÔNG dùng Sigma.js?
- [ ] Filter dùng hide/show, KHÔNG rebuild graph?
- [ ] Node drag là Cytoscape native?
- [ ] Auto-layout dùng Cytoscape layout engine?
- [ ] Camera fit dùng cy.fit()?
- [ ] Chạy `jsBrowserDevelopmentWebpack` sau khi sửa code?
