package com.assistant.frontend.models

import kotlinx.serialization.Serializable

@Serializable
data class GraphFilters(
    val enabledTypes: Set<String> = emptySet(),
    val selectedClusterId: Int? = null,
    val focusNodeId: String? = null,
    val focusDepth: Int = 1,
    val searchQuery: String = ""
)
