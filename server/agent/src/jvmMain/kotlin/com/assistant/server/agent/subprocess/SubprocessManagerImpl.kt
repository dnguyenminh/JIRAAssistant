package com.assistant.server.agent.subprocess

import com.assistant.agent.subprocess.SubprocessConfig
import com.assistant.agent.subprocess.SubprocessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the lifecycle of agent CLI subprocesses.
 *
 * Each agent type has at most one running subprocess (singleton pattern).
 * Subprocesses communicate via stdin/stdout using [MessageProtocol] framing.
 * Crash detection triggers auto-restart on the next [sendCommand] call.
 *
 * Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7
 *
 * @property configs mapping of agent type to subprocess configuration
 */
class SubprocessManagerImpl(
    configs: Map<String, SubprocessConfig> = emptyMap()
) : SubprocessManager {

    internal val configs = ConcurrentHashMap(configs)
    internal val subprocesses = ConcurrentHashMap<String, ManagedSubprocess>()
    internal val logger = LoggerFactory.getLogger(SubprocessManagerImpl::class.java)

    /** Register or update a subprocess config at runtime. */
    fun registerConfig(agentType: String, config: SubprocessConfig) {
        configs[agentType] = config
    }

    /**
     * Sends a command to the agent subprocess and returns streaming response chunks.
     *
     * Spawns a new subprocess if none exists or the existing one has crashed.
     * Acquires the [ManagedSubprocess.commandMutex] to ensure sequential execution.
     *
     * Requirements: 13.1, 13.2, 13.3, 13.4, 13.5
     */
    override suspend fun sendCommand(agentType: String, command: String): Flow<String> {
        val managed = getOrSpawnSubprocess(agentType)
        val config = configs[agentType]
        val isRealCli = config?.isRealCli ?: false
        val idleTimeoutMs = if (isRealCli) {
            config.unresponsiveTimeoutMs
        } else {
            Long.MAX_VALUE
        }
        if (isRealCli) {
            logger.info("sendCommand '{}': interruptible read mode (idle={}ms)",
                agentType, idleTimeoutMs)
        }
        return flow {
            managed.commandMutex.lock()
            try {
                managed.touch()
                writeCommandToStdin(managed, command, isRealCli)
                emitStdoutUntilDelimiter(managed, this, idleTimeoutMs)
            } finally {
                managed.commandMutex.unlock()
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Checks whether a subprocess is currently running for the given agent type.
     */
    override suspend fun isRunning(agentType: String): Boolean {
        return subprocesses[agentType]?.isAlive() == true
    }

    /**
     * Terminates the subprocess for the given agent type with graceful shutdown.
     *
     * Sends SIGTERM, waits up to the configured shutdown timeout, then force-kills.
     * Requirements: 13.7
     */
    override suspend fun terminate(agentType: String) {
        val managed = subprocesses.remove(agentType) ?: return
        terminateProcess(managed)
    }

    /**
     * Terminates all running agent subprocesses.
     *
     * Called during application shutdown. Requirements: 13.7
     */
    override suspend fun terminateAll() {
        val types = subprocesses.keys.toList()
        logger.info("Terminating {} agent subprocesses", types.size)
        types.forEach { terminate(it) }
    }

    /**
     * Returns the list of agent types with active subprocesses.
     */
    override fun getRunningAgentTypes(): List<String> {
        return subprocesses.entries
            .filter { it.value.isAlive() }
            .map { it.key }
    }

    /**
     * Returns or spawns a subprocess for the given agent type.
     *
     * If the existing subprocess has crashed, spawns a new one.
     * Requirements: 13.1, 13.4
     */
    internal suspend fun getOrSpawnSubprocess(agentType: String): ManagedSubprocess {
        val existing = subprocesses[agentType]
        if (existing != null && existing.isAlive()) return existing

        if (existing != null) {
            logger.warn("Subprocess for '{}' has crashed, restarting (count={})", agentType, existing.restartCount + 1)
            subprocesses.remove(agentType)
        }

        return spawnSubprocess(agentType, existing?.restartCount ?: 0)
    }

    /**
     * Gracefully terminates a managed subprocess.
     *
     * SIGTERM → configured shutdown timeout → force-kill.
     * Requirements: 13.7
     */
    internal suspend fun terminateProcess(managed: ManagedSubprocess) {
        withContext(Dispatchers.IO) {
            val config = configs[managed.agentType]
            val timeoutMs = config?.shutdownTimeoutMs ?: 5_000L
            managed.process.destroy()
            val exited = managed.process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!exited) {
                logger.warn("Force-killing subprocess for '{}'", managed.agentType)
                managed.process.destroyForcibly()
            }
            logger.info("Terminated subprocess for '{}'", managed.agentType)
        }
    }
}
