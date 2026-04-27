package com.assistant.server.agent.engine

import com.assistant.agent.engine.PhaseDefinition
import com.assistant.agent.engine.ThinkingLoopEngine
import com.assistant.agent.engine.ThinkingLoopResult
import com.assistant.agent.memory.StructuredMemory
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.tool.ToolRegistry
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * Drives an agent through its defined phases sequentially.
 * Evaluates entry/exit conditions, handles loopback with
 * maxIterations guard, per-phase and total timeouts.
 */
class ThinkingLoopEngineImpl(
    private val maxIterations: Int = 3,
    private val totalTimeoutMs: Long = 120_000
) : ThinkingLoopEngine {

    private val logger = LoggerFactory.getLogger(
        ThinkingLoopEngineImpl::class.java
    )

    override suspend fun execute(
        phases: List<PhaseDefinition>,
        memory: StructuredMemory,
        toolRegistry: ToolRegistry,
        reporter: ProgressReporter
    ): ThinkingLoopResult {
        val context = LoopContext(phases, memory, toolRegistry, reporter)
        return runWithTotalTimeout(context)
    }

    private suspend fun runWithTotalTimeout(
        ctx: LoopContext
    ): ThinkingLoopResult {
        val start = System.currentTimeMillis()
        try {
            withTimeout(totalTimeoutMs) {
                executePhases(ctx)
            }
        } catch (e: Exception) {
            handleTotalTimeout(ctx, e)
        }
        return buildResult(ctx, start)
    }

    private suspend fun executePhases(ctx: LoopContext) {
        var index = 0
        while (index < ctx.phases.size) {
            val phase = ctx.phases[index]
            index = processPhase(ctx, phase, index)
        }
    }

    private suspend fun processPhase(
        ctx: LoopContext,
        phase: PhaseDefinition,
        index: Int
    ): Int {
        if (!evaluateEntry(ctx, phase)) {
            ctx.log("Skipping phase '${phase.name}': entry=false")
            return index + 1
        }
        ctx.reporter.reportPhase(
            phase.name, index, ctx.phases.size
        )
        ctx.log("Entering phase '${phase.name}'")
        ctx.phasesExecuted++
        runPhaseAction(ctx, phase)
        return resolveNextIndex(ctx, phase, index)
    }

    private fun evaluateEntry(
        ctx: LoopContext,
        phase: PhaseDefinition
    ): Boolean = try {
        phase.entryCondition(ctx.memory)
    } catch (e: Exception) {
        logger.warn("Entry condition error: {}", e.message)
        false
    }

    private suspend fun runPhaseAction(
        ctx: LoopContext,
        phase: PhaseDefinition
    ) {
        try {
            val timeoutMs = phase.maxDurationSeconds * 1000L
            withTimeout(timeoutMs) {
                phase.phaseAction(ctx.memory, ctx.toolRegistry)
            }
        } catch (e: Exception) {
            ctx.log("Phase '${phase.name}' error: ${e.message}")
            logger.warn("Phase action error: {}", e.message)
        }
    }

    private fun resolveNextIndex(
        ctx: LoopContext,
        phase: PhaseDefinition,
        index: Int
    ): Int {
        val exitOk = evaluateExit(ctx, phase)
        if (exitOk || phase.loopbackTarget == null) {
            return index + 1
        }
        return handleLoopback(ctx, phase, index)
    }

    private fun evaluateExit(
        ctx: LoopContext,
        phase: PhaseDefinition
    ): Boolean = try {
        phase.exitCondition(ctx.memory)
    } catch (e: Exception) {
        logger.warn("Exit condition error: {}", e.message)
        true
    }

    private fun handleLoopback(
        ctx: LoopContext,
        phase: PhaseDefinition,
        currentIndex: Int
    ): Int {
        ctx.iterationCount++
        if (ctx.iterationCount >= maxIterations) {
            ctx.log("Max iterations reached at '${phase.name}'")
            return currentIndex + 1
        }
        val target = ctx.phases.indexOfFirst {
            it.name == phase.loopbackTarget
        }
        if (target < 0) return currentIndex + 1
        ctx.log("Looping back to '${phase.loopbackTarget}'")
        return target
    }

    private fun handleTotalTimeout(ctx: LoopContext, e: Exception) {
        ctx.log("Total timeout reached: ${e.message}")
        logger.warn("Total execution timeout: {}", e.message)
    }

    private fun buildResult(
        ctx: LoopContext,
        startMs: Long
    ) = ThinkingLoopResult(
        reasoningLog = ctx.reasoningLog.toList(),
        phasesExecuted = ctx.phasesExecuted,
        totalDurationMs = System.currentTimeMillis() - startMs
    )
}
