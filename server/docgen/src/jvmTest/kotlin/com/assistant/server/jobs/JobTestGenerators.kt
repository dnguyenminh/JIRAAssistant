package com.assistant.server.jobs

import com.assistant.document.models.ApprovalStatus
import com.assistant.document.models.DocumentType
import com.assistant.document.models.GenerationJob
import com.assistant.document.models.JobStatus
import com.assistant.server.db.GeneratedDocumentMeta
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/** Arb generators for document-job-manager property tests. */
object JobTestGenerators {

    fun arbJobStatus(): Arb<JobStatus> = Arb.enum<JobStatus>()

    fun arbDocumentType(): Arb<DocumentType> = Arb.enum<DocumentType>()

    fun arbApprovalStatus(): Arb<ApprovalStatus> = Arb.enum<ApprovalStatus>()

    fun arbTicketId(): Arb<String> =
        Arb.stringPattern("TICKET-[0-9]{1,5}")

    fun arbUserId(): Arb<String> =
        Arb.stringPattern("user-[a-z]{3,8}")

    fun arbPhase(): Arb<String> = Arb.of(
        "QUEUED", "AGGREGATING_DATA", "GENERATING_DOCUMENT", "SAVING", "COMPLETE"
    )

    fun arbGenerationJob(): Arb<GenerationJob> = Arb.bind(
        Arb.uuid(),
        arbTicketId(),
        arbDocumentType(),
        arbJobStatus(),
        Arb.int(0..100),
        arbPhase(),
        Arb.uuid().orNull(0.3),
        arbUserId(),
        Arb.of("2024-01-01T00:00:00Z", "2025-06-01T12:00:00Z"),
        Arb.string(5..30).orNull(0.5)
    ) { id, ticket, docType, status, pct, phase, chain, user, ts, err ->
        GenerationJob(
            jobId = id.toString(),
            ticketId = ticket,
            documentType = docType.name,
            status = status.name,
            progressPercent = pct,
            phase = phase,
            chainId = chain?.toString(),
            createdBy = user,
            createdAt = ts,
            updatedAt = ts,
            errorMessage = err
        )
    }

    fun arbDocumentMeta(): Arb<GeneratedDocumentMeta> = Arb.bind(
        arbDocumentType(),
        arbApprovalStatus(),
        Arb.int(1..20).orNull(0.3)
    ) { docType, approval, version ->
        GeneratedDocumentMeta(
            documentType = docType.name,
            generatedAt = "2024-01-01T00:00:00Z",
            aiProviderUsed = "test-provider",
            approvalStatus = approval.name,
            versionNumber = version,
            hasDraft = approval == ApprovalStatus.DRAFT
        )
    }
}
