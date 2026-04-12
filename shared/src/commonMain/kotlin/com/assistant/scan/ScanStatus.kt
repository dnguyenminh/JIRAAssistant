package com.assistant.scan

import kotlinx.serialization.Serializable

@Serializable
enum class ScanStatus {
    IDLE, SCANNING, PAUSED, COMPLETED, CANCELLED
}
