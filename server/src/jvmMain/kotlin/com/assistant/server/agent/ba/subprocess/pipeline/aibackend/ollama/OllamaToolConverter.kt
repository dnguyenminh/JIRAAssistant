package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama

import com.assistant.agent.models.ToolDescriptor

/**
 * Extension function to convert a [ToolDescriptor] to an [OllamaTool].
 *
 * Since [ToolDescriptor] only has parameter names (no types),
 * all parameters default to type = "string".
 */
fun ToolDescriptor.toOllamaTool(): OllamaTool {
    val properties = parameterNames.associateWith { name ->
        OllamaToolProperty(
            type = "string",
            description = "Parameter: $name"
        )
    }
    return OllamaTool(
        function = OllamaToolFunction(
            name = this.name,
            description = this.description,
            parameters = OllamaToolParameters(
                properties = properties,
                required = parameterNames
            )
        )
    )
}
