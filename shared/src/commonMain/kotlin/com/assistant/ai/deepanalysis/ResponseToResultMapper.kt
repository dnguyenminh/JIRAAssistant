package com.assistant.ai.deepanalysis

import com.assistant.ai.*
import com.assistant.ai.deepanalysis.models.*

/**
 * Maps intermediate AIResponseRoot to the domain AnalysisResult.
 * Validates Scrum Points and computes extraction_confidence.
 *
 * Requirements: 25.1, 25.4, 25.6
 */
internal object ResponseToResultMapper {

    fun map(ticketId: String, root: AIResponseRoot): AnalysisResult {
        val sectionCount = countPopulatedSections(root)
        val confidence = ResponseParserHelpers.computeConfidence(sectionCount)
        return AnalysisResult(
            ticketId = ticketId,
            context = mapRequirementSummary(root.requirementSummary),
            evolution = mapEvolution(root.evolution),
            complexity = mapComplexity(root.complexity),
            source = AnalysisSource.FRESH_AI,
            technicalDetails = mapTechnicalDetails(root.technicalDetails),
            acceptanceCriteria = mapAcceptanceCriteria(root.acceptanceCriteria),
            dependencies = mapDependencies(root.dependencies),
            analysisMetadata = buildMetadata(confidence),
            diagrams = root.diagrams.mapNotNull { mapDiagram(it) }
        )
    }

    private fun mapRequirementSummary(src: AIRequirementSummary): RequirementSummary {
        return RequirementSummary(
            unified = src.unified,
            affectedModules = src.affectedModules.map { mapModule(it) },
            businessSummary = src.businessSummary,
            asIsState = src.asIsState,
            toBeState = src.toBeState,
            extractedRequirements = src.extractedRequirements
        )
    }

    private fun mapModule(src: AIAffectedModule): AffectedModule {
        return AffectedModule(name = src.name, colorCategory = src.colorCategory)
    }

    private fun mapEvolution(src: List<AIEvolutionEntry>): List<EvolutionEntry> {
        return src.map { entry ->
            EvolutionEntry(
                version = entry.version,
                date = entry.date,
                description = entry.description,
                changeType = entry.changeType
            )
        }
    }

    private fun mapComplexity(src: AIComplexity): ComplexityAssessment {
        val normalizedPoints = ScrumPointsValidator.normalize(src.scrumPoints)
        return ComplexityAssessment(
            scrumPoints = normalizedPoints,
            description = src.description,
            kbReferences = src.kbReferences.map { mapKBRef(it) }
        )
    }

    private fun mapKBRef(src: AIKBReference): KBReference {
        return KBReference(ticketId = src.ticketId, similarityPercent = src.similarityPercent)
    }

    private fun mapTechnicalDetails(src: AITechnicalDetails): TechnicalDetails {
        return TechnicalDetails(
            apiSpecifications = src.apiSpecifications.map { mapApiSpec(it) },
            databaseChanges = src.databaseChanges.map { mapDbChange(it) },
            externalIntegrations = src.externalIntegrations.map { mapIntegration(it) }
        )
    }

    private fun mapApiSpec(src: AIApiSpec): ApiSpecification {
        return ApiSpecification(method = src.method, path = src.path, description = src.description)
    }

    private fun mapDbChange(src: AIDbChange): DatabaseChange {
        return DatabaseChange(
            tableName = src.tableName, operationType = src.operationType,
            columns = src.columns, description = src.description
        )
    }

    private fun mapIntegration(src: AIExternalIntegration): ExternalIntegration {
        return ExternalIntegration(
            serviceName = src.serviceName, protocol = src.protocol,
            endpoint = src.endpoint, description = src.description
        )
    }

    private fun mapAcceptanceCriteria(src: List<AIAcceptanceCriterion>): List<AcceptanceCriterion> {
        return src.map { AcceptanceCriterion(id = it.id, description = it.description, testabilityAssessment = it.testabilityAssessment) }
    }

    private fun mapDependencies(src: AIDependencies): DependencyInfo {
        return DependencyInfo(
            blockingIssues = src.blockingIssues.map { mapDepItem(it) },
            relatedIssues = src.relatedIssues.map { mapDepItem(it) },
            externalDependencies = src.externalDependencies
        )
    }

    private fun mapDepItem(src: AIDependencyItem): DependencyItem {
        return DependencyItem(
            key = src.key, summary = src.summary,
            relationshipType = src.relationshipType, riskLevel = src.riskLevel
        )
    }

    private fun buildMetadata(confidence: ExtractionConfidence): AnalysisMetadata {
        return AnalysisMetadata(extractionConfidence = confidence)
    }

    private fun countPopulatedSections(root: AIResponseRoot): Int {
        return ResponseParserHelpers.countSections(
            hasRequirementSummary = root.requirementSummary.unified.isNotBlank(),
            hasEvolution = root.evolution.isNotEmpty(),
            hasComplexity = root.complexity.description.isNotBlank(),
            hasTechnicalDetails = hasTechDetails(root.technicalDetails),
            hasAcceptanceCriteria = root.acceptanceCriteria.isNotEmpty(),
            hasDependencies = hasDeps(root.dependencies)
        )
    }

    private fun hasTechDetails(td: AITechnicalDetails): Boolean {
        return td.apiSpecifications.isNotEmpty() ||
            td.databaseChanges.isNotEmpty() ||
            td.externalIntegrations.isNotEmpty()
    }

    private fun hasDeps(deps: AIDependencies): Boolean {
        return deps.blockingIssues.isNotEmpty() ||
            deps.relatedIssues.isNotEmpty() ||
            deps.externalDependencies.isNotEmpty()
    }

    private fun mapDiagram(src: AIDiagram): DiagramData? {
        val format = src.format.ifBlank { "mermaid" }
        if (format == "drawio") return mapDrawioDiagram(src)
        return DiagramData(
            type = src.type, title = src.title,
            mermaidCode = src.mermaidCode, format = format
        )
    }

    private fun mapDrawioDiagram(src: AIDiagram): DiagramData? {
        val meta = src.drawioMetadata
        if (meta == null) {
            println("[ResponseMapper] Skipping drawio diagram '${src.title}': missing drawioMetadata")
            return null
        }
        val dedupedNodes = meta.nodes.distinctBy { it.id }
        val nodeIds = dedupedNodes.map { it.id }.toSet()
        val validConns = filterValidConnections(meta.connections, nodeIds)
        return DiagramData(
            type = src.type, title = src.title, format = "drawio",
            drawioMetadata = DrawioMetadata(
                template = meta.template.ifBlank { "component" },
                nodes = dedupedNodes.map { DrawioNode(it.id, it.label, it.type) },
                connections = validConns.map { DrawioConnection(it.from, it.to, it.label) }
            )
        )
    }

    private fun filterValidConnections(
        connections: List<AIDrawioConnection>,
        nodeIds: Set<String>
    ): List<AIDrawioConnection> {
        return connections.filter { conn ->
            val valid = conn.from in nodeIds && conn.to in nodeIds
            if (!valid) println("[ResponseMapper] Skipping connection ${conn.from}->${conn.to}: invalid node ref")
            valid
        }
    }
}
