package com.assistant.rbac

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileBasedAuditLogStoreTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun entry(ts: String = "2024-01-01T00:00:00Z") = AuditLogEntry(
        timestamp = ts,
        actorId = "admin1",
        targetUserId = "u2",
        action = "ROLE_CHANGE",
        oldValue = "READER",
        newValue = "ADMINISTRATOR",
        tag = "IAM_SYNC"
    )

    @Test
    fun `append and getAll returns entries`() = runTest {
        val store = FileBasedAuditLogStore(tempDir.root.absolutePath)
        val e = entry()
        store.append(e)
        assertEquals(listOf(e), store.getAll())
    }

    @Test
    fun `entries persist across instances`() = runTest {
        val dir = tempDir.root.absolutePath
        val store1 = FileBasedAuditLogStore(dir)
        store1.append(entry("2024-01-01T10:00:00Z"))
        store1.append(entry("2024-01-01T11:00:00Z"))

        val store2 = FileBasedAuditLogStore(dir)
        assertEquals(2, store2.getAll().size)
        assertEquals("2024-01-01T10:00:00Z", store2.getAll()[0].timestamp)
        assertEquals("2024-01-01T11:00:00Z", store2.getAll()[1].timestamp)
    }

    @Test
    fun `getRecent returns sorted by timestamp descending`() = runTest {
        val store = FileBasedAuditLogStore(tempDir.root.absolutePath)
        store.append(entry("2024-01-01T08:00:00Z"))
        store.append(entry("2024-01-01T12:00:00Z"))
        store.append(entry("2024-01-01T10:00:00Z"))

        val recent = store.getRecent(2)
        assertEquals(2, recent.size)
        assertEquals("2024-01-01T12:00:00Z", recent[0].timestamp)
        assertEquals("2024-01-01T10:00:00Z", recent[1].timestamp)
    }

    @Test
    fun `handles missing file gracefully`() = runTest {
        val store = FileBasedAuditLogStore(tempDir.newFolder("empty").absolutePath)
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `handles corrupt file gracefully`() = runTest {
        val dir = tempDir.newFolder("corrupt")
        java.io.File(dir, "audit-log.json").writeText("not valid json!!!")
        val store = FileBasedAuditLogStore(dir.absolutePath)
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `handles empty file gracefully`() = runTest {
        val dir = tempDir.newFolder("emptyfile")
        java.io.File(dir, "audit-log.json").writeText("")
        val store = FileBasedAuditLogStore(dir.absolutePath)
        assertTrue(store.getAll().isEmpty())
    }
}
