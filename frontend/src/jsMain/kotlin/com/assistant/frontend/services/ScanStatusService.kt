package com.assistant.frontend.services

import com.assistant.scan.ScanStatus
import org.w3c.dom.HTMLElement

/**
 * Shared scan status badge update logic.
 * Used by AnalysisPage, KnowledgeGraphPage, DashboardPage.
 */
object ScanStatusService {

    fun updateBadge(badge: HTMLElement, status: ScanStatus, processed: Int, total: Int) {
        when (status) {
            ScanStatus.SCANNING -> showBadge(
                badge, "rgba(45,254,207,0.1)", "rgba(45,254,207,0.2)",
                "var(--primary)", "\uD83D\uDD04 Đang quét ($processed/$total)"
            )
            ScanStatus.COMPLETED -> showBadge(
                badge, "rgba(45,254,207,0.15)", "rgba(45,254,207,0.3)",
                "var(--primary)", "✅ Đã hoàn tất"
            )
            ScanStatus.PAUSED -> showBadge(
                badge, "rgba(249,212,35,0.1)", "rgba(249,212,35,0.2)",
                "#f9d423", "⏸ Tạm dừng ($processed/$total)"
            )
            ScanStatus.CANCELLED -> showBadge(
                badge, "rgba(255,110,132,0.1)", "rgba(255,110,132,0.2)",
                "var(--danger)", "❌ Đã hủy"
            )
            ScanStatus.IDLE -> {
                badge.style.display = "none"
                badge.textContent = ""
            }
        }
    }

    private fun showBadge(
        badge: HTMLElement, bg: String, border: String,
        color: String, text: String
    ) {
        badge.style.display = ""
        badge.style.background = bg
        badge.style.borderColor = border
        badge.style.color = color
        badge.textContent = text
    }
}
