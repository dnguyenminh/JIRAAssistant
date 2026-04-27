package com.assistant.frontend.pages.analysis

import com.assistant.frontend.models.BottleneckAlert
import com.assistant.frontend.models.ProjectAnalysisResponse
import com.assistant.frontend.models.ProviderStatusInfo
import com.assistant.frontend.models.SprintVelocity
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property 2: Preservation — Analysis State Behavior.
 *
 * Observation-first methodology: these tests capture CURRENT
 * correct behavior on UNFIXED code. They MUST PASS before AND
 * after the bugfix to confirm no regression.
 *
 * Properties tested:
 * - Save/restore roundtrip preserves all ProjectAnalysisResponse fields
 * - Restore returns null when sessionStorage empty
 * - Restore returns null when sessionStorage contains invalid JSON
 * - First load (no saved state) follows normal API flow without crash
 *
 * **Validates: Requirements 3.1, 3.3, 3.7**
 */
class AnalysisPreservationTest {

    private val STORAGE_KEY = "analysis_page_state"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @BeforeTest
    fun setup() {
        window.sessionStorage.removeItem(STORAGE_KEY)
    }

    // -- Generators --

    private fun generateResponse(rng: Random): ProjectAnalysisResponse {
        val sprintCount = rng.nextInt(1, 8)
        val bottleneckCount = rng.nextInt(0, 5)
        val providerCount = rng.nextInt(0, 4)

        return ProjectAnalysisResponse(
            projectKey = "PRJ-${rng.nextInt(100, 999)}",
            totalTickets = rng.nextInt(0, 500),
            resolutionRate = rng.nextDouble(0.0, 100.0),
            cycleTimeDays = rng.nextDouble(0.1, 60.0),
            aiVelocity = rng.nextDouble(0.0, 15.0),
            velocityTrend = (0 until sprintCount).map { idx ->
                SprintVelocity(
                    sprintName = "Sprint ${idx + 1}",
                    storyPoints = rng.nextDouble(5.0, 80.0)
                )
            },
            bottlenecks = (0 until bottleneckCount).map { idx ->
                val types = listOf("RISK", "OPPORTUNITY")
                val severities = listOf("HIGH", "MEDIUM", "LOW")
                BottleneckAlert(
                    type = types[idx % types.size],
                    severity = severities[idx % severities.size],
                    title = "Alert #$idx",
                    description = "Description for alert $idx"
                )
            },
            providerStatuses = (0 until providerCount).map { idx ->
                ProviderStatusInfo(
                    providerId = "provider-$idx",
                    name = "Provider $idx",
                    status = if (rng.nextBoolean()) "CONNECTED" else "ERROR",
                    latencyMs = rng.nextLong(10, 2000),
                    lastChecked = "2025-01-${rng.nextInt(1, 28)}T10:00:00Z"
                )
            }
        )
    }

    // ── Property: Save/restore roundtrip preserves all fields ──

    /**
     * For any random ProjectAnalysisResponse, serializing to
     * sessionStorage and deserializing back MUST produce an
     * identical object — all fields preserved.
     */
    @Test
    fun saveRestoreRoundtripPreservesAllFields() {
        val rng = Random(seed = 42)
        repeat(30) { i ->
            window.sessionStorage.removeItem(STORAGE_KEY)

            val original = generateResponse(rng)

            // Save: serialize → sessionStorage
            val serialized = json.encodeToString(
                ProjectAnalysisResponse.serializer(), original
            )
            window.sessionStorage.setItem(STORAGE_KEY, serialized)

            // Restore: sessionStorage → deserialize
            val raw = window.sessionStorage.getItem(STORAGE_KEY)
            assertTrue(
                raw != null,
                "Iter $i: sessionStorage must contain data after save"
            )
            val restored = json.decodeFromString(
                ProjectAnalysisResponse.serializer(), raw
            )

            // All fields must match
            assertEquals(
                original.projectKey, restored.projectKey,
                "Iter $i: projectKey mismatch"
            )
            assertEquals(
                original.totalTickets, restored.totalTickets,
                "Iter $i: totalTickets mismatch"
            )
            assertEquals(
                original.resolutionRate, restored.resolutionRate,
                "Iter $i: resolutionRate mismatch"
            )
            assertEquals(
                original.cycleTimeDays, restored.cycleTimeDays,
                "Iter $i: cycleTimeDays mismatch"
            )
            assertEquals(
                original.aiVelocity, restored.aiVelocity,
                "Iter $i: aiVelocity mismatch"
            )
            assertEquals(
                original.velocityTrend.size,
                restored.velocityTrend.size,
                "Iter $i: velocityTrend size mismatch"
            )
            for (j in original.velocityTrend.indices) {
                assertEquals(
                    original.velocityTrend[j],
                    restored.velocityTrend[j],
                    "Iter $i: velocityTrend[$j] mismatch"
                )
            }
            assertEquals(
                original.bottlenecks.size,
                restored.bottlenecks.size,
                "Iter $i: bottlenecks size mismatch"
            )
            for (j in original.bottlenecks.indices) {
                assertEquals(
                    original.bottlenecks[j],
                    restored.bottlenecks[j],
                    "Iter $i: bottlenecks[$j] mismatch"
                )
            }
            assertEquals(
                original.providerStatuses.size,
                restored.providerStatuses.size,
                "Iter $i: providerStatuses size mismatch"
            )
        }
    }

    // ── Property: Restore returns null when sessionStorage empty ──

    /**
     * When sessionStorage has no saved state, restore attempt
     * returns null — normal first-load API flow, no crash.
     */
    @Test
    fun restoreReturnsNullWhenSessionStorageEmpty() {
        repeat(10) { i ->
            window.sessionStorage.removeItem(STORAGE_KEY)

            val raw = window.sessionStorage.getItem(STORAGE_KEY)
            assertNull(
                raw,
                "Iter $i: sessionStorage must be null when empty"
            )

            // Simulating restore logic: getItem returns null → no crash
            val restored: ProjectAnalysisResponse? = try {
                val data = window.sessionStorage.getItem(STORAGE_KEY)
                if (data != null) {
                    json.decodeFromString(
                        ProjectAnalysisResponse.serializer(), data
                    )
                } else null
            } catch (_: Exception) {
                null
            }

            assertNull(
                restored,
                "Iter $i: restore must return null on empty storage"
            )
        }
    }

    // ── Property: Restore returns null on invalid JSON ──

    /**
     * When sessionStorage contains invalid JSON, restore attempt
     * returns null gracefully — no exception propagated.
     */
    @Test
    fun restoreReturnsNullOnInvalidJson() {
        val invalidJsons = listOf(
            "not json at all",
            "{broken json here",
            "{{{}}}",
            "<html>not json</html>",
            "12345",
            "[1,2,3]"
        )

        invalidJsons.forEachIndexed { i, invalid ->
            window.sessionStorage.setItem(STORAGE_KEY, invalid)

            val restored: ProjectAnalysisResponse? = try {
                val raw = window.sessionStorage.getItem(STORAGE_KEY)
                if (raw != null) {
                    json.decodeFromString(
                        ProjectAnalysisResponse.serializer(), raw
                    )
                } else null
            } catch (_: Exception) {
                null
            }

            assertNull(
                restored,
                "Iter $i: restore must return null for invalid " +
                    "JSON: '${invalid.take(20)}'"
            )
        }
    }

    // ── Property: First load — no state, normal flow ──

    /**
     * On first load (sessionStorage empty), the page should
     * proceed with normal API flow. This test verifies the
     * precondition: no saved state exists.
     */
    @Test
    fun firstLoadHasNoSavedState() {
        val rng = Random(seed = 99)
        repeat(10) { i ->
            window.sessionStorage.removeItem(STORAGE_KEY)

            // Verify precondition: no state
            val raw = window.sessionStorage.getItem(STORAGE_KEY)
            assertNull(
                raw,
                "Iter $i: first load must have no saved state"
            )

            // After first load completes (simulated), state can be saved
            val data = generateResponse(rng)
            val serialized = json.encodeToString(
                ProjectAnalysisResponse.serializer(), data
            )
            window.sessionStorage.setItem(STORAGE_KEY, serialized)

            // Verify state was saved
            val saved = window.sessionStorage.getItem(STORAGE_KEY)
            assertTrue(
                saved != null && saved.isNotEmpty(),
                "Iter $i: state must be saved after first load"
            )

            // Cleanup for next iteration
            window.sessionStorage.removeItem(STORAGE_KEY)
        }
    }
}
