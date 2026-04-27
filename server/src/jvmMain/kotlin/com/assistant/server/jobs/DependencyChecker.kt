package com.assistant.server.jobs

import com.assistant.server.db.GeneratedDocumentMeta

/**
 * Pure function: validates document generation dependencies (Req 1.1–1.3).
 * BRD always allowed. FSD/Slides require BRD with DRAFT or APPROVED.
 */
object DependencyChecker {

    data class CheckResult(val allowed: Boolean, val reason: String? = null)

    fun canGenerate(
        documentType: String,
        existingDocs: List<GeneratedDocumentMeta>
    ): CheckResult {
        return when (documentType) {
            "BRD" -> CheckResult(allowed = true)
            "FSD" -> checkBrdExists(existingDocs, "FSD")
            "REQUIREMENT_SLIDES" -> checkBrdExists(existingDocs, "Slides")
            else -> CheckResult(allowed = false, reason = "Unknown document type: $documentType")
        }
    }

    private fun checkBrdExists(docs: List<GeneratedDocumentMeta>, target: String): CheckResult {
        val hasBrd = docs.any {
            it.documentType == "BRD" && it.approvalStatus in listOf("DRAFT", "APPROVED")
        }
        return if (hasBrd) CheckResult(allowed = true)
        else CheckResult(allowed = false, reason = "BRD phải được sinh trước khi tạo $target")
    }
}
