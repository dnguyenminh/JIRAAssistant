package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.AttachmentInfo
import com.assistant.ai.deepanalysis.models.CommentInfo
import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Prompt section builders for ticket data (overview, description,
 * sub-tasks, issue links, comments, changelog, attachments).
 *
 * Requirements: 18.1
 */

internal fun StringBuilder.appendSystemInstruction() {
    appendLine("You are a senior Scrum Master AI assistant.")
    appendLine("Analyze the following Jira ticket data thoroughly.")
    appendLine()
}

internal fun StringBuilder.appendTicketOverview(
    content: StructuredTicketContent
) {
    appendLine("=== TICKET OVERVIEW ===")
    appendLine("Summary: ${content.summary}")
    appendLine("Status: ${content.status}")
    appendLine("Priority: ${content.priority}")
    appendLine("Issue Type: ${content.issueType}")
    appendOverviewMetadata(content)
    appendLine()
}

internal fun StringBuilder.appendOverviewMetadata(
    content: StructuredTicketContent
) {
    appendLine("Assignee: ${content.assignee}")
    appendLine("Reporter: ${content.reporter}")
    if (content.storyPoints != null) {
        appendLine("Story Points: ${content.storyPoints}")
    }
    appendLine("Created: ${content.createdDate}")
    appendLine("Updated: ${content.updatedDate}")
    appendLabelsAndComponents(content)
}

internal fun StringBuilder.appendLabelsAndComponents(
    content: StructuredTicketContent
) {
    if (content.labels.isNotEmpty()) {
        appendLine("Labels: ${content.labels.joinToString(", ")}")
    }
    if (content.components.isNotEmpty()) {
        appendLine("Components: ${content.components.joinToString(", ")}")
    }
}

internal fun StringBuilder.appendDescriptionSection(
    content: StructuredTicketContent
) {
    if (content.description.isBlank()) return
    appendLine("=== TICKET DESCRIPTION ===")
    appendLine(content.description)
    appendClassifiedSections(content)
    appendLine()
}

internal fun StringBuilder.appendClassifiedSections(
    content: StructuredTicketContent
) {
    val cc = content.classifiedContent
    if (cc.asIsState.isNotBlank()) {
        appendLine("\n[As-Is State]: ${cc.asIsState}")
    }
    if (cc.toBeState.isNotBlank()) {
        appendLine("[To-Be State]: ${cc.toBeState}")
    }
    if (cc.acceptanceCriteria.isNotEmpty()) {
        appendLine("[Acceptance Criteria]:")
        cc.acceptanceCriteria.forEach { appendLine("  - $it") }
    }
}

internal fun StringBuilder.appendSubTasksSection(
    content: StructuredTicketContent
) {
    if (content.subTasks.isEmpty()) return
    appendLine("=== SUB-TASKS ===")
    content.subTasks.forEach { task ->
        appendLine("- [${task.status}] ${task.summary}")
    }
    appendLine()
}

internal fun StringBuilder.appendIssueLinksSection(
    content: StructuredTicketContent
) {
    if (content.issueLinks.isEmpty()) return
    appendLine("=== ISSUE LINKS ===")
    content.issueLinks.forEach { link ->
        appendLine("- ${link.relationshipType}: ${link.key} — ${link.summary}")
    }
    appendLine()
}

internal fun StringBuilder.appendCommentsSection(
    content: StructuredTicketContent
) {
    if (content.comments.isEmpty()) return
    appendLine("=== COMMENTS (most recent) ===")
    content.comments.forEach { comment ->
        appendLine("- [${comment.createdDate}] ${comment.author}: ${comment.content}")
    }
    appendLine()
}

internal fun StringBuilder.appendChangelogSection(
    content: StructuredTicketContent
) {
    if (content.changelog.isEmpty()) return
    appendLine("=== CHANGELOG ===")
    content.changelog.forEach { entry ->
        appendLine("- ${entry.changedDate}: ${entry.field} changed from '${entry.oldValue}' to '${entry.newValue}' by ${entry.changedBy}")
    }
    appendLine()
}

internal fun StringBuilder.appendAttachmentsSection(
    content: StructuredTicketContent
) {
    if (content.attachments.isEmpty()) return
    appendLine("=== ATTACHMENTS ===")
    content.attachments.forEach { att ->
        appendLine("- ${att.filename} (${att.mimeType}, ${att.size} bytes)")
    }
    appendLine()
}

/** Req 27.3, 11.4 — Inject linked ticket content into prompt. */
internal fun StringBuilder.appendLinkedTicketsContext(
    content: StructuredTicketContent
) {
    if (content.linkedTicketContents.isEmpty()) return
    appendLine("=== RELATED TICKETS CONTEXT ===")
    appendLine("The following are detailed contents of linked/blocking tickets. Use this information to enrich your analysis.")
    appendLine()
    for (linked in content.linkedTicketContents) {
        appendLine("--- ${linked.ticketId} (${linked.linkType}) ---")
        appendLine("Summary: ${linked.summary}")
        appendLine("Status: ${linked.status}")
        if (linked.description.isNotBlank()) {
            appendLine("Description: ${linked.description}")
        }
        appendLinkedComments(linked.comments)
        appendLinkedAttachments(linked.attachments)
        appendLine()
    }
}

/** Req 11.4 — Append comments from a linked ticket. */
private fun StringBuilder.appendLinkedComments(
    comments: List<CommentInfo>
) {
    if (comments.isEmpty()) return
    appendLine("Comments:")
    comments.forEach { c ->
        appendLine("  - [${c.createdDate}] ${c.author}: ${c.content}")
    }
}

/** Req 11.4 — Append attachment filenames from a linked ticket. */
private fun StringBuilder.appendLinkedAttachments(
    attachments: List<AttachmentInfo>
) {
    if (attachments.isEmpty()) return
    appendLine("Attachments:")
    attachments.forEach { a ->
        appendLine("  - ${a.filename} (${a.mimeType}, ${a.size} bytes)")
    }
}

/** Req 28.1, 3.1-3.5 — Instruct AI to generate Mermaid and draw.io diagrams. */
internal fun StringBuilder.appendDiagramInstructions() {
    appendLine()
    appendLine("=== DIAGRAM GENERATION ===")
    appendMermaidInstructions()
    appendDrawioInstructions()
    appendDrawioJsonExample()
    appendLine()
}

private fun StringBuilder.appendMermaidInstructions() {
    appendLine("Generate diagrams to visualize this ticket. Choose format per diagram:")
    appendLine("- format \"mermaid\": for flow, component, dependency diagrams (simple relationships)")
    appendLine("- format \"drawio\": for deployment, infrastructure, bpmn diagrams (rich shapes/icons)")
    appendLine()
    appendLine("Mermaid diagrams: provide valid Mermaid syntax in \"mermaidCode\".")
    appendLine("CRITICAL Mermaid syntax rules:")
    appendLine("- ALWAYS quote node labels with special chars: A[\"Label with (parens)\"]")
    appendLine("- NEVER use unquoted parentheses inside [] brackets: A[Bad (label)] → A[\"Bad (label)\"]")
    appendLine("- NEVER use semicolons at end of lines")
    appendLine("- Use simple ASCII for node IDs: A, B, C (not Vietnamese)")
    appendLine("- Keep labels short — max 50 chars per node")
}

private fun StringBuilder.appendDrawioInstructions() {
    appendLine("Draw.io diagrams: provide \"drawioMetadata\" with template, nodes, connections.")
    appendLine("Valid templates: flow, deployment, component, dependency, bpmn")
    appendLine("Valid node types: webapp, database, external_api, server, mobile, cloud, user, service, queue, cache")
    appendLine("Each node needs unique id, label, and type. Each connection needs from/to node ids.")
}

private fun StringBuilder.appendDrawioJsonExample() {
    appendLine()
    appendLine("Draw.io example:")
    appendLine("""{"type":"deployment","title":"System Architecture","format":"drawio",""")
    appendLine(""" "drawioMetadata":{"template":"deployment",""")
    appendLine("""  "nodes":[{"id":"web","label":"Web App","type":"webapp"},{"id":"db","label":"PostgreSQL","type":"database"}],""")
    appendLine("""  "connections":[{"from":"web","to":"db","label":"SQL"}]}}""")
}
