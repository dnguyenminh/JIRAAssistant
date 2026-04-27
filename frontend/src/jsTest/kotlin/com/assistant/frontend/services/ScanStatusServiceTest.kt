package com.assistant.frontend.services

import com.assistant.scan.ScanStatus
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Unit tests for ScanStatusService badge updates.
 *
 * **Validates: Requirements 3.1, 3.2**
 */
class ScanStatusServiceTest {

    private lateinit var badge: HTMLElement

    @BeforeTest
    fun setup() {
        badge = document.createElement("span") as HTMLElement
        badge.style.display = "none"
    }

    /** Req 3.1: Badge shows progress during SCANNING. */
    @Test
    fun scanningBadgeShowsProgressFormat() {
        ScanStatusService.updateBadge(badge, ScanStatus.SCANNING, 15, 42)
        assertEquals("Scanning... 15/42 — 35%", badge.textContent)
        assertNotEquals("none", badge.style.display)
    }

    @Test
    fun scanningBadgeZeroTotal() {
        ScanStatusService.updateBadge(badge, ScanStatus.SCANNING, 0, 0)
        assertEquals("Scanning... 0/0 — 0%", badge.textContent)
    }

    @Test
    fun scanningBadgeFullProgress() {
        ScanStatusService.updateBadge(badge, ScanStatus.SCANNING, 42, 42)
        assertEquals("Scanning... 42/42 — 100%", badge.textContent)
    }

    /** Req 3.2: Badge shows "Completed" with total count. */
    @Test
    fun completedBadgeShowsTotalCount() {
        ScanStatusService.updateBadge(badge, ScanStatus.COMPLETED, 42, 42)
        assertEquals("✅ Completed (42 tickets)", badge.textContent)
        assertNotEquals("none", badge.style.display)
    }

    @Test
    fun pausedBadgeShowsProcessedOfTotal() {
        ScanStatusService.updateBadge(badge, ScanStatus.PAUSED, 10, 50)
        assertEquals("⏸ Tạm dừng (10/50)", badge.textContent)
    }

    @Test
    fun cancelledBadgeShowsCancelled() {
        ScanStatusService.updateBadge(badge, ScanStatus.CANCELLED, 5, 20)
        assertEquals("❌ Đã hủy", badge.textContent)
    }

    @Test
    fun idleBadgeIsHidden() {
        badge.style.display = ""
        badge.textContent = "something"
        ScanStatusService.updateBadge(badge, ScanStatus.IDLE, 0, 0)
        assertEquals("none", badge.style.display)
        assertEquals("", badge.textContent)
    }
}
