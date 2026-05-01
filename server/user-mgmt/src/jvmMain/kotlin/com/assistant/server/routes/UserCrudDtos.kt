package com.assistant.server.routes

import kotlinx.serialization.Serializable

/**
 * Request DTO for creating a new user.
 */
@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val role: String,
    val status: String = "ACTIVE"
)

/**
 * Request DTO for updating user name and email.
 */
@Serializable
data class UpdateUserRequest(
    val name: String,
    val email: String
)

/**
 * Request DTO for updating user status (ACTIVE/DISABLED).
 */
@Serializable
data class UpdateStatusRequest(
    val status: String
)
