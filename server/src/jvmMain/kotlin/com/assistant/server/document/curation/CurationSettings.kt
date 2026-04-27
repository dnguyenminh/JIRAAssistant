package com.assistant.server.document.curation

/**
 * Setting keys for the Prompt Curation Pipeline feature flag.
 *
 * The key "prompt_curation_enabled" controls whether JobExecutor
 * uses the CurationPipeline + CuratedPromptAssembler path or the
 * existing PromptAssembler with 200K budget.
 *
 * Default: "false" (disabled).
 *
 * Requirements: 8.2, 8.3
 */
object CurationSettings {
    const val PROMPT_CURATION_ENABLED = "prompt_curation_enabled"
    const val DEFAULT_ENABLED = "false"
}
