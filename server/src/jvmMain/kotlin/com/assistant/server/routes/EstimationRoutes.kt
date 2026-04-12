package com.assistant.server.routes

import com.assistant.domain.NewRequirement
import com.assistant.domain.ScrumEstimator
import com.assistant.domain.SimilarTicket
import com.assistant.rbac.Permission
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class EstimationRequest(
    val summary: String,
    val description: String,
    val featureArea: String? = null,
    val historicalTickets: List<HistoricalTicketDto> = emptyList()
)

@Serializable
data class HistoricalTicketDto(
    val ticketKey: String,
    val summary: String,
    val actualPoints: Double,
    val similarityScore: Double = 0.0
)

/**
 * Estimation routes — POST /api/estimation/estimate.
 * Requires Neural_Architect+ role (ANALYZE_AI permission).
 */
fun Routing.estimationRoutes() {
    val scrumEstimator by inject<ScrumEstimator>()

    route("/api/estimation") {
        withPermission(Permission.ANALYZE_AI) {
            post("/estimate") {
                val request = call.receive<EstimationRequest>()

                if (request.summary.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("summary is required"))
                    return@post
                }

                val requirement = NewRequirement(
                    summary = request.summary,
                    description = request.description,
                    featureArea = request.featureArea
                )

                val history = request.historicalTickets.map {
                    SimilarTicket(
                        ticketKey = it.ticketKey,
                        summary = it.summary,
                        actualPoints = it.actualPoints,
                        similarityScore = it.similarityScore
                    )
                }

                val estimation = scrumEstimator.estimate(requirement, history)
                call.respond(HttpStatusCode.OK, estimation)
            }
        }
    }
}
