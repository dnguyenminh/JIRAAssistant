package com.assistant.ai

import com.assistant.ai.deepanalysis.models.AcceptanceCriterion
import com.assistant.ai.deepanalysis.models.AnalysisMetadata
import com.assistant.ai.deepanalysis.models.DependencyInfo
import com.assistant.ai.deepanalysis.models.TechnicalDetails
import kotlinx.serialization.Serializable

/**
 * AI Orchestrator interface — coordinates multi-provider AI analysis
 * with KB-First strategy and automatic failover.
 */
interface AIOrchestrator {
    /**
     * Analyze a ticket. Uses KB-First strategy: checks KBRepository first,
     * only calls AI agent when KB miss or forceReanalyze=true.
     */
    suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean = false): AnalysisResult

    /**
     * Analyze a ticket with enriched content (summary, description).
     * Provides better AI analysis by including actual ticket data in prompt.
     */
    suspend fun analyzeTicket(
        ticketId: String, ticketContent: String,
        forceReanalyze: Boolean = false
    ): AnalysisResult = analyzeTicket(ticketId, forceReanalyze)

    /**
     * Test connectivity to a specific AI provider.
     */
    suspend fun testProvider(providerId: String): ProviderTestResult

    /**
     * Get current status of all configured AI providers.
     */
    suspend fun getProviderStatuses(): List<ProviderStatus>

    /**
     * Set the failover priority order for AI providers.
     */
    fun setFailoverOrder(providerIds: List<String>)

    /**
     * Analyze project sprint data to detect bottlenecks.
     * Examines cycle time, blocked tickets, and unresolved ratio.
     * Returns at least 2 alerts: 1 RISK + 1 OPTIMIZATION.
     */
    fun analyzeBottlenecks(
        totalTickets: Int,
        resolvedCount: Int,
        cycleTimeDays: Double,
        blockedCount: Int
    ): List<BottleneckAlert>

    /**
     * Generate velocity trend data for recent sprints.
     * Returns at least 7 SprintVelocity entries.
     */
    fun generateVelocityTrend(totalTickets: Int, resolvedCount: Int): List<SprintVelocity>

    /**
     * Calculate AI velocity score from project metrics.
     */
    fun calculateAIVelocity(totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double): Double

    /**
     * Analyze multiple tickets in a single AI prompt (batch mode).
     * Returns map of ticketId → AnalysisResult.
     * Falls back to single-ticket mode on parse failure.
     * Req: AC 37
     */
    suspend fun analyzeTicketBatch(
        tickets: List<Pair<String, String>>,
        forceReanalyze: Boolean = false
    ): Map<String, AnalysisResult> = tickets.associate { (id, content) ->
        id to analyzeTicket(id, content, forceReanalyze)
    }
}

/**
 * Result of AI ticket analysis. Extended with deep analysis fields (19.1, 19.6).
 * New fields are optional with defaults for backward compatibility.
 */
@Serializable
data class AnalysisResult(
    val ticketId: String,
    val context: RequirementSummary,
    val evolution: List<EvolutionEntry>,
    val complexity: ComplexityAssessment,
    val source: AnalysisSource,
    // Deep analysis fields — Requirements 19.2, 19.3, 19.4, 19.5, 19.6
    val technicalDetails: TechnicalDetails = TechnicalDetails(),
    val acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),
    val dependencies: DependencyInfo = DependencyInfo(),
    val analysisMetadata: AnalysisMetadata = AnalysisMetadata(),
    // Diagrams — Requirements 28.1, 28.2
    val diagrams: List<com.assistant.ai.deepanalysis.models.DiagramData> = emptyList()
)

@Serializable
enum class AnalysisSource { KB_CACHE, FRESH_AI }

/**
 * Requirement summary with deep analysis fields (19.1, 19.6).
 * New fields are optional with defaults for backward compatibility.
 */
@Serializable
data class RequirementSummary(
    val unified: String,
    val affectedModules: List<AffectedModule> = emptyList(),
    // Deep analysis fields — Requirements 19.1, 19.6
    val businessSummary: String = "",
    val asIsState: String = "",
    val toBeState: String = "",
    val extractedRequirements: List<String> = emptyList()
)

@Serializable
data class AffectedModule(
    val name: String,
    val colorCategory: String // PRIMARY, ACCENT, SECONDARY
)

@Serializable
data class EvolutionEntry(
    val version: String,
    val date: String,
    val description: String,
    val changeType: String // ORIGIN, UPDATE, CURRENT
)

@Serializable
data class ComplexityAssessment(
    val scrumPoints: Double,
    val description: String,
    val kbReferences: List<KBReference> = emptyList()
)

@Serializable
data class KBReference(
    val ticketId: String,
    val similarityPercent: Double
)

@Serializable
data class ProviderStatus(
    val providerId: String,
    val name: String,
    val status: ConnectionStatus,
    val latencyMs: Long? = null,
    val lastChecked: String? = null
)

@Serializable
enum class ConnectionStatus { ACTIVE, STANDBY, OFFLINE }

@Serializable
data class ProviderTestResult(
    val providerId: String,
    val success: Boolean,
    val latencyMs: Long,
    val message: String
)

@Serializable
data class ProviderConfig(
    val providerId: String,
    val name: String,
    val type: ProviderType,
    val endpoint: String,
    val apiKey: String? = null,
    val model: String? = null,
    val priority: Int,
    val status: ConnectionStatus
)

@Serializable
enum class ProviderType { JIRA, OLLAMA, GEMINI, LM_STUDIO, GEMINI_CLI, COPILOT_CLI, KIRO_CLI, EMBEDDING }
