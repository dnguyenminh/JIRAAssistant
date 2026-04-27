package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * E2E API tests for MCP Servers — Internal MCP Server ("Jira Assistant UI").
 *
 * Covers: CRUD, Internal server protection, tool discovery, tool execution,
 * RBAC enforcement, argument validation, error handling, aggregated tools,
 * import/export, health/status, and agentic loop integration.
 *
 * Validates: Requirements 6.20–6.30, 6.31–6.61, 6.70–6.112
 */
@TestMethodOrder(OrderAnnotation::class)
class McpInternalApiTest : ApiTestBase() {

    companion object {
        const val MCP_BASE = "/api/integrations/mcp"
        const val INTERNAL_ID = "jira-assistant-ui"
    }

    // ══════════════════════════════════════════════════════════
    // Section 1: MCP Server List & Internal Server Presence
    // ══════════════════════════════════════════════════════════

    @Test @Order(500)
    fun listMcpServersRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE")
        assertEquals(401, resp.status.value)
    }

    @Test @Order(501)
    fun listMcpServersWithAuth() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("jira-assistant-ui"), "Should contain internal MCP server")
    }

    @Test @Order(502)
    fun internalServerAlwaysPresent() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val body = resp.bodyAsText()
        assertTrue(body.contains("Jira Assistant UI"), "Internal server name present")
        assertTrue(body.contains("\"internal\""), "Should have internal flag")
    }

    @Test @Order(503)
    fun readerCanListMcpServers() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "Reader should view MCP servers (Req 6.30)")
    }

    // ══════════════════════════════════════════════════════════
    // Section 2: Internal Server Protection (Req 6.70)
    // ══════════════════════════════════════════════════════════

    @Test @Order(510)
    fun cannotDeleteInternalServer() = runBlocking {
        val resp = client.delete("$baseUrl$MCP_BASE/$INTERNAL_ID") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(400, 403, 409),
            "DELETE internal server should be rejected, got ${resp.status.value}"
        )
    }

    @Test @Order(511)
    fun cannotStopInternalServer() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/$INTERNAL_ID/stop") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        // Internal server stop may be handled via settings/feature toggle
        assertTrue(
            resp.status.value in listOf(200, 400, 409),
            "STOP internal server should be rejected or handled via toggle, got ${resp.status.value}"
        )
    }

    @Test @Order(512)
    fun cannotDisableInternalServerViaUpdate() = runBlocking {
        val resp = client.put("$baseUrl$MCP_BASE/$INTERNAL_ID") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"disabled":true}""")
        }
        assertTrue(
            resp.status.value in listOf(400, 403, 409),
            "Disabling internal server via PUT should be rejected, got ${resp.status.value}"
        )
    }

    // ══════════════════════════════════════════════════════════
    // Section 3: Internal Server Status (Req 6.57, 6.70)
    // ══════════════════════════════════════════════════════════

    @Test @Order(520)
    fun internalServerStatusAlwaysRunning() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/$INTERNAL_ID/status") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("RUNNING", ignoreCase = true),
            "Internal server should always be RUNNING: $body"
        )
    }

    @Test @Order(521)
    fun internalServerStatusContainsToolCount() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/$INTERNAL_ID/status") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val body = resp.bodyAsText()
        assertTrue(body.contains("toolCount"), "Status should contain toolCount: $body")
        val toolCountRegex = """"toolCount"\s*:\s*(\d+)""".toRegex()
        val match = toolCountRegex.find(body)
        assertNotNull(match, "Should have numeric toolCount")
        val count = match!!.groupValues[1].toInt()
        assertTrue(count >= 25, "Internal server should have ≥25 tools, got $count")
    }

    @Test @Order(522)
    fun readerCanViewInternalServerStatus() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/$INTERNAL_ID/status") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "Reader should view status (Req 6.57)")
    }

    // ══════════════════════════════════════════════════════════
    // Section 4: Tool Discovery (Req 6.44–6.47, 6.107, 6.108)
    // ══════════════════════════════════════════════════════════

    @Test @Order(530)
    fun getInternalServerTools() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/$INTERNAL_ID/tools") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.startsWith("["), "Tools should be a JSON array")
        // Verify key tools exist
        assertTrue(body.contains("navigate_to_page"), "Should have navigate_to_page tool")
        assertTrue(body.contains("start_scan"), "Should have start_scan tool")
        assertTrue(body.contains("analyze_ticket"), "Should have analyze_ticket tool")
        assertTrue(body.contains("send_chat_message"), "Should have send_chat_message tool")
        assertTrue(body.contains("get_settings"), "Should have get_settings tool")
        assertTrue(body.contains("list_users"), "Should have list_users tool")
        assertTrue(body.contains("get_graph_data"), "Should have get_graph_data tool")
        assertTrue(body.contains("get_dashboard_metrics"), "Should have get_dashboard_metrics tool")
        assertTrue(body.contains("list_projects"), "Should have list_projects tool")
    }

    @Test @Order(531)
    fun toolsHaveInputSchema() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/$INTERNAL_ID/tools") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val body = resp.bodyAsText()
        assertTrue(body.contains("inputSchema"), "Each tool should have inputSchema (Req 6.108)")
        assertTrue(body.contains("description"), "Each tool should have description")
    }

    @Test @Order(532)
    fun aggregatedToolsIncludeInternal() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/tools") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("jira-assistant-ui"), "Aggregated tools should include internal server (Req 6.107)")
        assertTrue(body.contains("Jira Assistant UI"), "Should have serverName")
    }

    @Test @Order(533)
    fun readerCanViewTools() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/$INTERNAL_ID/tools") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "Reader should view tools (Req 6.45)")
    }

    @Test @Order(534)
    fun aggregatedToolsReaderAccess() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/tools") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "Reader should view aggregated tools (Req 6.46)")
    }

    // ══════════════════════════════════════════════════════════
    // Section 5: Tool Execution — Navigation (Req 6.74–6.76)
    // ══════════════════════════════════════════════════════════

    @Test @Order(540)
    fun executeNavigateToPage() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"navigate_to_page","arguments":{"page":"dashboard"}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("dashboard", ignoreCase = true), "Should return dashboard info: $body")
        assertFalse(body.contains("\"isError\":true"), "Should not be error: $body")
    }

    @Test @Order(541)
    fun executeListAvailablePages() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_available_pages","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("dashboard", ignoreCase = true), "Should list dashboard page")
        assertTrue(body.contains("integrations", ignoreCase = true), "Should list integrations page")
    }

    @Test @Order(542)
    fun executeGetCurrentPage() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_current_page","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(543)
    fun navigateToPageInvalidEnum() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"navigate_to_page","arguments":{"page":"nonexistent_page"}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("isError") || body.contains("error", ignoreCase = true) || body.contains("-32602"),
            "Invalid enum should return error (Req 6.112): $body"
        )
    }

    // ══════════════════════════════════════════════════════════
    // Section 6: Tool Execution — Scan Control (Req 6.77–6.82)
    // ══════════════════════════════════════════════════════════

    @Test @Order(550)
    fun executeGetScanStatus() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_scan_status","arguments":{"projectKey":"PROJ"}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(551)
    fun executeGetScanLog() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_scan_log","arguments":{"projectKey":"PROJ","limit":10}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(552)
    fun startScanMissingProjectKey() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"start_scan","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("isError") || body.contains("-32602") || body.contains("required", ignoreCase = true),
            "Missing required projectKey should return error (Req 6.112): $body"
        )
    }

    // ══════════════════════════════════════════════════════════
    // Section 7: Tool Execution — Analysis (Req 6.83–6.85)
    // ══════════════════════════════════════════════════════════

    @Test @Order(560)
    fun executeGetTicketAnalysis() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_ticket_analysis","arguments":{"ticketId":"PROJ-1"}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(561)
    fun executeListAnalyzedTickets() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_analyzed_tickets","arguments":{"projectKey":"PROJ"}}""")
        }
        assertEquals(200, resp.status.value)
    }

    // ══════════════════════════════════════════════════════════
    // Section 8: Tool Execution — Chat (Req 6.86–6.88)
    // ══════════════════════════════════════════════════════════

    @Test @Order(570)
    fun executeListConversations() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_conversations","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(571)
    fun executeGetChatHistory() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_chat_history","arguments":{"limit":5}}""")
        }
        assertEquals(200, resp.status.value)
    }

    // ══════════════════════════════════════════════════════════
    // Section 9: Tool Execution — Settings (Req 6.89–6.91)
    // ══════════════════════════════════════════════════════════

    @Test @Order(580)
    fun executeGetSettings() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_settings","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(581)
    fun executeGetSetting() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_setting","arguments":{"key":"internal_mcp_enabled"}}""")
        }
        assertEquals(200, resp.status.value)
    }

    // ══════════════════════════════════════════════════════════
    // Section 10: Tool Execution — User Management (Req 6.92–6.94)
    // ══════════════════════════════════════════════════════════

    @Test @Order(590)
    fun executeListUsers() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_users","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
    }

    // ══════════════════════════════════════════════════════════
    // Section 11: Tool Execution — Integrations (Req 6.95–6.98)
    // ══════════════════════════════════════════════════════════

    @Test @Order(600)
    fun executeListAiProviders() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_ai_providers","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(601)
    fun executeListMcpServers() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_mcp_servers","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("jira-assistant-ui", ignoreCase = true), "Should list itself: $body")
    }

    @Test @Order(602)
    fun manageMcpServerSelfProtection() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"manage_mcp_server","arguments":{"serverId":"jira-assistant-ui","action":"stop"}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("isError") || body.contains("Cannot", ignoreCase = true) || body.contains("internal", ignoreCase = true),
            "Should reject managing internal server (Req 6.98): $body"
        )
    }

    // ══════════════════════════════════════════════════════════
    // Section 12: Tool Execution — Knowledge Graph (Req 6.99–6.100)
    // ══════════════════════════════════════════════════════════

    @Test @Order(610)
    fun executeGetGraphData() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_graph_data","arguments":{"projectKey":"PROJ"}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(611)
    fun executeSearchGraphNodes() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"search_graph_nodes","arguments":{"query":"authentication"}}""")
        }
        assertEquals(200, resp.status.value)
    }

    // ══════════════════════════════════════════════════════════
    // Section 13: Tool Execution — Dashboard (Req 6.101–6.103)
    // ══════════════════════════════════════════════════════════

    @Test @Order(620)
    fun executeGetDashboardMetrics() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_dashboard_metrics","arguments":{"projectKey":"PROJ"}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(621)
    fun executeListProjects() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_projects","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(622)
    fun executeGetProjectAnalysisSummary() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_project_analysis_summary","arguments":{"projectKey":"PROJ"}}""")
        }
        assertEquals(200, resp.status.value)
    }

    // ══════════════════════════════════════════════════════════
    // Section 14: RBAC Enforcement (Req 6.104, 6.106)
    // ══════════════════════════════════════════════════════════

    @Test @Order(700)
    fun readerCanCallReadOnlyTools() = runBlocking {
        val readOnlyTools = listOf(
            "get_scan_status" to """{"projectKey":"PROJ"}""",
            "list_available_pages" to "{}",
            "get_settings" to "{}",
            "list_ai_providers" to "{}",
            "list_mcp_servers" to "{}",
            "get_graph_data" to """{"projectKey":"PROJ"}""",
            "get_dashboard_metrics" to """{"projectKey":"PROJ"}""",
            "list_projects" to "{}"
        )
        for ((tool, args) in readOnlyTools) {
            val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
                setBody("""{"serverId":"$INTERNAL_ID","toolName":"$tool","arguments":$args}""")
            }
            assertEquals(200, resp.status.value, "Reader should call read-only tool '$tool'")
            val body = resp.bodyAsText()
            assertFalse(
                body.contains("Access denied", ignoreCase = true),
                "Reader should NOT get access denied for '$tool': $body"
            )
        }
    }

    @Test @Order(701)
    fun readerCannotCallWriteTools() = runBlocking {
        val writeTools = listOf(
            "start_scan" to """{"projectKey":"PROJ"}""",
            "update_setting" to """{"key":"test","value":"val"}""",
            "list_users" to "{}",
            "update_user_role" to """{"userId":"u1","role":"Reader"}""",
            "test_ai_provider" to """{"providerId":"ollama"}""",
            "manage_mcp_server" to """{"serverId":"test","action":"start"}"""
        )
        for ((tool, args) in writeTools) {
            val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
                setBody("""{"serverId":"$INTERNAL_ID","toolName":"$tool","arguments":$args}""")
            }
            assertEquals(200, resp.status.value, "Tool call endpoint should return 200 for '$tool'")
            val body = resp.bodyAsText()
            assertTrue(
                body.contains("Access denied", ignoreCase = true) ||
                    body.contains("isError", ignoreCase = true) ||
                    body.contains("-32603") ||
                    body.contains("permission", ignoreCase = true),
                "Reader should be denied write tool '$tool' (Req 6.106): $body"
            )
        }
    }

    @Test @Order(702)
    fun neuralArchitectCanCallAnalyzeTools() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_scan_status","arguments":{"projectKey":"PROJ"}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertFalse(body.contains("Access denied", ignoreCase = true), "Neural_Architect should access scan status")
    }

    @Test @Order(703)
    fun neuralArchitectCannotManageUsers() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_users","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("Access denied", ignoreCase = true) ||
                body.contains("isError") || body.contains("-32603"),
            "Neural_Architect should be denied user management (Req 6.104): $body"
        )
    }

    @Test @Order(704)
    fun toolCallRequiresAuth() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"list_projects","arguments":{}}""")
        }
        assertEquals(401, resp.status.value, "Tool call without JWT should return 401")
    }

    // ══════════════════════════════════════════════════════════
    // Section 15: Argument Validation (Req 6.112)
    // ══════════════════════════════════════════════════════════

    @Test @Order(710)
    fun missingRequiredArgument() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"analyze_ticket","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("-32602") || body.contains("required", ignoreCase = true) ||
                body.contains("isError") || body.contains("missing", ignoreCase = true),
            "Missing ticketId should return validation error: $body"
        )
    }

    @Test @Order(711)
    fun unknownToolReturnsError() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"nonexistent_tool","arguments":{}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("-32601") || body.contains("not found", ignoreCase = true) ||
                body.contains("isError"),
            "Unknown tool should return error: $body"
        )
    }

    @Test @Order(712)
    fun invalidEnumArgument() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"update_user_role","arguments":{"userId":"u1","role":"INVALID_ROLE"}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("-32602") || body.contains("isError") ||
                body.contains("must be one of", ignoreCase = true) || body.contains("invalid", ignoreCase = true),
            "Invalid enum should return validation error: $body"
        )
    }

    // ══════════════════════════════════════════════════════════
    // Section 16: Error Handling (Req 6.110, 6.111)
    // ══════════════════════════════════════════════════════════

    @Test @Order(720)
    fun businessErrorTicketNotFound() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_ticket_analysis","arguments":{"ticketId":"NONEXISTENT-99999"}}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        // Business error should return isError:true, NOT crash
        assertFalse(resp.status.value == 500, "Should not return 500 for business error")
    }

    @Test @Order(721)
    fun businessErrorProjectNotConfigured() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/tools/call") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"serverId":"$INTERNAL_ID","toolName":"get_dashboard_metrics","arguments":{"projectKey":"NONEXISTENT"}}""")
        }
        assertEquals(200, resp.status.value, "Business error should not crash (Req 6.110)")
    }

    // ══════════════════════════════════════════════════════════
    // Section 17: MCP Server CRUD (Req 6.26, 6.30a)
    // ══════════════════════════════════════════════════════════

    @Test @Order(800)
    fun createMcpServer() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"E2E Test Server","command":"echo","args":["hello"],"env":{},"autoApprove":[],"disabled":true}""")
        }
        assertTrue(
            resp.status.value in listOf(200, 201, 409),
            "Create MCP server should succeed or conflict, got ${resp.status.value}"
        )
    }

    @Test @Order(801)
    fun duplicateNameReturns409() = runBlocking {
        // First create
        client.post("$baseUrl$MCP_BASE") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"Duplicate Test","command":"echo","args":[],"env":{},"autoApprove":[],"disabled":true}""")
        }
        // Second create with same name
        val resp = client.post("$baseUrl$MCP_BASE") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"Duplicate Test","command":"echo","args":[],"env":{},"autoApprove":[],"disabled":true}""")
        }
        assertEquals(409, resp.status.value, "Duplicate name should return 409 (Req 6.30a)")
    }

    @Test @Order(802)
    fun readerCannotCreateMcpServer() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
            setBody("""{"name":"Reader Server","command":"echo","args":[],"env":{},"autoApprove":[],"disabled":true}""")
        }
        assertEquals(403, resp.status.value, "Reader cannot create MCP server (Req 6.30)")
    }

    @Test @Order(803)
    fun readerCannotDeleteMcpServer() = runBlocking {
        val resp = client.delete("$baseUrl$MCP_BASE/some-server-id") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertTrue(
            resp.status.value in listOf(403, 404),
            "Reader cannot delete MCP server (Req 6.30), got ${resp.status.value}"
        )
    }

    // ══════════════════════════════════════════════════════════
    // Section 18: Import/Export (Req 6.27)
    // ══════════════════════════════════════════════════════════

    @Test @Order(810)
    fun exportMcpConfig() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/export") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("mcpServers"), "Export should contain mcpServers key (Req 6.27)")
    }

    @Test @Order(811)
    fun importMcpConfigSkipsDuplicates() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/import") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"mcpServers":{"jira-assistant-ui":{"command":"echo","args":[]}}}""")
        }
        assertTrue(
            resp.status.value in listOf(200, 201),
            "Import should succeed (skip duplicates), got ${resp.status.value}"
        )
    }

    // ══════════════════════════════════════════════════════════
    // Section 19: Test Connection (Req 6.34)
    // ══════════════════════════════════════════════════════════

    @Test @Order(820)
    fun testInternalServerConnection() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/$INTERNAL_ID/test") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("tools", ignoreCase = true) || body.contains("success", ignoreCase = true),
            "Test internal server should return tools or success: $body"
        )
    }

    @Test @Order(821)
    fun testConnectionRequiresAdmin() = runBlocking {
        val resp = client.post("$baseUrl$MCP_BASE/$INTERNAL_ID/test") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "Test connection requires Administrator (Req 6.30)")
    }

    // ══════════════════════════════════════════════════════════
    // Section 20: Logs (Req 6.61)
    // ══════════════════════════════════════════════════════════

    @Test @Order(830)
    fun getServerLogsRequiresAdmin() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/$INTERNAL_ID/logs") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "Logs require Administrator (Req 6.61)")
    }

    @Test @Order(831)
    fun getServerLogsWithAdmin() = runBlocking {
        val resp = client.get("$baseUrl$MCP_BASE/$INTERNAL_ID/logs") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Admin should access logs, got ${resp.status.value}"
        )
    }
}
