package com.assistant.server.agent.ba

import com.assistant.agent.config.AgentConfig
import com.assistant.agent.registry.AgentNotFoundException
import com.assistant.agent.registry.AgentRegistry
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.server.agent.di.agentModule
import io.kotest.matchers.collections.shouldContain

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Koin integration tests verifying BA agent registration at
 * startup and preservation of other module beans.
 *
 * **Validates: Requirements 2.1, 2.2, 3.4**
 */
@Tag("agent-document-generation")
class BAAgentModuleRegistrationTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `BA agent is registered after module startup`() {
        val app = startKoin {
            modules(agentModule, baAgentModule)
        }
        val registry = app.koin.get<AgentRegistry>()

        registry.listAgentTypes() shouldContain "ba-document"
    }

    @Test
    fun `getAgent does not throw AgentNotFoundException`() {
        val app = startKoin {
            modules(agentModule, baAgentModule)
        }
        val registry = app.koin.get<AgentRegistry>()

        try {
            registry.getAgent("ba-document", AgentConfig())
        } catch (_: AgentNotFoundException) {
            throw AssertionError(
                "ba-document should be registered but was not"
            )
        } catch (_: Exception) {
            // Factory-internal resolution errors are expected
            // in a minimal test Koin graph — the important
            // thing is that the factory IS registered.
        }
    }

    @Test
    fun `BASubprocessOrchestrator is registered in module`() {
        val app = startKoin {
            modules(agentModule, baAgentModule)
        }
        // Verify the singleton definition exists. Actual
        // resolution may fail in a minimal test Koin graph
        // because SubprocessManager/SubprocessProxy are
        // registered in agentModule but their constructor
        // deps (SettingsRepository) are not available here.
        try {
            app.koin.get<BASubprocessOrchestrator>()
        } catch (_: org.koin.core.error.InstanceCreationException) {
            // Expected — the definition IS registered but
            // transitive deps are missing in this test graph.
        }
    }
}
