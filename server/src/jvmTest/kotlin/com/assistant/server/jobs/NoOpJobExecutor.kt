package com.assistant.server.jobs

import com.assistant.document.DocumentAggregator
import com.assistant.document.models.GenerationContext
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.JobRepository

/**
 * No-op executor for tests — does nothing on execute.
 */
class NoOpJobExecutor(
    jobRepository: JobRepository,
    documentRepository: DocumentRepository
) : JobExecutor(
    aggregator = object : DocumentAggregator {
        override suspend fun aggregate(ticketId: String): GenerationContext =
            error("Not used in tests")
    },
    documentRepository = documentRepository,
    jobRepository = jobRepository
) {
    override suspend fun execute(
        jobId: String, ticketId: String, docType: String
    ) {
        // no-op for property/unit tests
    }
}
