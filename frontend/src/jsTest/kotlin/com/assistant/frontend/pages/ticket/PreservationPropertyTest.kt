package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.TicketPageState
import com.assistant.scan.TicketAnalysisState
import com.assistant.scan.TicketAnalysisStatus
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 2: Preservation — Existing Ticket Selection &
 * Interaction Behavior.
 *
 * Observation-first methodology: these tests capture CURRENT
 * correct behavior on UNFIXED code. They MUST PASS before AND
 * after the bugfix to confirm no regression.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
 */
class PreservationPropertyTest {

    private val STORAGE_KEY = "ticket_intelligence_state"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private fun comboboxHtml(): String = """
        <div id="ticket-combobox">
            <input type="text" id="ticket-search" value="">
        </div>
        <div id="outside-area" style="width:100px;height:100px;"></div>
    """.trimIndent()

    @BeforeTest
    fun setup() {
        document.body?.innerHTML = comboboxHtml()
        TicketCombobox.reset()
        window.sessionStorage.removeItem(STORAGE_KEY)
    }

    // -- Test data generators --

    private fun makeTicket(
        id: String,
        summary: String,
        state: TicketAnalysisState = TicketAnalysisState.ANALYZED
    ) = TicketAnalysisStatus(
        ticketId = id,
        ticketSummary = summary,
        analysisState = state
    )

    private val sampleTickets = listOf(
        makeTicket("ICL2-1001", "Fix login timeout"),
        makeTicket("ICL2-2002", "Update dashboard layout"),
        makeTicket("ICL2-3003", "Refactor API client"),
        makeTicket("ICL2-4004", "Add unit tests", TicketAnalysisState.NOT_ANALYZED),
        makeTicket("ICL2-5005", "Improve error handling", TicketAnalysisState.SCANNED)
    )

    private fun randomTicket(rng: Random): TicketAnalysisStatus {
        return sampleTickets[rng.nextInt(sampleTickets.size)]
    }

    private fun randomQuery(rng: Random): String {
        val queries = listOf(
            "ICL2", "login", "dashboard", "API", "error",
            "1001", "unit", "refactor", "fix", "update",
            "NONEXISTENT", "zzz", "999"
        )
        return queries[rng.nextInt(queries.size)]
    }

    // ── Property: selectTicket() preserves correct behavior ──

    /**
     * Observed behavior: For all tickets in ticketList,
     * calling selectTicket(ticket) →
     *   selectedTicket == ticket
     *   AND input value == "${ticket.ticketId} — ${ticket.ticketSummary}"
     *   AND state saved to sessionStorage
     */
    @Test
    fun selectTicketUpdatesStateAndInput() {
        val rng = Random(seed = 42)
        repeat(30) { i ->
            document.body?.innerHTML = comboboxHtml()
            TicketCombobox.reset()
            window.sessionStorage.removeItem(STORAGE_KEY)

            TicketCombobox.ticketList = sampleTickets
            TicketCombobox.filteredTickets = sampleTickets

            val ticket = randomTicket(rng)
            TicketCombobox.selectTicket(ticket)

            // selectedTicket must be the chosen ticket
            assertEquals(
                ticket.ticketId,
                TicketCombobox.selectedTicket?.ticketId,
                "Iter $i: selectedTicket must be '${ticket.ticketId}'"
            )

            // Input value must show "ticketId — summary"
            val input = document.getElementById("ticket-search")
                as? HTMLInputElement
            val expected = "${ticket.ticketId} — ${ticket.ticketSummary}"
            assertEquals(
                expected,
                input?.value,
                "Iter $i: input must show '$expected'"
            )

            // State must be saved to sessionStorage
            val raw = window.sessionStorage.getItem(STORAGE_KEY)
            assertTrue(
                raw != null && raw.contains(ticket.ticketId),
                "Iter $i: sessionStorage must contain '${ticket.ticketId}'"
            )
        }
    }

    // ── Property: filterTickets() preserves correct behavior ──

    /**
     * Observed behavior: For all query strings,
     * filterTickets(query) → filteredTickets only contains tickets
     * with ticketId or ticketSummary containing query (case-insensitive)
     */
    @Test
    fun filterTicketsReturnsOnlyMatchingTickets() {
        val rng = Random(seed = 77)
        repeat(30) { i ->
            document.body?.innerHTML = comboboxHtml()
            TicketCombobox.reset()

            TicketCombobox.ticketList = sampleTickets
            TicketCombobox.filteredTickets = sampleTickets

            val query = randomQuery(rng)
            TicketCombobox.filterTickets(query)

            val q = query.trim().lowercase()
            val filtered = TicketCombobox.filteredTickets

            if (q.isEmpty()) {
                // Empty query → all tickets
                assertEquals(
                    sampleTickets.size,
                    filtered.size,
                    "Iter $i: empty query must return all tickets"
                )
            } else {
                // Every filtered ticket must match query
                for (t in filtered) {
                    val matches = t.ticketId.lowercase().contains(q)
                        || t.ticketSummary.lowercase().contains(q)
                    assertTrue(
                        matches,
                        "Iter $i: '${t.ticketId}' must match '$query'"
                    )
                }
                // Every non-filtered ticket must NOT match
                val excluded = sampleTickets - filtered.toSet()
                for (t in excluded) {
                    val matches = t.ticketId.lowercase().contains(q)
                        || t.ticketSummary.lowercase().contains(q)
                    assertTrue(
                        !matches,
                        "Iter $i: '${t.ticketId}' should NOT match '$query'"
                    )
                }
            }
        }
    }

    // ── Property: click inside combobox does NOT hide dropdown ──

    /**
     * Observed behavior: For all click events INSIDE combobox
     * or dropdown → hideDropdown() NOT called (dropdown stays visible)
     */
    @Test
    fun clickInsideComboboxDoesNotHideDropdown() {
        document.body?.innerHTML = comboboxHtml()
        TicketCombobox.reset()

        TicketCombobox.ticketList = sampleTickets
        TicketCombobox.filteredTickets = sampleTickets

        // Show dropdown by filtering
        TicketCombobox.filterTickets("")

        val dd = document.getElementById("ticket-dropdown") as? HTMLElement
        // Dropdown should be visible after filterTickets
        assertTrue(
            dd != null && dd.style.display != "none",
            "Dropdown must be visible after filterTickets"
        )

        // Click inside combobox
        val combobox = document.getElementById("ticket-combobox")
            ?: return
        val event = document.createEvent("MouseEvent")
        event.asDynamic().initEvent("click", true, true)
        combobox.dispatchEvent(event)

        // Dropdown must still be visible
        val ddAfter = document.getElementById("ticket-dropdown") as? HTMLElement
        assertTrue(
            ddAfter != null && ddAfter.style.display != "none",
            "Dropdown must remain visible after click inside combobox"
        )
    }

    // ── Property: empty ticketList + no state → input empty ──

    /**
     * Observed behavior: When ticketList empty and no state →
     * combobox input value empty
     */
    @Test
    fun emptyTicketListAndNoStateShowsEmptyInput() {
        val rng = Random(seed = 99)
        repeat(10) { i ->
            document.body?.innerHTML = comboboxHtml()
            TicketCombobox.reset()
            window.sessionStorage.removeItem(STORAGE_KEY)

            // ticketList is empty (first load, no state)
            assertTrue(
                TicketCombobox.ticketList.isEmpty(),
                "Iter $i: ticketList must be empty"
            )
            assertTrue(
                TicketCombobox.selectedTicket == null,
                "Iter $i: selectedTicket must be null"
            )

            val input = document.getElementById("ticket-search")
                as? HTMLInputElement
            assertEquals(
                "",
                input?.value ?: "",
                "Iter $i: input must be empty on first load"
            )

            // sessionStorage must not have state
            val raw = window.sessionStorage.getItem(STORAGE_KEY)
            assertTrue(
                raw == null,
                "Iter $i: sessionStorage must be empty"
            )
        }
    }

    // ── Property: click-outside with empty input → only hide ──

    /**
     * Observed behavior: click-outside when input empty →
     * only hideDropdown(), no change to selectedTicket
     */
    @Test
    fun clickOutsideWithEmptyInputOnlyHidesDropdown() {
        document.body?.innerHTML = comboboxHtml()
        TicketCombobox.reset()

        TicketCombobox.ticketList = sampleTickets
        TicketCombobox.filteredTickets = sampleTickets

        // Pre-select a ticket
        val preSelected = sampleTickets[0]
        TicketCombobox.selectTicket(preSelected)

        // Clear input to simulate empty text
        val input = document.getElementById("ticket-search")
            as? HTMLInputElement
        input?.value = ""

        // Show dropdown
        TicketCombobox.filterTickets("")

        // Click outside
        val outside = document.getElementById("outside-area") ?: return
        val event = document.createEvent("MouseEvent")
        event.asDynamic().initEvent("click", true, true)
        outside.dispatchEvent(event)

        // selectedTicket must remain unchanged
        assertEquals(
            preSelected.ticketId,
            TicketCombobox.selectedTicket?.ticketId,
            "selectedTicket must remain '${preSelected.ticketId}' " +
                "after click-outside with empty input"
        )
    }
}
