package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.*
import com.assistant.jira.*

/**
 * Maps Jira API models to Deep Analysis domain models.
 * Pure functions — no side effects, no I/O.
 * Requirements: 16.1-16.7
 */
object JiraFieldMappers {

    /** Req 16.3, 26.1 — Map sub-tasks to SubTaskInfo list. */
    fun mapSubTasks(subtasks: List<JiraSubtask>?): List<SubTaskInfo> {
        return subtasks?.map { sub ->
            SubTaskInfo(
                key = sub.key,
                summary = sub.fields?.summary ?: "",
                status = sub.fields?.status?.name ?: ""
            )
        } ?: emptyList()
    }

    /** Req 16.4 — Map issue links to IssueLinkInfo list. */
    fun mapIssueLinks(links: List<JiraIssueLink>?): List<IssueLinkInfo> {
        if (links.isNullOrEmpty()) return emptyList()
        return links.flatMap { link -> mapSingleIssueLink(link) }
    }

    private fun mapSingleIssueLink(link: JiraIssueLink): List<IssueLinkInfo> {
        val result = mutableListOf<IssueLinkInfo>()
        link.outwardIssue?.let { outward ->
            result.add(IssueLinkInfo(
                key = outward.key,
                summary = outward.fields?.summary ?: "",
                relationshipType = link.type?.outward ?: ""
            ))
        }
        link.inwardIssue?.let { inward ->
            result.add(IssueLinkInfo(
                key = inward.key,
                summary = inward.fields?.summary ?: "",
                relationshipType = link.type?.inward ?: ""
            ))
        }
        return result
    }

    /** Req 16.5 — Map attachments to AttachmentInfo list. */
    fun mapAttachments(
        attachments: List<JiraAttachment>?
    ): List<AttachmentInfo> {
        return attachments?.map { att ->
            AttachmentInfo(
                id = att.id,
                filename = att.filename,
                mimeType = att.mimeType ?: "",
                size = att.size,
                content = att.content ?: ""
            )
        } ?: emptyList()
    }

    /** Req 16.6 — Map comments (max 20 most recent). */
    fun mapComments(wrapper: JiraCommentWrapper?): List<CommentInfo> {
        val comments = wrapper?.comments ?: return emptyList()
        return comments.takeLast(MAX_COMMENTS).map { c ->
            CommentInfo(
                author = c.author?.displayName ?: "",
                createdDate = c.created ?: "",
                content = c.bodyText
            )
        }
    }

    /** Req 16.7 — Map changelog, filtering important fields. */
    fun mapChangelog(changelog: JiraChangelog?): List<ChangelogEntry> {
        val histories = changelog?.histories ?: return emptyList()
        return histories.flatMap { history ->
            mapChangeHistory(history)
        }
    }

    private fun mapChangeHistory(
        history: JiraChangeHistory
    ): List<ChangelogEntry> {
        return history.items
            .filter { it.field.lowercase() in TRACKED_FIELDS }
            .map { item ->
                ChangelogEntry(
                    field = item.field,
                    oldValue = item.fromString ?: "",
                    newValue = item.toString ?: "",
                    changedBy = history.author?.displayName ?: "",
                    changedDate = history.created ?: ""
                )
            }
    }

    private const val MAX_COMMENTS = 20
    private val TRACKED_FIELDS = setOf(
        "status", "priority", "story points",
        "assignee", "summary"
    )
}
