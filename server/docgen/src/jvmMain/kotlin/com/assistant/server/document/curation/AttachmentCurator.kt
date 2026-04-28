package com.assistant.server.document.curation

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.server.document.curation.models.CuratedAttachment

/**
 * Curates attachment content with previews and priority ordering.
 *
 * Requirements: 5.1
 */
interface AttachmentCurator {
    /**
     * Curate attachments with preview truncation and priority.
     *
     * @param rootAttachments Attachments from the root ticket
     * @param linkedAttachments Attachments from linked tickets
     * @param kbReferencedFilenames Filenames already in KB records
     * @return Curated attachments within budget
     */
    fun curate(
        rootAttachments: List<AttachmentChunkInfo>,
        linkedAttachments: List<AttachmentChunkInfo>,
        kbReferencedFilenames: Set<String>
    ): List<CuratedAttachment>
}
