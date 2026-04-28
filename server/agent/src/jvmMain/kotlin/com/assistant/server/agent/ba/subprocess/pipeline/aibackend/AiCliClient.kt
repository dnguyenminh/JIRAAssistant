package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliType
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ProcessMode

/**
 * CLI-specific extension of [AiBackend] with process mode and CLI type.
 */
interface AiCliClient : AiBackend {
    val type: AiCliType
    var processMode: ProcessMode
}
