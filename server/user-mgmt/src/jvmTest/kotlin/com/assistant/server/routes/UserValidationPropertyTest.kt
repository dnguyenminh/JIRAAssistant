package com.assistant.server.routes

// Feature: user-crud-profile, Property 1: Name validation rejects empty and whitespace-only strings
// Feature: user-crud-profile, Property 2: Email validation accepts valid emails and rejects invalid ones

import com.assistant.server.services.ValidationService
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import io.kotest.common.ExperimentalKotest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based tests for name and email validation.
 *
 * Covers Properties 1 and 2 from the design document.
 */
@OptIn(ExperimentalKotest::class)
class UserValidationPropertyTest {

    // ── Generators ──────────────────────────────────────────────

    /** Generates whitespace-only strings (spaces, tabs, newlines). */
    private val arbWhitespaceOnly: Arb<String> = Arb.element(
        "", " ", "  ", "\t", "\n", "\r\n", "   \t  ", "\t\t\t"
    )

    /** Generates strings with at least one non-whitespace character. */
    private val arbNonBlankName: Arb<String> =
        Arb.string(1..50, Codepoint.alphanumeric())
            .filter { it.isNotBlank() }

    /** Generates valid email local parts. */
    private val arbLocalPart: Arb<String> =
        Arb.string(1..15, Codepoint.alphanumeric())

    /** Generates valid email domain parts. */
    private val arbDomain: Arb<String> =
        Arb.string(2..10, Codepoint.az())

    /** Generates valid TLDs. */
    private val arbTld: Arb<String> = Arb.element(
        "com", "org", "net", "io", "dev", "co"
    )

    /** Generates valid email addresses. */
    private val arbValidEmail: Arb<String> = Arb.bind(
        arbLocalPart, arbDomain, arbTld
    ) { local, domain, tld -> "$local@$domain.$tld" }

    /** Generates random strings unlikely to be valid emails. */
    private val arbInvalidEmail: Arb<String> = Arb.element(
        "", " ", "noatsign", "@", "a@", "@b", "a@b",
        "a@.com", "@domain.com", "user@domain",
        "user name@domain.com", "user@@domain.com",
        "user@dom ain.com", ".user@domain.com"
    )

    // ─────────────────────────────────────────────────────────────
    // Property 1: Name validation rejects empty and whitespace-only
    //
    // For any string composed entirely of whitespace characters
    // (including the empty string), isValidName SHALL reject it.
    // For any string containing at least one non-whitespace character,
    // isValidName SHALL accept it.
    // **Validates: Requirements 1.2, 3.2**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Property 1 - rejects whitespace-only strings`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100), arbWhitespaceOnly
        ) { whitespace ->
            assertFalse(
                ValidationService.isValidName(whitespace),
                "Should reject whitespace-only: '$whitespace'"
            )
        }
    }

    @Test
    fun `Property 1 - accepts strings with non-whitespace`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100), arbNonBlankName
        ) { name ->
            assertTrue(
                ValidationService.isValidName(name),
                "Should accept non-blank name: '$name'"
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Property 2: Email validation accepts valid and rejects invalid
    //
    // For any string matching standard email format (local@domain.tld),
    // isValidEmail SHALL accept it. For any string not matching the
    // email format, isValidEmail SHALL reject it.
    // **Validates: Requirements 1.3, 3.3**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Property 2 - accepts valid email patterns`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100), arbValidEmail
        ) { email ->
            assertTrue(
                ValidationService.isValidEmail(email),
                "Should accept valid email: '$email'"
            )
        }
    }

    @Test
    fun `Property 2 - rejects invalid email patterns`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100), arbInvalidEmail
        ) { email ->
            assertFalse(
                ValidationService.isValidEmail(email),
                "Should reject invalid email: '$email'"
            )
        }
    }
}
