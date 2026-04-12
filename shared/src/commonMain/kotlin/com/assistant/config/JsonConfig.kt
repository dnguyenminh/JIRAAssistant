package com.assistant.config

import kotlinx.serialization.json.Json

/**
 * Shared Json configuration for the entire project.
 * All serialization/deserialization should use this instance.
 */
object JsonConfig {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
