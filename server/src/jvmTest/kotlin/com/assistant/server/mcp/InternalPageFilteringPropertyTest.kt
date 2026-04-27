package com.assistant.server.mcp

import com.assistant.auth.UserRole
import com.assistant.rbac.Permission
import com.assistant.rbac.PermissionMatrix
import com.assistant.server.mcp.internal.UserContext
import com.assistant.server.mcp.internal.handlers.NavigationHandlers
import com.assistant.server.mcp.internal.handlers.PAGE_REGISTRY
import com.assistant.server.mcp.internal.handlers.PageInfo
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 10: RBAC-filtered page listing
 *
 * For any user role (Reader, Neural_Architect, Administrator),
 * `list_available_pages` SHALL return only and exactly the pages
 * that role has permission to access. No page missing or extra
 * compared to RBAC rules.
 *
 * **Validates: Requirements AC 6.76**
 *
 * Feature: mcp-servers, Property 10: RBAC-filtered page listing
 */
@OptIn(ExperimentalKotest::class)
class InternalPageFilteringPropertyTest {

    private val handlers = NavigationHandlers()
    private val json = Json { ignoreUnknownKeys = true }
    private val arbRole = Arb.of(UserRole.entries.toList())
    private val allPages = PAGE_REGISTRY.values.toList()

    /** Compute expected pages for a role using PermissionMatrix. */
    private fun expectedPages(role: UserRole): Set<String> {
        val perms = PermissionMatrix.getPermissions(role)
        return allPages.filter { page ->
            page.requiredPermission == null ||
                perms.contains(safePermission(page.requiredPermission))
        }.map { it.page }.toSet()
    }

    /** Parse page names from handler response JSON. */
    private fun parsePages(responseText: String): Set<String> {
        val pages = json.decodeFromString<List<PageInfo>>(responseText)
        return pages.map { it.page }.toSet()
    }

    private fun safePermission(name: String?): Permission? = try {
        name?.let { Permission.valueOf(it) }
    } catch (_: Exception) { null }

    private fun ctxFor(role: UserRole) =
        UserContext("test-user", role.name, "test@test.com")

    /**
     * Property 10a — Handler returns exactly the RBAC-expected pages.
     */
    @Test
    fun `Property 10a - filtered pages match RBAC permissions`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbRole) { role ->
                val ctx = ctxFor(role)
                val response = handlers.handleListAvailablePages(JsonObject(emptyMap()), ctx)
                val text = response.content.firstOrNull()?.text ?: ""
                val actual = parsePages(text)
                val expected = expectedPages(role)
                assertEquals(
                    expected, actual,
                    "Role $role: expected=$expected, actual=$actual"
                )
            }
        }
    }

    /**
     * Property 10b — Administrator sees ALL pages.
     */
    @Test
    fun `Property 10b - Administrator sees all pages`() {
        runBlocking {
            val ctx = ctxFor(UserRole.ADMINISTRATOR)
            val response = handlers.handleListAvailablePages(JsonObject(emptyMap()), ctx)
            val text = response.content.firstOrNull()?.text ?: ""
            val actual = parsePages(text)
            val allPageNames = allPages.map { it.page }.toSet()
            assertEquals(
                allPageNames, actual,
                "Administrator must see all ${allPageNames.size} pages"
            )
        }
    }

    /**
     * Property 10c — No role sees pages it lacks permission for.
     */
    @Test
    fun `Property 10c - no extra pages beyond permissions`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbRole) { role ->
                val perms = PermissionMatrix.getPermissions(role)
                val ctx = ctxFor(role)
                val response = handlers.handleListAvailablePages(JsonObject(emptyMap()), ctx)
                val text = response.content.firstOrNull()?.text ?: ""
                val actual = parsePages(text)
                for (pageName in actual) {
                    val page = PAGE_REGISTRY[pageName]!!
                    val reqPerm = safePermission(page.requiredPermission)
                    if (reqPerm != null) {
                        assertTrue(
                            perms.contains(reqPerm),
                            "Role $role should NOT see '$pageName' " +
                                "(requires ${page.requiredPermission})"
                        )
                    }
                }
            }
        }
    }

    /**
     * Property 10d — No permitted page is missing from the result.
     */
    @Test
    fun `Property 10d - no permitted page is missing`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbRole) { role ->
                val expected = expectedPages(role)
                val ctx = ctxFor(role)
                val response = handlers.handleListAvailablePages(JsonObject(emptyMap()), ctx)
                val text = response.content.firstOrNull()?.text ?: ""
                val actual = parsePages(text)
                for (page in expected) {
                    assertTrue(
                        actual.contains(page),
                        "Role $role is missing permitted page '$page'"
                    )
                }
            }
        }
    }
}
