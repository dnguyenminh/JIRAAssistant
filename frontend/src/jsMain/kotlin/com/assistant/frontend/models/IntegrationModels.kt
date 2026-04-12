package com.assistant.frontend.models

import kotlinx.serialization.Serializable

@Serializable
data class ProviderInfo(
    val providerId: String = "",
    val name: String = "",
    val type: String = "",
    val status: String = "OFFLINE",
    val latencyMs: Long? = null,
    val lastChecked: String? = null,
    val sessionTime: String? = null,
    val errorMessage: String? = null,
    val endpoint: String? = null,
    val model: String? = null,
    val apiKeyMasked: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val priority: Int = 0
)

@Serializable
data class OllamaModelsListResponse(
    val models: List<OllamaModelEntry> = emptyList()
)

@Serializable
data class OllamaModelEntry(
    val name: String = "",
    val model: String = "",
    val size: Long = 0
)

@Serializable
data class TestResult(
    val providerId: String = "",
    val success: Boolean = false,
    val status: String = "OFFLINE",
    val latencyMs: Long? = null,
    val message: String = ""
)

@Serializable
data class ConfigUpdate(
    val endpoint: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null
)

@Serializable
data class PriorityUpdate(val priority: Int)

@Serializable
data class JiraConfigRequest(
    val domain: String,
    val email: String,
    val apiToken: String
)

@Serializable
data class JiraProjectInfo(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val projectTypeKey: String = ""
)

@Serializable
data class JiraConfigResponse(
    val status: String = "offline",
    val projects: List<JiraProjectInfo> = emptyList(),
    val error: String? = null
)
