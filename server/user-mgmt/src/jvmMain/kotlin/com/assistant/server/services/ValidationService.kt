package com.assistant.server.services

/**
 * Validation functions for user CRUD operations.
 *
 * Name validation: rejects empty and whitespace-only strings.
 * Email validation: regex-based standard email format check.
 */
object ValidationService {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9][A-Za-z0-9+_.-]*@[A-Za-z0-9][A-Za-z0-9.-]*\\.[A-Za-z]{2,}$"
    )

    /**
     * Returns true if the name contains at least one non-whitespace character.
     */
    fun isValidName(name: String): Boolean {
        return name.isNotBlank()
    }

    /**
     * Returns true if the email matches standard email format.
     */
    fun isValidEmail(email: String): Boolean {
        return EMAIL_REGEX.matches(email)
    }
}
