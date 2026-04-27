package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * API specification extracted from ticket description.
 * Requirements: 19.2
 */
@Serializable
data class ApiSpecification(
    val method: String = "",
    val path: String = "",
    val description: String = ""
)

/**
 * Database change extracted from ticket description.
 * Requirements: 19.2
 */
@Serializable
data class DatabaseChange(
    val tableName: String = "",
    val operationType: String = "",
    val columns: List<String> = emptyList(),
    val description: String = ""
)

/**
 * External integration dependency.
 * Requirements: 19.2
 */
@Serializable
data class ExternalIntegration(
    val serviceName: String = "",
    val protocol: String = "",
    val endpoint: String = "",
    val description: String = ""
)

/**
 * Technical details extracted from deep analysis.
 * Contains API specs, DB changes, and external integrations.
 * Requirements: 19.2
 */
@Serializable
data class TechnicalDetails(
    val apiSpecifications: List<ApiSpecification> = emptyList(),
    val databaseChanges: List<DatabaseChange> = emptyList(),
    val externalIntegrations: List<ExternalIntegration> = emptyList()
)
