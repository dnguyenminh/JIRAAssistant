package com.assistant.frontend.services

/** Shared validation logic extracted from page controllers. */
object ValidationService {

    /** Basic URL validation: must start with http:// or https:// */
    fun isValidUrl(url: String): Boolean {
        val pattern = Regex(
            "^https?://[^\\s/\$.?#].[^\\s]*$",
            RegexOption.IGNORE_CASE
        )
        return pattern.matches(url)
    }
}
