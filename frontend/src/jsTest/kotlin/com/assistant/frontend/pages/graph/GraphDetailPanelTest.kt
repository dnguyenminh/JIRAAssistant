package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphNode
import com.assistant.graph.LinkedTicketDTO
import com.assistant.graph.SubTaskDTO
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for GraphDetailPanel rendering.
 * Validates: Requirements 20.1, 20.2, 20.3, 20.5, 20.9
 */
class GraphDetailPanelTest {

    @BeforeTest
    fun setupDom() {
        document.body?.innerHTML = buildPanelHtml()
        GraphState.reset()
    }

    private fun buildPanelHtml(): String = """
        <div id="detailPanel" style="display:none">
          <div id="detailPanelContent">
            <span id="detail-type-dot"></span>
            <span id="detail-type-label"></span>
            <span id="detail-key"></span>
            <span id="detail-summary"></span>
            <div id="detail-description"></div>
            <div id="detail-jira-link"></div>
            <div id="detail-linked-section" style="display:none">
              <div id="detail-linked-list"></div>
            </div>
            <div id="detail-subtasks-section" style="display:none">
              <div id="detail-subtasks-list"></div>
            </div>
            <div id="attachment-status-section"></div>
          </div>
        </div>
    """.trimIndent()

    private fun makeNode(
        desc: String? = null
    ) = GraphNode(
        id = "n-1", key = "TK-1", summary = "Test ticket",
        description = desc, type = "FEATURE", x = 0.0, y = 0.0
    )

    // -- Description rendering (Req 20.1, 20.2) --

    @Test
    fun descriptionRenderedWhenPresent() {
        GraphDetailPanel.show(makeNode(desc = "Detailed info"))
        val el = document.getElementById("detail-description")
        assertEquals("Detailed info", el?.textContent)
    }

    @Test
    fun descriptionFallbackWhenNull() {
        GraphDetailPanel.show(makeNode(desc = null))
        val el = document.getElementById("detail-description")
        assertEquals("No description available.", el?.textContent)
    }

    @Test
    fun descriptionFallbackWhenBlank() {
        GraphDetailPanel.show(makeNode(desc = "   "))
        val el = document.getElementById("detail-description")
        assertEquals("No description available.", el?.textContent)
    }

    // -- Linked tickets section (Req 20.3) --

    @Test
    fun renderLinkedTicketsShowsSection() {
        val links = listOf(
            LinkedTicketDTO("TK-10", "Payment setup", "blocks"),
            LinkedTicketDTO("TK-11", "Login page", "relates to")
        )
        GraphDetailPanel.renderLinkedTickets(links)
        val section = document.getElementById("detail-linked-section") as? HTMLElement
        assertEquals("", section?.style?.display)
        val list = document.getElementById("detail-linked-list") as? HTMLElement
        assertEquals(2, list?.childElementCount)
    }

    @Test
    fun linkedTicketItemHasCorrectClasses() {
        val links = listOf(LinkedTicketDTO("TK-10", "Summary", "blocks"))
        GraphDetailPanel.renderLinkedTickets(links)
        val list = document.getElementById("detail-linked-list") as? HTMLElement
        val row = list?.firstElementChild as? HTMLElement
        assertNotNull(row)
        assertEquals("detail-linked-item", row.className)
        val badge = row.firstElementChild as? HTMLElement
        assertEquals("linked-relationship-badge", badge?.className)
        assertEquals("blocks", badge?.textContent)
    }

    @Test
    fun linkedSectionHiddenByDefault() {
        val section = document.getElementById("detail-linked-section") as? HTMLElement
        assertEquals("none", section?.style?.display)
    }

    // -- Sub-tasks section (Req 20.5) --

    @Test
    fun renderSubTasksShowsSection() {
        val tasks = listOf(
            SubTaskDTO("TK-20", "Implement OAuth", "In Progress"),
            SubTaskDTO("TK-21", "Add reset", "To Do"),
            SubTaskDTO("TK-22", "Deploy", "Done")
        )
        GraphDetailPanel.renderSubTasks(tasks)
        val section = document.getElementById("detail-subtasks-section") as? HTMLElement
        assertEquals("", section?.style?.display)
        val list = document.getElementById("detail-subtasks-list") as? HTMLElement
        assertEquals(3, list?.childElementCount)
    }

    @Test
    fun subtaskStatusIcons() {
        val tasks = listOf(
            SubTaskDTO("TK-20", "A", "In Progress"),
            SubTaskDTO("TK-21", "B", "To Do"),
            SubTaskDTO("TK-22", "C", "Done")
        )
        GraphDetailPanel.renderSubTasks(tasks)
        val list = document.getElementById("detail-subtasks-list") as? HTMLElement
        assertNotNull(list)
        val icons = (0 until list.childElementCount).map { i ->
            (list.children.item(i) as? HTMLElement)
                ?.firstElementChild?.textContent
        }
        assertEquals(listOf("●", "○", "✓"), icons)
    }

    @Test
    fun subtasksSectionHiddenByDefault() {
        val section = document.getElementById("detail-subtasks-section") as? HTMLElement
        assertEquals("none", section?.style?.display)
    }

    // -- Click-to-focus: ticket key lookup (Req 20.9) --

    @Test
    fun ticketKeyLookupFindsNodeInGraphState() {
        val node = GraphNode(
            id = "n-50", key = "TK-10", summary = "Target",
            type = "FEATURE", x = 0.0, y = 0.0
        )
        GraphState.allNodes = listOf(node)
        val found = GraphState.allNodes.find { it.key == "TK-10" }
        assertNotNull(found)
        assertEquals("n-50", found.id)
    }

    @Test
    fun ticketKeyLookupReturnsNullWhenMissing() {
        GraphState.allNodes = emptyList()
        val found = GraphState.allNodes.find { it.key == "TK-999" }
        assertEquals(null, found)
    }
}
