package com.assistant.ai

import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

/**
 * AI Orchestrator implementation with KB-First strategy and failover.
 *
 * Strategy:
 * 1. Check KBRepository first (unless forceReanalyze=true)
 * 2. On KB miss, call AI agent with failover across providers
 * 3. Parse AI response into structured AnalysisResult
 * 4. Retry up to 2 times on invalid JSON
 * 5. Timeout 30s per provider, then failover to next
 */
class AIOrchestratorImpl(
    private val kbRepository: KBRepository,
    private val agents: Map<String, AIAgent>,
    private val agentProvider: (() -> Map<String, AIAgent>)? = null,
    private val providerConfigs: MutableList<ProviderConfig> = mutableListOf(),
    private val providerConfigProvider: (() -> List<ProviderConfig>)? = null
) : AIOrchestrator {

    /** Get current agents — uses dynamic provider if available, otherwise static map. */
    private fun currentAgents(): Map<String, AIAgent> = agentProvider?.invoke() ?: agents

    /** Get current provider configs — uses dynamic provider if available, otherwise static list. */
    private fun currentProviderConfigs(): List<ProviderConfig> =
        providerConfigProvider?.invoke() ?: providerConfigs

    companion object {
        const val PROVIDER_TIMEOUT_MS = 120_000L  // 2 min — accounts for model loading + inference
        const val MAX_RETRIES = 2
    }

    private val failoverLog = mutableListOf<FailoverEvent>()

    data class FailoverEvent(
        val fromProvider: String,
        val toProvider: String,
        val reason: String,
        val timestamp: Long
    )

    override suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean): AnalysisResult {
        return analyzeTicket(ticketId, "", forceReanalyze)
    }

    override suspend fun analyzeTicket(
        ticketId: String, ticketContent: String, forceReanalyze: Boolean
    ): AnalysisResult {
        if (!forceReanalyze) {
            val cached = kbRepository.findByTicketId(ticketId)
            if (cached != null) return cached.toAnalysisResult(AnalysisSource.KB_CACHE)
        }
        val activeProviders = getActiveProvidersByPriority()
        if (activeProviders.isEmpty()) return errorResult(ticketId, "All AI providers are offline")
        return tryProvidersWithFailover(ticketId, ticketContent, activeProviders, forceReanalyze)
    }

    private suspend fun tryProvidersWithFailover(
        ticketId: String, ticketContent: String,
        providers: List<ProviderConfig>, forceReanalyze: Boolean
    ): AnalysisResult {
        var lastError: String? = null
        for (provider in providers) {
            val agent = currentAgents()[provider.providerId] ?: continue
            val result = tryAnalyzeWithRetry(ticketId, ticketContent, agent, provider)
            if (result != null) {
                saveToKB(result, forceReanalyze)
                return result
            }
            lastError = "Provider ${provider.name} failed or timed out"
            logFailover(provider, providers)
        }
        return errorResult(ticketId, lastError ?: "Analysis failed")
    }

    private suspend fun saveToKB(result: AnalysisResult, forceReanalyze: Boolean) {
        val kbRecord = result.toKBRecord()
        if (forceReanalyze) kbRepository.overwrite(kbRecord) else kbRepository.save(kbRecord)
    }

    private fun logFailover(current: ProviderConfig, all: List<ProviderConfig>) {
        val next = all.getOrNull(all.indexOf(current) + 1) ?: return
        failoverLog.add(FailoverEvent(current.providerId, next.providerId, "timeout_or_failure", currentTimeMillis()))
    }

    private suspend fun tryAnalyzeWithRetry(
        ticketId: String, ticketContent: String,
        agent: AIAgent, provider: ProviderConfig
    ): AnalysisResult? {
        var retries = 0
        while (retries <= MAX_RETRIES) {
            val prompt = buildAnalysisPrompt(ticketId, ticketContent, retries > 0)
            val aiResult = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                agent.analyze(prompt)
            } ?: return null
            when (aiResult) {
                is AIResult.Success -> {
                    val parsed = parseAIResponse(ticketId, aiResult.response)
                    if (parsed != null) return parsed
                    retries++
                }
                is AIResult.Failure -> return null
            }
        }
        return null
    }

    private fun buildAnalysisPrompt(ticketId: String, ticketContent: String, isRetry: Boolean): String {
        val retryHint = if (isRetry) "\nIMPORTANT: Return ONLY valid JSON. No markdown, no extra text." else ""
        val contentSection = if (ticketContent.isNotBlank()) {
            "\n--- TICKET CONTENT ---\n$ticketContent\n--- END CONTENT ---\n"
        } else ""
        return """
            Analyze Jira ticket $ticketId.$contentSection
            Return a JSON object with:
            {
              "requirementSummary": { "unified": "...", "affectedModules": [{"name": "...", "colorCategory": "PRIMARY|ACCENT|SECONDARY"}] },
              "evolution": [{"version": "...", "date": "...", "description": "...", "changeType": "ORIGIN|UPDATE|CURRENT"}],
              "complexity": { "scrumPoints": 5.0, "description": "...", "kbReferences": [{"ticketId": "...", "similarityPercent": 85.0}] }
            }$retryHint
        """.trimIndent()
    }

    internal fun parseAIResponse(ticketId: String, response: String): AnalysisResult? {
        return try {
            val json = Json { ignoreUnknownKeys = true }

            // Strip markdown code fences if present (```json ... ``` or ``` ... ```)
            val cleaned = response.trim().let { raw ->
                // Find JSON content between code fences
                val startIdx = raw.indexOf("```")
                if (startIdx >= 0) {
                    val afterFence = raw.substring(startIdx + 3)
                    // Skip optional language tag (e.g., "json")
                    val contentStart = afterFence.indexOf('\n')
                    if (contentStart >= 0) {
                        val content = afterFence.substring(contentStart + 1)
                        val endIdx = content.lastIndexOf("```")
                        if (endIdx >= 0) content.substring(0, endIdx).trim() else content.trim()
                    } else afterFence.trim()
                } else raw
            }

            val root = json.parseToJsonElement(cleaned).jsonObject

            val reqSummaryObj = root["requirementSummary"]?.jsonObject
            val unified = reqSummaryObj?.get("unified")?.jsonPrimitive?.content ?: ""
            val modules = reqSummaryObj?.get("affectedModules")?.jsonArray?.map { mod ->
                val obj = mod.jsonObject
                AffectedModule(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    colorCategory = obj["colorCategory"]?.jsonPrimitive?.content ?: "PRIMARY"
                )
            } ?: emptyList()

            val evolutionArr = root["evolution"]?.jsonArray?.map { entry ->
                val obj = entry.jsonObject
                EvolutionEntry(
                    version = obj["version"]?.jsonPrimitive?.content ?: "",
                    date = obj["date"]?.jsonPrimitive?.content ?: "",
                    description = obj["description"]?.jsonPrimitive?.content ?: "",
                    changeType = obj["changeType"]?.jsonPrimitive?.content ?: "UPDATE"
                )
            } ?: emptyList()

            val complexityObj = root["complexity"]?.jsonObject
            val scrumPoints = complexityObj?.get("scrumPoints")?.jsonPrimitive?.doubleOrNull ?: 0.0
            val description = complexityObj?.get("description")?.jsonPrimitive?.content ?: ""
            val kbRefs = complexityObj?.get("kbReferences")?.jsonArray?.map { ref ->
                val obj = ref.jsonObject
                KBReference(
                    ticketId = obj["ticketId"]?.jsonPrimitive?.content ?: "",
                    similarityPercent = obj["similarityPercent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                )
            } ?: emptyList()

            AnalysisResult(
                ticketId = ticketId,
                context = RequirementSummary(unified = unified, affectedModules = modules),
                evolution = evolutionArr,
                complexity = ComplexityAssessment(
                    scrumPoints = scrumPoints,
                    description = description,
                    kbReferences = kbRefs
                ),
                source = AnalysisSource.FRESH_AI
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun testProvider(providerId: String): ProviderTestResult {
        val agent = currentAgents()[providerId]
            ?: return ProviderTestResult(providerId, false, 0, "Provider not found")

        val start = currentTimeMillis()
        return try {
            // For Ollama, use /api/tags endpoint (lightweight) instead of full inference
            if (agent is OllamaAgent) {
                val result = agent.testConnection()
                val latency = currentTimeMillis() - start
                if (result != null) {
                    ProviderTestResult(providerId, true, latency, result)
                } else {
                    ProviderTestResult(providerId, false, latency, "Connection failed")
                }
            } else {
                val result = withTimeoutOrNull(5000L) {
                    agent.analyze("ping")
                }
                val latency = currentTimeMillis() - start
                when (result) {
                    is AIResult.Success -> ProviderTestResult(providerId, true, latency, "Connected")
                    is AIResult.Failure -> ProviderTestResult(providerId, false, latency, result.error)
                    null -> ProviderTestResult(providerId, false, latency, "Timeout")
                }
            }
        } catch (e: Exception) {
            val latency = currentTimeMillis() - start
            ProviderTestResult(providerId, false, latency, e.message ?: "Connection failed")
        }
    }

    override suspend fun getProviderStatuses(): List<ProviderStatus> {
        return currentProviderConfigs().map { config ->
            ProviderStatus(
                providerId = config.providerId,
                name = config.name,
                status = config.status,
                latencyMs = null,
                lastChecked = null
            )
        }
    }

    override fun setFailoverOrder(providerIds: List<String>) {
        val configs = currentProviderConfigs().toMutableList()
        providerIds.forEachIndexed { index, id ->
            configs.find { it.providerId == id }?.let { config ->
                val idx = configs.indexOf(config)
                configs[idx] = config.copy(priority = index)
            }
        }
        configs.sortBy { it.priority }
        // Update the static list if using it
        providerConfigs.clear()
        providerConfigs.addAll(configs)
    }

    internal fun getActiveProvidersByPriority(): List<ProviderConfig> {
        return currentProviderConfigs()
            .filter { it.status == ConnectionStatus.ACTIVE }
            .sortedBy { it.priority }
    }

    fun getFailoverLog(): List<FailoverEvent> = failoverLog.toList()

    override fun analyzeBottlenecks(
        totalTickets: Int,
        resolvedCount: Int,
        cycleTimeDays: Double,
        blockedCount: Int
    ): List<BottleneckAlert> {
        val alerts = mutableListOf<BottleneckAlert>()
        val unresolvedRatio = if (totalTickets > 0) {
            (totalTickets - resolvedCount).toDouble() / totalTickets
        } else 0.0

        // RISK alerts based on cycle time, blocked tickets, unresolved ratio
        if (blockedCount > 0) {
            val severity = when {
                blockedCount >= 5 -> "HIGH"
                blockedCount >= 2 -> "MEDIUM"
                else -> "LOW"
            }
            alerts.add(
                BottleneckAlert(
                    type = "RISK",
                    severity = severity,
                    title = "Blocked Tickets Detected",
                    description = "$blockedCount ticket(s) currently blocked. Review dependencies and remove impediments to maintain sprint flow."
                )
            )
        } else if (cycleTimeDays > 14.0) {
            alerts.add(
                BottleneckAlert(
                    type = "RISK",
                    severity = "HIGH",
                    title = "High Cycle Time Alert",
                    description = "Average cycle time is ${((cycleTimeDays * 10).toInt() / 10.0)} days, exceeding the 14-day threshold. Consider breaking down large tickets."
                )
            )
        } else if (unresolvedRatio > 0.5) {
            alerts.add(
                BottleneckAlert(
                    type = "RISK",
                    severity = "MEDIUM",
                    title = "Low Resolution Rate",
                    description = "Only ${((1.0 - unresolvedRatio) * 100).toInt()}% of tickets resolved. Prioritize closing open items before adding new work."
                )
            )
        } else {
            alerts.add(
                BottleneckAlert(
                    type = "RISK",
                    severity = "LOW",
                    title = "Sprint Health Monitoring",
                    description = "No critical bottlenecks detected. Continue monitoring cycle time and blocked ticket count."
                )
            )
        }

        // OPTIMIZATION alert based on velocity and patterns
        if (cycleTimeDays > 0 && cycleTimeDays <= 7.0 && unresolvedRatio < 0.3) {
            alerts.add(
                BottleneckAlert(
                    type = "OPTIMIZATION",
                    severity = "LOW",
                    title = "Sprint Velocity Opportunity",
                    description = "Current pace is strong. Consider increasing sprint scope by 10-15% to maximize team capacity."
                )
            )
        } else if (unresolvedRatio > 0.4) {
            alerts.add(
                BottleneckAlert(
                    type = "OPTIMIZATION",
                    severity = "MEDIUM",
                    title = "Workflow Optimization Suggested",
                    description = "High unresolved ratio (${(unresolvedRatio * 100).toInt()}%). Implement WIP limits and focus on completing in-progress items."
                )
            )
        } else {
            alerts.add(
                BottleneckAlert(
                    type = "OPTIMIZATION",
                    severity = "MEDIUM",
                    title = "Optimized Path Available",
                    description = "Based on current velocity, sprint could finish earlier. Consider pulling items from the backlog."
                )
            )
        }

        return alerts
    }

    override fun generateVelocityTrend(totalTickets: Int, resolvedCount: Int): List<SprintVelocity> {
        // Generate at least 7 sprint velocity entries based on project data
        val basePoints = if (resolvedCount > 0) (resolvedCount.toDouble() / 7.0) * 5.0 else 20.0
        val sprintNames = listOf(
            "Sprint 1", "Sprint 2", "Sprint 3", "Sprint 4",
            "Sprint 5", "Sprint 6", "Sprint 7"
        )
        // Simulate a realistic velocity trend with gradual increase
        val multipliers = listOf(0.6, 0.75, 0.85, 1.0, 0.9, 1.1, 1.05)
        return sprintNames.zip(multipliers).map { (name, mult) ->
            SprintVelocity(
                sprintName = name,
                storyPoints = (basePoints * mult).coerceAtLeast(5.0)
            )
        }
    }

    override fun calculateAIVelocity(totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double): Double {
        if (totalTickets == 0) return 0.0
        val resolutionRate = resolvedCount.toDouble() / totalTickets
        val cycleEfficiency = if (cycleTimeDays > 0) (14.0 / cycleTimeDays).coerceAtMost(2.0) else 1.0
        return ((resolutionRate * 50.0) + (cycleEfficiency * 25.0)).coerceIn(0.0, 100.0)
    }

    private fun errorResult(ticketId: String, message: String): AnalysisResult {
        return AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary(unified = "Error: $message"),
            evolution = emptyList(),
            complexity = ComplexityAssessment(scrumPoints = 0.0, description = message),
            source = AnalysisSource.FRESH_AI
        )
    }
}

private fun KBRecord.toAnalysisResult(source: AnalysisSource): AnalysisResult {
    return AnalysisResult(
        ticketId = ticketId,
        context = RequirementSummary(unified = requirementSummary),
        evolution = evolutionHistory.map { entry ->
            com.assistant.ai.EvolutionEntry(
                version = entry.version,
                date = entry.date,
                description = entry.description,
                changeType = entry.changeType
            )
        },
        complexity = ComplexityAssessment(
            scrumPoints = scrumPoints,
            description = rationale,
            kbReferences = similarTicketRefs.map { ref ->
                KBReference(ticketId = ref, similarityPercent = confidenceScore * 100)
            }
        ),
        source = source
    )
}

private fun AnalysisResult.toKBRecord(): KBRecord {
    return KBRecord(
        ticketId = ticketId,
        requirementSummary = context.unified,
        evolutionHistory = evolution.map { entry ->
            com.assistant.kb.EvolutionEntry(
                version = entry.version,
                date = entry.date,
                description = entry.description,
                changeType = entry.changeType
            )
        },
        scrumPoints = complexity.scrumPoints,
        confidenceScore = 0.8,
        rationale = complexity.description,
        similarTicketRefs = complexity.kbReferences.map { it.ticketId },
        timestamp = currentTimeMillis().toString()
    )
}

internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
