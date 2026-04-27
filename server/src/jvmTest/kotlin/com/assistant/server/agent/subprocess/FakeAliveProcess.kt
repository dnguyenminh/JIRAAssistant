package com.assistant.server.agent.subprocess

import java.io.InputStream
import java.io.OutputStream

/**
 * A fake [Process] that always reports as alive.
 *
 * Used in property tests to verify subprocess singleton reuse
 * and mutex behavior without spawning real OS processes.
 */
class FakeAliveProcess : Process() {

    @Volatile
    private var alive = true

    override fun getOutputStream(): OutputStream =
        OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream =
        InputStream.nullInputStream()

    override fun getErrorStream(): InputStream =
        InputStream.nullInputStream()

    override fun waitFor(): Int = 0

    override fun exitValue(): Int =
        if (alive) throw IllegalThreadStateException("running")
        else 0

    override fun destroy() {
        alive = false
    }

    override fun isAlive(): Boolean = alive
}
