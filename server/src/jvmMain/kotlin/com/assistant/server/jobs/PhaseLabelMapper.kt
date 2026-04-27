package com.assistant.server.jobs

/**
 * Maps technical phase codes to user-friendly Vietnamese labels.
 *
 * Used by the API layer to derive `phaseLabel` from the current job phase
 * without storing redundant data in the database.
 *
 * @see Requirements 2.1 — Phase Label thân thiện với User
 */
object PhaseLabelMapper {

    /**
     * Returns a Vietnamese label for the given phase code.
     *
     * @param phase the technical phase code (e.g. "GENERATING_DOCUMENT")
     * @return a user-friendly Vietnamese label, or the phase code itself as fallback
     */
    fun getLabel(phase: String): String = when (phase) {
        "QUEUED" -> "Đang chờ xử lý..."
        "AGGREGATING_DATA" -> "Thu thập dữ liệu ticket..."
        "GENERATING_DOCUMENT" -> "Đang gọi AI sinh tài liệu..."
        "PARSING_RESPONSE" -> "Phân tích kết quả AI..."
        "SAVING" -> "Đang lưu tài liệu..."
        "COMPLETE" -> "Hoàn tất"
        "FAILED" -> "Thất bại"
        else -> phase
    }
}
