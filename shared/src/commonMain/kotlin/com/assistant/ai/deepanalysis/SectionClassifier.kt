package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.ClassifiedContent

/**
 * Classifies raw ticket description text into structured sections.
 *
 * Recognizes: As-Is/To-Be states, API specifications, database changes,
 * external dependencies, and acceptance criteria via pattern matching.
 * Falls back to raw_description with LOW confidence when no structured
 * sections are identified.
 *
 * Requirements: 17.1-17.6
 */
interface SectionClassifier {
    fun classify(description: String): ClassifiedContent
}
