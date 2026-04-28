package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.server.agent.ba.subprocess.DocumentQualityChecker
import com.assistant.server.agent.ba.subprocess.pipeline.models.PipelineStepConfig
import com.assistant.server.agent.ba.subprocess.pipeline.models.StepResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Multi-turn pipeline strategy for BA document generation.
 *
 * Orchestrates 3 phases:
 * 1. Data collection via MCP tools
 * 2. Multi-turn AI loop (analysis → requirements → writing → review)
 * 3. Document assembly into BATaskResult
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 4.1–4.5, 5.1–5.4, 8.3, 8.4
 */
class MultiTurnPipelineStrategy(
    private val dataCollector: DataCollector,
    private val stopCondition: PipelineStopCondition,
    private val documentAssembler: DocumentAssembler,
    private val subprocessManager: SubprocessManager
) : PipelineStrategy {

    private val logger = LoggerFactory.getLogger(MultiTurnPipelineStrategy::class.java)

    override suspend fun execute(
        config: BATaskConfig,
        progressReporter: ProgressReporter
    ): BATaskResult {
        val startTimeMs = System.currentTimeMillis()

        // Phase 1: Data Collection
        val collectedContext = collectData(config, progressReporter)

        // Phase 2: Multi-turn Loop
        val stepResponses = runMultiTurnLoop(collectedContext, config, progressReporter)

        // Phase 3: Assembly
        return assembleResult(stepResponses, collectedContext, startTimeMs, progressReporter)
    }

    // ── Phase 1 ─────────────────────────────────────────

    private suspend fun collectData(
        config: BATaskConfig,
        progressReporter: ProgressReporter
    ) = run {
        progressReporter.reportProgress(5, "Starting data collection")
        val ctx = dataCollector.collectData(config.rootTicketId)
        progressReporter.reportProgress(20, "Data collection complete: ${ctx.successCount}/4 tools succeeded")
        ctx
    }

    // ── Phase 2 ─────────────────────────────────────────

    private suspend fun runMultiTurnLoop(
        collectedContext: com.assistant.server.agent.ba.subprocess.pipeline.models.CollectedContext,
        config: BATaskConfig,
        progressReporter: ProgressReporter
    ): MutableList<StepResponse> {
        val stepResponses = mutableListOf<StepResponse>()
        var consecutiveFailures = 0

        // Initial 3 steps
        consecutiveFailures = runInitialSteps(
            collectedContext, config, progressReporter, stepResponses, consecutiveFailures
        )

        // Review loop — continues until stop condition met
        consecutiveFailures = runReviewLoop(
            stepResponses, config, progressReporter, consecutiveFailures
        )

        return stepResponses
    }

    private suspend fun runInitialSteps(
        ctx: com.assistant.server.agent.ba.subprocess.pipeline.models.CollectedContext,
        config: BATaskConfig,
        pr: ProgressReporter,
        responses: MutableList<StepResponse>,
        failures: Int
    ): Int {
        val analysisPrompt = StepPromptBuilder.buildAnalysisPrompt(ctx, config.docType)
        responses.add(sendStep("analysis", analysisPrompt, PipelineStepConfig.ANALYSIS, pr))

        val reqPrompt = StepPromptBuilder.buildRequirementsPrompt(
            responses.last().content, ctx.linkedTicketsData.data
        )
        responses.add(sendStep("requirements", reqPrompt, PipelineStepConfig.WRITING, pr))

        val accumulated = responses.joinToString("\n\n") { it.content }
        val writePrompt = StepPromptBuilder.buildWritingPrompt(accumulated, config.docType)
        responses.add(sendStep("writing", writePrompt, PipelineStepConfig.WRITING, pr))

        return failures
    }

    private suspend fun runReviewLoop(
        responses: MutableList<StepResponse>,
        config: BATaskConfig,
        pr: ProgressReporter,
        initialFailures: Int
    ): Int {
        var consecutiveFailures = initialFailures
        while (true) {
            val last = responses.last()
            consecutiveFailures = if (last.isEmpty || last.timedOut) consecutiveFailures + 1 else 0

            val qualityResult = DocumentQualityChecker.check(last.content, config.docType)
            val decision = stopCondition.evaluate(last, responses.dropLast(1), qualityResult.passed, consecutiveFailures)

            if (decision.shouldStop) {
                logger.info("Pipeline stopped: {}", decision.message)
                break
            }

            val reviewPrompt = StepPromptBuilder.buildReviewPrompt(qualityResult.feedback, last.content)
            responses.add(sendStep("review", reviewPrompt, PipelineStepConfig.REVIEW, pr))
        }
        return consecutiveFailures
    }

    // ── Phase 3 ─────────────────────────────────────────

    private suspend fun assembleResult(
        responses: List<StepResponse>,
        ctx: com.assistant.server.agent.ba.subprocess.pipeline.models.CollectedContext,
        startTimeMs: Long,
        pr: ProgressReporter
    ): BATaskResult {
        pr.reportProgress(90, "Assembling document")
        val result = documentAssembler.assemble(responses, ctx, startTimeMs)
        pr.reportProgress(95, "Document assembly complete")
        return result
    }

    // ── Helpers ──────────────────────────────────────────

    private suspend fun sendStep(
        stepName: String,
        prompt: String,
        config: PipelineStepConfig,
        progressReporter: ProgressReporter
    ): StepResponse {
        val start = System.currentTimeMillis()
        progressReporter.reportProgress(config.progressRange.first, "Step: $stepName")
        return try {
            val responseFlow = subprocessManager.sendCommand(AGENT_TYPE, prompt + "\n")
            val content = collectResponse(responseFlow, config.timeoutSeconds)
            val duration = System.currentTimeMillis() - start
            progressReporter.reportProgress(config.progressRange.last, "Step $stepName complete")
            StepResponse(stepName, content, duration, timedOut = false, isEmpty = content.isBlank())
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            logger.warn("Step {} failed: {}", stepName, e.message)
            StepResponse(stepName, "", duration, timedOut = false, isEmpty = true)
        }
    }

    private suspend fun collectResponse(flow: Flow<String>, timeoutSeconds: Int): String {
        val lines = mutableListOf<String>()
        withTimeoutOrNull(timeoutSeconds * 1000L) {
            flow.takeWhile { it != "---END---" }.collect { line -> lines.add(line) }
        }
        return lines.joinToString("\n").trim()
    }

    companion object {
        private const val AGENT_TYPE = "ba-agent"
    }
}
