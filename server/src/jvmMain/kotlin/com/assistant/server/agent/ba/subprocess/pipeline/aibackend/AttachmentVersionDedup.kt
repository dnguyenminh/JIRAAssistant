package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import org.slf4j.LoggerFactory

/**
 * Attachment collection helpers for DataPreFetcher.
 * Handles multi-ticket collection, version deduplication,
 * and result formatting.
 */

private val log = LoggerFactory.getLogger("AttachmentVersionDedup")

private val VERSION_PATTERN = Regex("""v(\d+(?:\.\d+)*)""", RegexOption.IGNORE_CASE)
private val VERSION_AND_EXT = Regex(
    """\s*v\d+(?:\.\d+)*(?:\s*\([^)]*\))?(\.\w+)?$""",
    RegexOption.IGNORE_CASE
)

/** Collect ATTACHMENT/CONFLUENCE chunks from multiple tickets. */
internal suspend fun collectChunksFromTickets(
    store: VectorStore,
    ticketIds: List<String>
): List<AttachmentChunk> {
    val all = mutableListOf<AttachmentChunk>()
    for (tid in ticketIds) {
        try {
            val chunks = store.findByTicketId(tid)
            val filtered = chunks.filter {
                it.chunkType in listOf("ATTACHMENT", "CONFLUENCE")
            }
            if (filtered.isNotEmpty()) all.addAll(filtered)
        } catch (e: Exception) {
            log.warn("Failed to fetch attachments for {}: {}", tid, e.message)
        }
    }
    return all
}

/**
 * Deduplicate versioned files — keep only the latest version.
 * Detects patterns like "name v1.7.docx", "name v2.3.zip".
 * Files without version patterns are kept as-is.
 */
internal fun deduplicateVersions(
    grouped: Map<String, List<AttachmentChunk>>
): Map<String, List<AttachmentChunk>> {
    val byBase = mutableMapOf<String, MutableList<Pair<String, List<AttachmentChunk>>>>()
    for ((filename, chunks) in grouped) {
        val base = extractBaseName(filename)
        byBase.getOrPut(base) { mutableListOf() }.add(filename to chunks)
    }
    val result = mutableMapOf<String, List<AttachmentChunk>>()
    for ((_, versions) in byBase) {
        if (versions.size <= 1) {
            result[versions[0].first] = versions[0].second
        } else {
            val latest = versions.maxByOrNull { extractVersion(it.first) }
                ?: versions.last()
            result[latest.first] = latest.second
            log.info("Version dedup: kept '{}' from {} versions", latest.first, versions.size)
        }
    }
    return result
}

/** Format deduped attachments into readable text for AI prompt. */
internal fun formatAttachmentResult(
    deduped: Map<String, List<AttachmentChunk>>
): String = deduped.entries.joinToString("\n\n") { (filename, chunks) ->
    val source = chunks.firstOrNull()?.ticketId ?: "unknown"
    "--- FILE: $filename (source: $source, ${chunks.size} chunks) ---\n${
        chunks.joinToString("\n") { it.chunkText.take(500) }
    }"
}

/** Extract base name by stripping version + extension. */
internal fun extractBaseName(filename: String): String {
    val stripped = VERSION_AND_EXT.replace(filename, "").trim()
    return if (stripped.isNotBlank()) stripped else filename
}

/** Extract numeric version for comparison. Returns 0.0 if none. */
internal fun extractVersion(filename: String): Double {
    val match = VERSION_PATTERN.find(filename) ?: return 0.0
    val parts = match.groupValues[1].split(".")
    return try {
        parts[0].toDouble() + (parts.getOrNull(1)?.toDouble() ?: 0.0) / 100.0
    } catch (_: Exception) { 0.0 }
}
