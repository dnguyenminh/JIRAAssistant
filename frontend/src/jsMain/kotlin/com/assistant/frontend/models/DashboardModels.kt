package com.assistant.frontend.models

import kotlinx.serialization.Serializable

@Serializable
data class DashboardAnalysis(
    val projectKey: String = "",
    val totalTickets: Int = 0,
    val resolutionRate: Double = 0.0,
    val cycleTimeDays: Double = 0.0,
    val aiVelocity: Double = 0.0,
    val velocityTrend: List<VelocityPoint> = emptyList(),
    val bottlenecks: List<BottleneckEntry> = emptyList(),
    val providerStatuses: List<ProviderStatusEntry> = emptyList()
)

@Serializable
data class VelocityPoint(
    val sprintName: String = "",
    val storyPoints: Double = 0.0
)

@Serializable
data class BottleneckEntry(
    val type: String = "",
    val severity: String = "",
    val title: String = "",
    val description: String = ""
)

@Serializable
data class ProviderStatusEntry(
    val providerId: String = "",
    val name: String = "",
    val status: String = "",
    val latencyMs: Long? = null,
    val lastChecked: String? = null
)

data class ConsoleEntry(val tag: String, val message: String)
