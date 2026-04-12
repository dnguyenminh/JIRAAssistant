package com.assistant.server.attachment

/**
 * Downloads attachment files from Jira.
 * Requirements: 22.2
 */
interface AttachmentDownloader {
    /** Download file from URL to destPath. Returns true on success. */
    suspend fun download(contentUrl: String, destPath: String, authHeader: String): Boolean
}
