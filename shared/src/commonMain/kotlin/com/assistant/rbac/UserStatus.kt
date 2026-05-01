package com.assistant.rbac

import kotlinx.serialization.Serializable

@Serializable
enum class UserStatus {
    ACTIVE, DISABLED, PENDING
}
