package com.assistant.ai.deepanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for SectionClassifierImpl — API, DB, external deps,
 * and acceptance criteria extraction.
 * Requirements: 17.2, 17.3, 17.4, 17.5
 */
class SectionClassifierExtractionTest {

    private val classifier = SectionClassifierImpl()

    // ── Req 17.2: API specification extraction ──

    @Test
    fun `classify extracts GET endpoint`() {
        val desc = "GET /api/users Fetch all users"
        val result = classifier.classify(desc)
        assertEquals(1, result.apiSpecifications.size)
        assertEquals("GET", result.apiSpecifications[0].method)
        assertEquals("/api/users", result.apiSpecifications[0].path)
    }

    @Test
    fun `classify extracts multiple HTTP methods`() {
        val desc = """
            POST /api/users Create user
            PUT /api/users/{id} Update user
            DELETE /api/users/{id} Remove user
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(3, result.apiSpecifications.size)
        val methods = result.apiSpecifications.map { it.method }
        assertTrue(methods.containsAll(listOf("POST", "PUT", "DELETE")))
    }

    @Test
    fun `classify extracts API under API heading`() {
        val desc = """
            ## API
            GET /api/orders List orders
            POST /api/orders Create order
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(2, result.apiSpecifications.size)
    }

    // ── Req 17.3: Database change extraction ──

    @Test
    fun `classify extracts CREATE TABLE`() {
        val desc = "CREATE TABLE users (id INT, name VARCHAR)"
        val result = classifier.classify(desc)
        assertEquals(1, result.databaseChanges.size)
        assertEquals("users", result.databaseChanges[0].tableName)
        assertEquals("CREATE", result.databaseChanges[0].operationType)
    }

    @Test
    fun `classify extracts ALTER TABLE`() {
        val desc = "ALTER TABLE orders ADD COLUMN status VARCHAR"
        val result = classifier.classify(desc)
        assertEquals(1, result.databaseChanges.size)
        assertEquals("orders", result.databaseChanges[0].tableName)
        assertEquals("ALTER", result.databaseChanges[0].operationType)
    }

    @Test
    fun `classify extracts DROP TABLE`() {
        val desc = "DROP TABLE legacy_data"
        val result = classifier.classify(desc)
        assertEquals(1, result.databaseChanges.size)
        assertEquals("legacy_data", result.databaseChanges[0].tableName)
        assertEquals("DROP", result.databaseChanges[0].operationType)
    }

    @Test
    fun `classify extracts multiple DB changes`() {
        val desc = """
            CREATE TABLE users (id INT)
            ALTER TABLE orders ADD COLUMN total DECIMAL
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(2, result.databaseChanges.size)
    }

    // ── Req 17.4: External dependency extraction ──

    @Test
    fun `classify extracts https URL as external dependency`() {
        val desc = "Integrate with https://auth.example.com/oauth"
        val result = classifier.classify(desc)
        assertEquals(1, result.externalDependencies.size)
        assertEquals("https", result.externalDependencies[0].protocol)
        assertTrue(result.externalDependencies[0].endpoint.contains("auth.example.com"))
    }

    @Test
    fun `classify extracts grpc URL as external dependency`() {
        val desc = "Call grpc://payment-service:9090/charge"
        val result = classifier.classify(desc)
        assertEquals(1, result.externalDependencies.size)
        assertEquals("grpc", result.externalDependencies[0].protocol)
    }

    @Test
    fun `classify extracts multiple protocol URLs`() {
        val desc = """
            https://api.stripe.com/v1/charges
            grpc://inventory-svc:9090/check
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(2, result.externalDependencies.size)
    }

    // ── Req 17.5: Acceptance criteria extraction ──

    @Test
    fun `classify extracts bullet list under AC heading`() {
        val desc = """
            ## Acceptance Criteria
            - User can log in with email
            - User receives confirmation email
            - Admin can view audit log
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(3, result.acceptanceCriteria.size)
        assertTrue(result.acceptanceCriteria[0].contains("log in"))
    }

    @Test
    fun `classify extracts GIVEN WHEN THEN patterns`() {
        val desc = """
            - GIVEN a registered user
            - WHEN they submit the form
            - THEN the order is created
        """.trimIndent()
        val result = classifier.classify(desc)
        assertTrue(result.acceptanceCriteria.isNotEmpty())
    }

    @Test
    fun `classify extracts AC under Definition of Done heading`() {
        val desc = """
            ## Definition of Done
            - All unit tests pass
            - Code review approved
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(2, result.acceptanceCriteria.size)
    }

    // ── rawDescription always preserved ──

    @Test
    fun `classify always preserves rawDescription`() {
        val desc = """
            ## As-Is
            Old system.
            POST /api/test endpoint
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(desc, result.rawDescription)
    }
}
