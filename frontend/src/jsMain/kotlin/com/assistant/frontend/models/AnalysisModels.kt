package com.assistant.frontend.models

import kotlinx.serialization.Serializable

@Serializable
data class ProjectAnalysisResponse(
    val projectKey: String = "",
    val totalTickets: Int = 0,
    val resolutionRate: Double = 0.0,
    val cycleTimeDays: Double = 0.0,
    val aiVelocity: Double = 0.0,
    val velocityTrend: List<SprintVelocity> = emptyList(),
    val bottlenecks: List<BottleneckAlert> = emptyList(),
    val providerStatuses: List<ProviderStatusInfo> = emptyList()
)

@Serializable
data class SprintVelocity(
    val sprintName: String,
    val storyPoints: Double
)

@Serializable
data class BottleneckAlert(
    val type: String,
    val severity: String,
    val title: String,
    val description: String
)

@Serializable
data class ProviderStatusInfo(
    val providerId: String = "",
    val name: String = "",
    val status: String = "",
    val latencyMs: Long? = null,
    val lastChecked: String? = null
)
