package com.assistant.server.jobs

import com.assistant.ai.*

/** Stub AIAgent for subprocess-generated documents (logging only). */
internal object SubprocessAgentStub : AIAgent {
    override suspend fun analyze(prompt: String, context: AIContext?) =
        AIResult.Failure("Not used — subprocess document")
    override fun getAgentName() = "BA Subprocess Orchestrator"
}
