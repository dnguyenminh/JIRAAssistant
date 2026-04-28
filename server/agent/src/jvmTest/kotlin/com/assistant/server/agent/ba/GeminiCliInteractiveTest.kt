package com.assistant.server.agent.ba

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Tag("ba-agent-integration")
@Tag("sequential")
class GeminiCliInteractiveTest {

    @Test
    @Timeout(300, unit = TimeUnit.SECONDS)
    fun `interactive gemini produces BRD via tool call loop`() = runBlocking {
        val model = "gemini-2.5-flash"
        val ticketId = "ICL2-15"

        val process = spawnGemini(model)

        try {
            withContext(Dispatchers.IO) {
                val inputWriter = process.outputStream.bufferedWriter()
                val outputReader = process.inputStream.bufferedReader()

                // 1. Send the full Master Prompt via stdin first.
                val masterPrompt = getFullMasterPrompt(ticketId)
                sendToStdin(inputWriter, masterPrompt)

                // 2. Loop to handle the real-time interaction.
                val finalBRD = StringBuilder()

                while (currentCoroutineContext().isActive) {
                    val line = outputReader.readLine() ?: break // End of stream
                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty()) continue

                    println("<<< $line")

                    if (trimmedLine.contains("\"toolCall\"")) {
                        val jsonStart = trimmedLine.indexOf("{\"toolCall\"")
                        if (jsonStart != -1) {
                            val jsonPart = trimmedLine.substring(jsonStart)
                            val result = executeToolCall(jsonPart, ticketId)
                            sendToStdin(inputWriter, result)
                        }
                    } else if (trimmedLine.contains("---END---")) {
                        break
                    } else {
                        finalBRD.appendLine(line)
                    }
                }
                println("\n=== RESULTING BRD ===\n$finalBRD")
                kotlin.test.assertTrue(
                        finalBRD.contains("##"),
                        "BRD should contain markdown headings"
                )
            }
        } finally {
            process.destroyForcibly()
            println("[Test] Gemini CLI terminated.")
        }
    }

    private fun spawnGemini(model: String): Process {
        // New strategy: Execute the .ps1 script using PowerShell (pwsh.exe)
        // This leverages the script's own input piping logic (`$input | & ...`)
        // which might be more robust against I/O buffering deadlocks.
        val powershellPath = "pwsh.exe"
        val geminiPs1ScriptPath = "C:\\Users\\ASUS\\scoop\\apps\\nodejs24\\current\\bin\\gemini.ps1"

        val pb = ProcessBuilder(powershellPath, "-File", geminiPs1ScriptPath, "-m", model)
        pb.redirectErrorStream(true)
        pb.directory(File("."))

        return pb.start()
    }

    private suspend fun sendToStdin(writer: java.io.BufferedWriter, text: String) {
        withContext(Dispatchers.IO) {
            writer.write(text)
            writer.newLine()
            writer.flush()
        }
    }

    private fun getFullMasterPrompt(ticketId: String): String =
            """
## ROLE INSTRUCTION
You are a Senior Business Analyst (15+ years experience) at FE CREDIT, specializing in FinTech, BPM (Flowable), and Enterprise Integration. You follow the Carleton University ITS BRD template.

## STRATEGY & REASONING (X10THINK)
1. **Explore & Fetch:** Use `knowledge_base_get_ticket_info` for root ticket $ticketId. Identify all linked tickets.
2. **Deep Dive:** Use `knowledge_base_search_relationships` to find dependencies.
3. **Gap Analysis:** If data is missing, fallback to `jira_get_issue` or infer based on FE CREDIT's tech stack (Java 21, Spring Boot, Flowable, OpenShift).

## AVAILABLE TOOLS (JSON ONLY)
Format: `{"toolCall":{"name":"<toolName>","arguments":{...}}}`

1. **knowledge_base_get_ticket_info** - Get full ticket info (summary, description, comments, linked tickets metadata)
2. **knowledge_base_search_relationships** - Find ticket dependencies from KB
3. **jira_get_issue** - Get ticket from Jira (ONLY if KB data missing)
4. **jira_search** - Search Jira via JQL (ONLY if KB data missing)

## TOOL RESULT FORMAT
I will provide results in this format:
`{"toolResult":{"name":"<toolName>","success":true/false,"data":"<JSON_STRING>","error":"<msg>"}}`

## TEMPLATE STRUCTURE
1. Revision History
2. Project Overview
3. Common Project Acronyms
4. Existing Processes
5. Project Requirements (5.1 Flow, 5.2 Functional PREQ-NNN, 5.3 NFR, 5.4 Data)
6. Known Issues/Assumptions/Risks/Dependencies
7. Sign Off & Appendix

## OUTPUT CONSTRAINTS
- Cite sources as [Source: TICKET-ID].
- Mark inferences with [INFERRED].
- End with ONLY the delimiter: ---END---.

## TASK
Collect data for $ticketId and write complete BRD. Start with knowledge_base_get_ticket_info.
""".trimIndent()

    private suspend fun executeToolCall(json: String, ticketId: String): String {
        // Parse tool name from JSON
        val nameMatch = """"name"\s*:\s*"([^"]+)"""".toRegex().find(json)
        val toolName = nameMatch?.groupValues?.get(1) ?: "unknown"

        return when (toolName) {
            "knowledge_base_get_ticket_info" -> {
                """{"toolResult":{"name":"knowledge_base_get_ticket_info","success":true,"data":"{\"ticketId\":\"$ticketId\",\"summary\":\"Implement AI-powered document generation for BA workflow\",\"description\":\"As a BA, I need an automated system that generates BRD documents from Jira ticket analysis. The system should collect ticket data, analyze requirements, and produce structured BRD documents.\",\"status\":\"In Progress\",\"priority\":\"High\",\"assignee\":\"duc.nguyen\",\"reporter\":\"pm.lead\",\"created\":\"2026-03-15\",\"comments\":[{\"author\":\"pm.lead\",\"body\":\"Please include stakeholder analysis and risk assessment\"},{\"author\":\"tech.lead\",\"body\":\"Consider integration with existing KB system\"}],\"linkedTickets\":[\"ICL2-10\",\"ICL2-12\",\"ICL2-14\"],\"attachments\":[{\"filename\":\"requirements-draft.pdf\",\"size\":\"245KB\"},{\"filename\":\"architecture-diagram.png\",\"size\":\"180KB\"}]}"}}"""
            }
            "knowledge_base_search_relationships" -> {
                """{"toolResult":{"name":"knowledge_base_search_relationships","success":true,"data":"{\"ticketId\":\"$ticketId\",\"relationships\":[{\"type\":\"blocks\",\"target\":\"ICL2-10\",\"summary\":\"Setup KB infrastructure\"},{\"type\":\"is blocked by\",\"target\":\"ICL2-12\",\"summary\":\"Jira API integration\"},{\"type\":\"relates to\",\"target\":\"ICL2-14\",\"summary\":\"AI prompt engineering for document generation\"}]}"}}"""
            }
            "jira_get_issue" -> {
                """{"toolResult":{"name":"jira_get_issue","success":true,"data":"{\"key\":\"$ticketId\",\"summary\":\"AI-powered BRD generation\",\"description\":\"Full ticket description from Jira\",\"status\":\"In Progress\"}"}}"""
            }
            "jira_search" -> {
                """{"toolResult":{"name":"jira_search","success":true,"data":"{\"total\":3,\"issues\":[{\"key\":\"ICL2-10\"},{\"key\":\"ICL2-12\"},{\"key\":\"ICL2-14\"}]}"}}"""
            }
            else -> {
                """{"toolResult":{"name":"$toolName","success":false,"error":"Unknown tool: $toolName"}}"""
            }
        }
    }
}