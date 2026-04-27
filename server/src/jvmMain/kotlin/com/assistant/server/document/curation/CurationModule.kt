package com.assistant.server.document.curation

import org.koin.dsl.module

/**
 * Koin module for the Curation Pipeline components.
 *
 * Requirements: 8.1
 */
val curationModule = module {
    single<TemporalClassifier> { DefaultTemporalClassifier() }
    single<CommentSummarizer> { DefaultCommentSummarizer() }
    single<AttachmentCurator> { DefaultAttachmentCurator() }
    single<BudgetEnforcer> { DefaultBudgetEnforcer() }
    single<McpToolRegistrar> { DefaultMcpToolRegistrar() }
    single<CurationPipeline> {
        DefaultCurationPipeline(
            temporalClassifier = get(),
            commentSummarizer = get(),
            attachmentCurator = get(),
            budgetEnforcer = get()
        )
    }
}
