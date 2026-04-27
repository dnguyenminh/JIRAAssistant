package com.assistant.server.document.curation

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.server.document.curation.models.CuratedAttachment

/**
 * Default implementation of [AttachmentCurator].
 *
 * Rules:
 * - Exclude attachments referenced in KB records
 * - Preview: first 3000 chars (5000 for requirement docs)
 * - Priority: root > linked (by depth)
 * - Total budget: 15000 chars max, truncate from deepest first
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
class DefaultAttachmentCurator : AttachmentCurator {

    override fun curate(
        rootAttachments: List<AttachmentChunkInfo>,
        linkedAttachments: List<AttachmentChunkInfo>,
        kbReferencedFilenames: Set<String>
    ): List<CuratedAttachment> {
        val rootCurated = curateGroup(
            rootAttachments, kbReferencedFilenames, PRIORITY_ROOT, "ROOT"
        )
        val linkedCurated = curateGroup(
            linkedAttachments, kbReferencedFilenames, PRIORITY_LINKED, "LINKED"
        )
        val all = (rootCurated + linkedCurated).sortedBy { it.priority }
        return enforceAttachmentBudget(all)
    }

    private fun curateGroup(
        attachments: List<AttachmentChunkInfo>,
        kbReferenced: Set<String>,
        basePriority: Int,
        ticketId: String
    ): List<CuratedAttachment> {
        return attachments
            .filter { it.filename !in kbReferenced }
            .mapIndexed { idx, att -> buildCuratedAttachment(att, basePriority + idx, ticketId) }
    }

    private fun buildCuratedAttachment(
        att: AttachmentChunkInfo,
        priority: Int,
        ticketId: String
    ): CuratedAttachment {
        val isReqDoc = isRequirementDoc(att.filename)
        val maxPreview = if (isReqDoc) {
            CurationConfig.MAX_REQUIREMENT_DOC_PREVIEW_CHARS
        } else {
            CurationConfig.MAX_ATTACHMENT_PREVIEW_CHARS
        }
        return CuratedAttachment(
            filename = att.filename,
            ticketId = ticketId,
            preview = att.content.take(maxPreview),
            priority = priority,
            isRequirementDoc = isReqDoc
        )
    }

    private fun isRequirementDoc(filename: String): Boolean {
        val lower = filename.lowercase()
        return REQUIREMENT_KEYWORDS.any { lower.contains(it) }
    }

    private fun enforceAttachmentBudget(
        attachments: List<CuratedAttachment>
    ): List<CuratedAttachment> {
        var totalChars = 0
        val result = mutableListOf<CuratedAttachment>()
        for (att in attachments) {
            val newTotal = totalChars + att.preview.length
            if (newTotal > CurationConfig.MAX_TOTAL_ATTACHMENT_CHARS) {
                val remaining = CurationConfig.MAX_TOTAL_ATTACHMENT_CHARS - totalChars
                if (remaining > 0) {
                    result.add(att.copy(preview = att.preview.take(remaining)))
                }
                break
            }
            result.add(att)
            totalChars = newTotal
        }
        return result
    }

    companion object {
        private const val PRIORITY_ROOT = 0
        private const val PRIORITY_LINKED = 100

        private val REQUIREMENT_KEYWORDS = setOf(
            "brd", "frd", "fsd", "requirement"
        )
    }
}
