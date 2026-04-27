package com.assistant.server.agent.ba.prompt

import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.StructuredMemory
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.server.document.curation.AttachmentCurator
import java.time.Instant

/**
 * Applies AttachmentCurator to raw attachment data in memory.
 *
 * Reads raw attachment entries from "attachmentsData" slot,
 * separates root vs linked attachments, applies curation,
 * and stores curated previews back.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
internal object AttachmentCurationStep {

    fun apply(memory: StructuredMemory, curator: AttachmentCurator) {
        val rawEntries = memory.getSlot("attachmentsData")
        if (rawEntries.isEmpty()) return
        val kbRefs = extractKbReferencedFilenames(memory)
        val (root, linked) = partitionAttachments(rawEntries)
        val curated = curator.curate(root, linked, kbRefs)
        storeCuratedEntries(memory, curated)
    }

    private fun extractKbReferencedFilenames(
        memory: StructuredMemory
    ): Set<String> {
        return memory.getSlot("kbRecords")
            .flatMap { extractFilenames(it.data) }
            .toSet()
    }

    private fun extractFilenames(kbData: String): List<String> {
        val regex = Regex("""[\w\-]+\.\w{2,5}""")
        return regex.findAll(kbData).map { it.value }.toList()
    }

    private fun partitionAttachments(
        entries: List<MemoryEntry>
    ): Pair<List<AttachmentChunkInfo>, List<AttachmentChunkInfo>> {
        val root = mutableListOf<AttachmentChunkInfo>()
        val linked = mutableListOf<AttachmentChunkInfo>()
        entries.forEach { entry ->
            val chunk = toAttachmentChunk(entry)
            if (entry.toolName == "processAttachment") {
                root.add(chunk)
            } else {
                linked.add(chunk)
            }
        }
        return root to linked
    }

    private fun toAttachmentChunk(entry: MemoryEntry): AttachmentChunkInfo {
        val filename = entry.source.ifBlank { "unknown.txt" }
        return AttachmentChunkInfo(
            filename = filename,
            content = entry.data,
            similarityScore = 0.5f
        )
    }

    private fun storeCuratedEntries(
        memory: StructuredMemory,
        curated: List<com.assistant.server.document.curation.models.CuratedAttachment>
    ) {
        curated.forEach { att ->
            memory.store(
                "attachmentsData",
                MemoryEntry(
                    data = "[${att.filename}] ${att.preview}",
                    source = att.filename,
                    toolName = "attachmentCurator",
                    timestamp = Instant.now().toString()
                )
            )
        }
    }
}
