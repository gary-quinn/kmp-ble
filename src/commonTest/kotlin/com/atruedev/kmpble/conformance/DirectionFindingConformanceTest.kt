package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.direction.AntennaConfig
import com.atruedev.kmpble.direction.DirectionFindingMode
import com.atruedev.kmpble.direction.DirectionFindingParameters
import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Direction Finding conformance tests.
 *
 * Verifies the Direction Finding API contract across KMP platforms:
 * parameter validation, result type handling, edge cases, and platform
 * behavior differences (iOS vs Android).
 *
 * Platform-specific runners (IosDirectionFindingConformanceTest,
 * AndroidDirectionFindingConformanceTest) extend this class and run all
 * inherited tests with the platform's direction finding implementation.
 */
public abstract class DirectionFindingConformanceTest {
    /** Factory for peripheral test doubles. Override to inject platform behavior. */
    protected open fun buildPeripheral(): FakePeripheral = FakePeripheral {}

    // --- Parameter validation: cteLength ---

    @Test
    fun `DirectionFindingParameters rejects cteLength below minimum`() {
        assertFailsWith<IllegalArgumentException> {
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 1,
                cteCount = 1,
                antennaConfig = AntennaConfig(listOf(1), 1),
            )
        }
    }

    @Test
    fun `DirectionFindingParameters rejects cteLength above maximum`() {
        assertFailsWith<IllegalArgumentException> {
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 21,
                cteCount = 1,
                antennaConfig = AntennaConfig(listOf(1), 1),
            )
        }
    }

    @Test
    fun `DirectionFindingParameters accepts cteLength boundary values`() {
        // Minimum valid
        DirectionFindingParameters(
            mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
            cteLength = 2,
            cteCount = 1,
            antennaConfig = AntennaConfig(listOf(1), 1),
        )
        // Maximum valid
        DirectionFindingParameters(
            mode = DirectionFindingMode.ANGLES_OF_DEPARTURE,
            cteLength = 20,
            cteCount = 16,
            antennaConfig = AntennaConfig(listOf(1, 2), 2),
        )
    }

    // --- Parameter validation: cteCount ---

    @Test
    fun `DirectionFindingParameters rejects cteCount below minimum`() {
        assertFailsWith<IllegalArgumentException> {
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 2,
                cteCount = 0,
                antennaConfig = AntennaConfig(listOf(1), 1),
            )
        }
    }

    @Test
    fun `DirectionFindingParameters rejects cteCount above maximum`() {
        assertFailsWith<IllegalArgumentException> {
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 2,
                cteCount = 17,
                antennaConfig = AntennaConfig(listOf(1), 1),
            )
        }
    }

    @Test
    fun `DirectionFindingParameters accepts cteCount boundary values`() {
        // Minimum valid
        DirectionFindingParameters(
            mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
            cteLength = 2,
            cteCount = 1,
            antennaConfig = AntennaConfig(listOf(1), 1),
        )
        // Maximum valid
        DirectionFindingParameters(
            mode = DirectionFindingMode.ANGLES_OF_DEPARTURE,
            cteLength = 20,
            cteCount = 16,
            antennaConfig = AntennaConfig(listOf(1, 2, 1, 2), 2),
        )
    }

    // --- AntennaConfig validation: empty pattern ---

    @Test
    fun `AntennaConfig rejects empty switch pattern`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(emptyList(), 1)
        }
    }

    // --- AntennaConfig validation: zero antennas ---

    @Test
    fun `AntennaConfig rejects zero numberOfAntennas`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(listOf(1), 0)
        }
    }

    // --- AntennaConfig validation: index out of range ---

    @Test
    fun `AntennaConfig rejects antenna index exceeding numberOfAntennas`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(listOf(1, 3), 2)
        }
    }

    // --- AntennaConfig validation: zero-based indices ---

    @Test
    fun `AntennaConfig rejects zero-based antenna indices`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(listOf(0, 1), 2)
        }
    }

    @Test
    fun `AntennaConfig rejects negative antenna indices`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(listOf(-1, 1), 2)
        }
    }

    // --- AntennaConfig validation: valid configurations ---

    @Test
    fun `AntennaConfig accepts single antenna configuration`() {
        val config = AntennaConfig(listOf(1), 1)
        assertEquals(listOf(1), config.antennaSwitchPattern)
        assertEquals(1, config.numberOfAntennas)
    }

    @Test
    fun `AntennaConfig accepts uniform array pattern`() {
        val config = AntennaConfig(listOf(1, 2, 1, 2), 2)
        assertEquals(listOf(1, 2, 1, 2), config.antennaSwitchPattern)
        assertEquals(2, config.numberOfAntennas)
    }

    @Test
    fun `AntennaConfig accepts custom layout pattern`() {
        val config = AntennaConfig(listOf(1, 2, 3, 2, 1), 3)
        assertEquals(listOf(1, 2, 3, 2, 1), config.antennaSwitchPattern)
        assertEquals(3, config.numberOfAntennas)
    }

    // --- DirectionFindingMode coverage ---

    @Test
    fun `DirectionFindingMode has both AoA and AoD values`() {
        val entries = DirectionFindingMode.entries
        assertEquals(2, entries.size)
        assertTrue(entries.contains(DirectionFindingMode.ANGLES_OF_ARRIVAL))
        assertTrue(entries.contains(DirectionFindingMode.ANGLES_OF_DEPARTURE))
    }

    // --- DirectionFindingResult: NotSupported ---

    @Test
    fun `DirectionFindingResult NotSupported is returned by default on fake`() =
        runTest {
            val peripheral = buildPeripheral()
            peripheral.connect()
            val result =
                peripheral.requestDirectionFinding(
                    DirectionFindingParameters(
                        mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                        cteLength = 8,
                        cteCount = 4,
                        antennaConfig = AntennaConfig(listOf(1, 2), 2),
                    ),
                )
            assertIs<DirectionFindingResult.NotSupported>(result)
            peripheral.close()
        }

    // --- DirectionFindingResult: Failed ---

    @Test
    fun `DirectionFindingResult Failed carries reason string`() {
        val failed = DirectionFindingResult.Failed("hardware not supported")
        assertEquals("hardware not supported", failed.reason)
    }

    @Test
    fun `DirectionFindingResult Failed can have null reason`() {
        val failed = DirectionFindingResult.Failed()
        assertEquals(null, failed.reason)
    }

    // --- DirectionFindingResult: Angle ---

    @Test
    fun `DirectionFindingResult Angle carries azimuth and elevation`() {
        val angle = DirectionFindingResult.Angle(45.0f, 30.0f, -50.0f)
        assertEquals(45.0f, angle.azimuth)
        assertEquals(30.0f, angle.elevation)
        assertEquals(-50.0f, angle.signalQuality)
    }

    @Test
    fun `DirectionFindingResult Angle signalQuality is nullable`() {
        val angle = DirectionFindingResult.Angle(180.0f, 0.0f, null)
        assertEquals(180.0f, angle.azimuth)
        assertEquals(0.0f, angle.elevation)
        assertEquals(null, angle.signalQuality)
    }

    @Test
    fun `DirectionFindingResult Angle accepts boundary azimuth values`() {
        // North (0 degrees)
        val north = DirectionFindingResult.Angle(0.0f, 0.0f, -60.0f)
        assertEquals(0.0f, north.azimuth)

        // Full circle (360 degrees)
        val fullCircle = DirectionFindingResult.Angle(360.0f, 0.0f, -60.0f)
        assertEquals(360.0f, fullCircle.azimuth)
    }

    @Test
    fun `DirectionFindingResult Angle accepts boundary elevation values`() {
        // Horizontal plane (0 degrees)
        val horizontal = DirectionFindingResult.Angle(90.0f, 0.0f, -55.0f)
        assertEquals(0.0f, horizontal.elevation)

        // Upward (+90 degrees)
        val upward = DirectionFindingResult.Angle(90.0f, 90.0f, -55.0f)
        assertEquals(90.0f, upward.elevation)

        // Downward (-90 degrees)
        val downward = DirectionFindingResult.Angle(90.0f, -90.0f, -55.0f)
        assertEquals(-90.0f, downward.elevation)
    }

    // --- Lifecycle: connection state ---

    @Test
    fun `requestDirectionFinding throws when not connected`() =
        runTest {
            val peripheral = buildPeripheral()
            try {
                peripheral.requestDirectionFinding(
                    DirectionFindingParameters(
                        mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                        cteLength = 2,
                        cteCount = 1,
                        antennaConfig = AntennaConfig(listOf(1), 1),
                    ),
                )
                fail("Expected IllegalStateException when not connected")
            } catch (e: IllegalStateException) {
                assertTrue("not connected" in e.message.orEmpty().lowercase())
            }
        }

    // --- Platform behavior: FakePeripheral with custom handler ---

    @Test
    fun `requestDirectionFinding returns configured Angle result`() =
        runTest {
            val expected = DirectionFindingResult.Angle(135.0f, 15.0f, -42.0f)
            val peripheral =
                FakePeripheral {
                    onDirectionFinding { expected }
                }
            peripheral.connect()
            val result =
                peripheral.requestDirectionFinding(
                    DirectionFindingParameters(
                        mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                        cteLength = 8,
                        cteCount = 4,
                        antennaConfig = AntennaConfig(listOf(1, 2), 2),
                    ),
                )
            assertIs<DirectionFindingResult.Angle>(result)
            assertEquals(135.0f, result.azimuth)
            assertEquals(15.0f, result.elevation)
            assertEquals(-42.0f, result.signalQuality)
            peripheral.close()
        }

    // --- Edge cases: invalid antenna configurations ---

    @Test
    fun `AntennaConfig with duplicate antenna IDs is valid`() {
        // Duplicate IDs in pattern are allowed - the pattern just repeats
        val config = AntennaConfig(listOf(1, 1, 2, 2), 2)
        assertEquals(listOf(1, 1, 2, 2), config.antennaSwitchPattern)
        assertEquals(2, config.numberOfAntennas)
    }

    @Test
    fun `AntennaConfig with long switch pattern is valid`() {
        val config = AntennaConfig(listOf(1, 2, 1, 2, 1, 2, 1, 2), 2)
        assertEquals(8, config.antennaSwitchPattern.size)
        assertEquals(2, config.numberOfAntennas)
    }

    // --- Cross-platform parity test template ---

    @Test
    fun `DirectionFindingParameters equality is consistent`() {
        val params1 =
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 8,
                cteCount = 4,
                antennaConfig = AntennaConfig(listOf(1, 2), 2),
            )
        val params2 =
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 8,
                cteCount = 4,
                antennaConfig = AntennaConfig(listOf(1, 2), 2),
            )
        assertEquals(params1, params2)
        assertEquals(params1.hashCode(), params2.hashCode())
    }

    @Test
    fun `DirectionFindingParameters inequality on mode change`() {
        val params1 =
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 8,
                cteCount = 4,
                antennaConfig = AntennaConfig(listOf(1, 2), 2),
            )
        val params2 =
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_DEPARTURE,
                cteLength = 8,
                cteCount = 4,
                antennaConfig = AntennaConfig(listOf(1, 2), 2),
            )
        assertNotEquals(params1, params2)
    }
}
