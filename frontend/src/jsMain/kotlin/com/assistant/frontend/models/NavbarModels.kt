package com.assistant.frontend.models

import com.assistant.scan.ScanStatus
import kotlinx.serialization.Serializable

@Serializable
data class NavScanStatusResponse(
    val projectKey: String = "",
    val status: ScanStatus = ScanStatus.IDLE
)
