package com.assistant.document.models

import kotlinx.serialization.Serializable

/**
 * A parsed section of a generated document.
 * Used by BrdResponseParser/FsdResponseParser to represent individual document sections
 * with their heading, content, and source references.
 */
@Serializable
data class DocumentSection(
    val heading: String,
    val content: String,
    val sourceRefs: List<String> = emptyList()
)
