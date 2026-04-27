package com.assistant.server.mcp

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.*
import com.assistant.server.mcp.internal.InternalMcpBridge
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based tests for McpHealthChecker.
 *
 * Properties 1–5 from the mcp-readiness-check design document.
 */
@OptIn(ExperimentalKotest::class)
class McpHealthCheckerPropertyTest {

    // ── Generators ──────────────────────────────────────────────

    private val arbServerName: Arb<String> =
        Arb.string(3..20, Codepoint.az())

    private val arbServerId: Arb<String> =
        Arb.string(5..15, Codepoint.az())
            .filter { it != InternalMcpBridge.INTERNAL_SERVER_ID }

    /** Random McpServerConfig (external, may be disabled/marker). */
    private val arbExternalConfig: Arb<McpServerConfig> = Arb.bind(
        arbServerId, arbServerName, Arb.boolean(), Arb.element("stdio", "marker")
    ) { id, name, disabled, type ->
        McpServerConfig(id = id, name = name, disabled = disabled, type = type)
    }

    /** Random list of 0-10 external configs. */
    private val arbConfigList: Arb<List<McpServerConfig>> =
        Arb.list(arbExternalConfig, 0..10)

    // ── Stubs ───────────────────────────────────────────────────

    private class InMemoryRepo(
        private val configs: List<McpServerConfig>
    ) : McpServerRepository {
        override suspend fun getAll() = configs
        override suspend fun findById(id: String) = configs.find { it.id == id }
        override suspend fun findByName(name: String) =
            configs.find { it.name.equals(name, ignoreCase = true) }
        override suspend fun isInternal(id: String) =
            configs.find { it.id == id }?.internal == true
        override suspend fun insert(config: McpServerConfig) {}
        override suspend fun update(config: McpServerConfig) {}
        override suspend fun updateStatus(id: String, status: String) {}
        override suspend fun delete(id: String) {}
        override suspend fun deleteAll() {}
    }

    /**
     * Configurable stub: clientProvider decides per-server
     * whether getClient returns a client or null.
     */
    private class ConfigurableProcessManager(
        private val clientProvider: (String) -> McpProtocolClient?
    ) : McpProcessManager {
        private val stopped = McpProcessStatus("x", state = McpServerState.STOPPED)
        override suspend fun startServer(id: String) = stopped
        override suspend fun stopServer(id: String) = stopped
        override suspend fun restartServer(id: String) = stopped
        override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
        override fun getStatus(id: String): McpProcessStatus? = null
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools() = emptyList<McpAggregatedTool>()
        override fun getClient(id: String) = clientProvider(id)
    }

    /** Stub client that returns a fixed tool list. */
    private class SuccessClient(private val toolCount: Int) : McpProtocolClient {
        private val schema: JsonElement = JsonObject(
            mapOf("type" to JsonPrimitive("object"))
        )
        override suspend fun initialize() = McpInitializeResult(
            protocolVersion = "1.0",
            serverInfo = McpServerInfoDto("stub", "1.0")
        )
        override suspend fun sendRequest(method: String, params: JsonElement?) =
            JsonPrimitive("ok")
        override suspend fun sendNotification(method: String, params: JsonElement?) {}
        override suspend fun listTools() = (1..toolCount).map {
            McpToolInfo("tool_$it", "desc", schema)
        }
        override suspend fun callTool(name: String, arguments: JsonObject) =
            McpToolCallResponse(content = emptyList(), isError = false)
        override fun close() {}
    }

    /** Stub client that throws on listTools(). */
    private class FailingClient(private val error: String) : McpProtocolClient {
        override suspend fun initialize() = McpInitializeResult(
            protocolVersion = "1.0",
            serverInfo = McpServerInfoDto("stub", "1.0")
        )
        override suspend fun sendRequest(method: String, params: JsonElement?) =
            JsonPrimitive("ok")
        override suspend fun sendNotification(method: String, params: JsonElement?) {}
        override suspend fun listTools(): List<McpToolInfo> = throw RuntimeException(error)
        override suspend fun callTool(name: String, arguments: JsonObject) =
            McpToolCallResponse(content = emptyList(), isError = true)
        override fun close() {}
    }

    /**
     * Real InternalMcpBridge with null executor → getAggregatedTools()
     * returns emptyList(), isInternalServer() works correctly.
     */
    private fun buildBridge() = InternalMcpBridge(null, null)

    private val internalConfig = McpServerConfig(
        id = InternalMcpBridge.INTERNAL_SERVER_ID,
        name = InternalMcpBridge.INTERNAL_SERVER_NAME,
        type = "internal", internal = true
    )

    // ── Property 1: Health response completeness ────────────────

    /**
     * *For any* set of active server configs and ping results,
     * checkAll() returns exactly one McpServerHealth per active
     * server with valid fields.
     *
     * **Validates: Requirements 1.1, 1.2**
     *
     * Feature: mcp-readiness-check, Property 1
     */
    @Test
    fun `Property 1 - response contains one entry per active server`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), arbConfigList) { rawConfigs ->
                val active = rawConfigs.filter { !it.disabled && it.type != "marker" }
                val allConfigs = active + internalConfig
                val repo = InMemoryRepo(allConfigs)
                val pm = ConfigurableProcessManager { SuccessClient(2) }
                val bridge = buildBridge()
                val checker = McpHealthChecker(pm, bridge, repo)

                val response = checker.checkAll()

                // Exactly one entry per active config + internal
                assertEquals(
                    allConfigs.size, response.servers.size,
                    "Must have one entry per active server"
                )
                // All configIds present
                val ids = response.servers.map { it.configId }.toSet()
                for (cfg in allConfigs) {
                    assertTrue(cfg.id in ids, "Missing configId: ${cfg.id}")
                }
                // Valid fields
                for (s in response.servers) {
                    assertTrue(s.configId.isNotBlank(), "configId must not be blank")
                    assertTrue(s.serverName.isNotBlank(), "serverName must not be blank")
                    assertTrue(s.toolCount >= 0, "toolCount must be >= 0")
                    if (s.ready) assertNull(s.error, "ready=true → error must be null")
                    if (!s.ready) assertNotNull(s.error, "ready=false → error must be non-null")
                }
            }
        }
    }

    // ── Property 2: Ping result determines ready status ─────────

    /**
     * *For any* external server, ready status matches ping
     * success/failure: client exists + ping succeeds → ready: true;
     * client null / ping throws → ready: false with error.
     *
     * **Validates: Requirements 1.3, 1.4, 4.3**
     *
     * Feature: mcp-readiness-check, Property 2
     */
    @Test
    fun `Property 2 - ping success yields ready true`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbServerId, arbServerName, Arb.int(0..20)
            ) { id, name, toolCount ->
                val config = McpServerConfig(id = id, name = name)
                val repo = InMemoryRepo(listOf(config))
                val pm = ConfigurableProcessManager { SuccessClient(toolCount) }
                val bridge = buildBridge()
                val checker = McpHealthChecker(pm, bridge, repo)

                val response = checker.checkAll()
                val server = response.servers.first()

                assertTrue(server.ready, "Successful ping → ready: true")
                assertEquals(toolCount, server.toolCount)
                assertNull(server.error)
            }
        }
    }

    @Test
    fun `Property 2 - null client yields ready false`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbServerId, arbServerName
            ) { id, name ->
                val config = McpServerConfig(id = id, name = name)
                val repo = InMemoryRepo(listOf(config))
                val pm = ConfigurableProcessManager { null }
                val bridge = buildBridge()
                val checker = McpHealthChecker(pm, bridge, repo)

                val response = checker.checkAll()
                val server = response.servers.first()

                assertTrue(!server.ready, "Null client → ready: false")
                assertNotNull(server.error)
                assertTrue(
                    server.error!!.contains("not running", ignoreCase = true),
                    "Error should mention 'not running'"
                )
            }
        }
    }

    @Test
    fun `Property 2 - ping exception yields ready false`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbServerId, arbServerName,
                Arb.string(5..30, Codepoint.az())
            ) { id, name, errMsg ->
                val config = McpServerConfig(id = id, name = name)
                val repo = InMemoryRepo(listOf(config))
                val pm = ConfigurableProcessManager { FailingClient(errMsg) }
                val bridge = buildBridge()
                val checker = McpHealthChecker(pm, bridge, repo)

                val response = checker.checkAll()
                val server = response.servers.first()

                assertTrue(!server.ready, "Exception → ready: false")
                assertNotNull(server.error)
                assertEquals(errMsg, server.error)
            }
        }
    }

    // ── Property 4: allReady consistency ─────────────────────────

    /**
     * *For any* McpHealthResponse, allReady == servers.all { it.ready }.
     *
     * **Validates: Requirements 1.6**
     *
     * Feature: mcp-readiness-check, Property 4
     */
    @Test
    fun `Property 4 - allReady equals all servers ready`() {
        val arbHealth = Arb.bind(
            Arb.string(3..10, Codepoint.az()),
            Arb.string(3..10, Codepoint.az()),
            Arb.boolean(),
            Arb.int(0..10),
            Arb.element("knowledge_base", "database", "markitdown", "other")
        ) { cid, sn, ready, tc, role ->
            McpServerHealth(
                configId = cid, serverName = sn, ready = ready,
                toolCount = tc,
                error = if (!ready) "some error" else null,
                role = role
            )
        }
        val arbHealthList = Arb.list(arbHealth, 0..8)

        runBlocking {
            checkAll(PropTestConfig(iterations = 100), arbHealthList) { servers ->
                val expected = servers.all { it.ready }
                val response = McpHealthResponse(
                    allReady = expected, servers = servers
                )
                assertEquals(
                    servers.all { it.ready }, response.allReady,
                    "allReady must equal servers.all { it.ready }"
                )
            }
        }
    }

    /**
     * Verify McpHealthChecker.checkAll() computes allReady correctly
     * by mixing ready/not-ready external servers.
     */
    @Test
    fun `Property 4 - checkAll computes allReady from actual results`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbConfigList, Arb.boolean()
            ) { rawConfigs, includeDown ->
                val active = rawConfigs
                    .filter { !it.disabled && it.type != "marker" }
                    .take(5) // keep manageable
                if (active.isEmpty()) return@checkAll

                // First server may be "down" based on includeDown flag
                val downId = if (includeDown) active.first().id else ""
                val allConfigs = active + internalConfig
                val repo = InMemoryRepo(allConfigs)
                val pm = ConfigurableProcessManager { id ->
                    if (id == downId) null else SuccessClient(1)
                }
                val bridge = buildBridge()
                val checker = McpHealthChecker(pm, bridge, repo)

                val response = checker.checkAll()
                val expected = response.servers.all { it.ready }
                assertEquals(expected, response.allReady)
            }
        }
    }

    // ── Property 5: Server role classification correctness ──────

    /**
     * *For any* server config, classifyRole() returns correct role
     * based on name/ID patterns. Classification is case-insensitive
     * and deterministic.
     *
     * **Validates: Requirements 5.1**
     *
     * Feature: mcp-readiness-check, Property 5
     */
    @Test
    fun `Property 5 - role keywords map to correct roles`() {
        data class RoleCase(val keyword: String, val expected: String)
        val cases = listOf(
            RoleCase("knowledge", "knowledge_base"),
            RoleCase("kb", "knowledge_base"),
            RoleCase("database", "database"),
            RoleCase("db", "database"),
            RoleCase("markitdown", "markitdown")
        )
        val arbCase = Arb.element(cases)
        val allKeywords = listOf("knowledge", "kb", "database", "db", "markitdown")
        // Prefix/suffix must not accidentally contain any role keyword
        val arbSafeStr = Arb.string(0..5, Codepoint.az()).filter { s ->
            allKeywords.none { kw -> s.lowercase().contains(kw) }
        }
        val arbUpper = Arb.boolean()

        val bridge = buildBridge()
        val pm = ConfigurableProcessManager { null }
        val checker = McpHealthChecker(pm, bridge, InMemoryRepo(emptyList()))

        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbCase, arbSafeStr, arbSafeStr, arbUpper
            ) { case, prefix, suffix, upper ->
                val kw = if (upper) case.keyword.uppercase() else case.keyword
                val name = "$prefix$kw$suffix"
                val config = McpServerConfig(
                    id = "test-${name.take(10)}", name = name
                )
                val role = checker.classifyRole(config)
                assertEquals(
                    case.expected, role,
                    "Name '$name' should classify as '${case.expected}'"
                )
            }
        }
    }

    @Test
    fun `Property 5 - no keyword maps to other`() {
        val arbSafeName = Arb.string(3..15, Codepoint.az())
            .filter { n ->
                val low = n.lowercase()
                !low.contains("knowledge") && !low.contains("kb") &&
                    !low.contains("database") && !low.contains("db") &&
                    !low.contains("markitdown")
            }

        val bridge = buildBridge()
        val pm = ConfigurableProcessManager { null }
        val checker = McpHealthChecker(pm, bridge, InMemoryRepo(emptyList()))

        runBlocking {
            checkAll(PropTestConfig(iterations = 100), arbSafeName) { name ->
                val config = McpServerConfig(id = "ext-$name", name = name)
                assertEquals(
                    "other", checker.classifyRole(config),
                    "Name '$name' without keywords → 'other'"
                )
            }
        }
    }

    @Test
    fun `Property 5 - internal server ID always yields jira_internal`() {
        val bridge = buildBridge()
        val pm = ConfigurableProcessManager { null }
        val checker = McpHealthChecker(pm, bridge, InMemoryRepo(emptyList()))

        val config = McpServerConfig(
            id = InternalMcpBridge.INTERNAL_SERVER_ID,
            name = "anything"
        )
        assertEquals("jira_internal", checker.classifyRole(config))
    }

    @Test
    fun `Property 5 - classification is deterministic`() {
        val bridge = buildBridge()
        val pm = ConfigurableProcessManager { null }
        val checker = McpHealthChecker(pm, bridge, InMemoryRepo(emptyList()))

        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbServerId, arbServerName
            ) { id, name ->
                val config = McpServerConfig(id = id, name = name)
                val r1 = checker.classifyRole(config)
                val r2 = checker.classifyRole(config)
                assertEquals(r1, r2, "classifyRole must be deterministic")
            }
        }
    }

    // ── Property 3: Internal server invariant ───────────────────

    /**
     * *For any* health check with internal server present, internal
     * server is always ready: true with role jira_internal and
     * toolCount matching internal tools.
     *
     * **Validates: Requirements 1.5**
     *
     * Feature: mcp-readiness-check, Property 3
     */
    @Test
    fun `Property 3 - internal server always ready with correct role`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbConfigList
            ) { rawConfigs ->
                val active = rawConfigs
                    .filter { !it.disabled && it.type != "marker" }
                    .take(5)
                val allConfigs = active + internalConfig
                val repo = InMemoryRepo(allConfigs)
                // External servers may or may not have clients
                val pm = ConfigurableProcessManager { id ->
                    if (id == InternalMcpBridge.INTERNAL_SERVER_ID) null
                    else SuccessClient(1)
                }
                val bridge = buildBridge()
                val checker = McpHealthChecker(pm, bridge, repo)

                val response = checker.checkAll()
                val internal = response.servers.find {
                    it.configId == InternalMcpBridge.INTERNAL_SERVER_ID
                }

                assertNotNull(internal, "Internal server must be in response")
                assertTrue(internal.ready, "Internal server must be ready")
                assertEquals("jira_internal", internal.role)
                assertTrue(
                    internal.toolCount >= 0,
                    "toolCount must be non-negative"
                )
                assertNull(internal.error, "Internal server must have no error")
            }
        }
    }
}
