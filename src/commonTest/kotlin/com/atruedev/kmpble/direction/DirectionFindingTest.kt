package com.atruedev.kmpble.direction

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class DirectionFindingTest {
    // --- Parameter validation ---

    @Test
    fun `DirectionFindingParameters rejects out of range cteLength`() {
        assertFailsWith<IllegalArgumentException>("cteLength 1") {
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 1,
                cteCount = 1,
                antennaConfig = AntennaConfig(listOf(1), 1),
            )
        }
        assertFailsWith<IllegalArgumentException>("cteLength 21") {
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 21,
                cteCount = 1,
                antennaConfig = AntennaConfig(listOf(1), 1),
            )
        }
    }

    @Test
    fun `DirectionFindingParameters rejects out of range cteCount`() {
        assertFailsWith<IllegalArgumentException>("cteCount 0") {
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 2,
                cteCount = 0,
                antennaConfig = AntennaConfig(listOf(1), 1),
            )
        }
        assertFailsWith<IllegalArgumentException>("cteCount 17") {
            DirectionFindingParameters(
                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                cteLength = 2,
                cteCount = 17,
                antennaConfig = AntennaConfig(listOf(1), 1),
            )
        }
    }

    @Test
    fun `DirectionFindingParameters accepts boundary values`() {
        DirectionFindingParameters(
            mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
            cteLength = 2,
            cteCount = 1,
            antennaConfig = AntennaConfig(listOf(1), 1),
        )
        DirectionFindingParameters(
            mode = DirectionFindingMode.ANGLES_OF_DEPARTURE,
            cteLength = 20,
            cteCount = 16,
            antennaConfig = AntennaConfig(listOf(1, 2, 1, 2), 2),
        )
    }

    // --- AntennaConfig validation ---

    @Test
    fun `AntennaConfig rejects empty switch pattern`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(emptyList(), 1)
        }
    }

    @Test
    fun `AntennaConfig rejects zero antennas`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(listOf(1), 0)
        }
    }

    @Test
    fun `AntennaConfig rejects index exceeding numberOfAntennas`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(listOf(1, 3), 2)
        }
    }

    @Test
    fun `AntennaConfig rejects zero-based indices`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(listOf(0, 1), 2)
        }
    }

    @Test
    fun `AntennaConfig accepts valid configuration`() {
        val config = AntennaConfig(listOf(1, 2, 1, 2), 2)
        assertEquals(listOf(1, 2, 1, 2), config.antennaSwitchPattern)
        assertEquals(2, config.numberOfAntennas)
    }

    // --- Result types ---

    @Test
    fun `DirectionFindingResult NotSupported is a singleton`() {
        assertEquals(
            DirectionFindingResult.NotSupported,
            DirectionFindingResult.NotSupported,
        )
    }

    @Test
    fun `DirectionFindingResult Failed carries reason`() {
        val failed = DirectionFindingResult.Failed("hardware unsupported")
        assertEquals("hardware unsupported", failed.reason)
    }

    @Test
    fun `DirectionFindingResult Angle carries values`() {
        val angle = DirectionFindingResult.Angle(45.0f, 10.0f, -42.0f)
        assertEquals(45.0f, angle.azimuth)
        assertEquals(10.0f, angle.elevation)
        assertEquals(-42.0f, angle.signalQuality)
    }

    @Test
    fun `DirectionFindingResult Angle signalQuality is nullable`() {
        val angle = DirectionFindingResult.Angle(180.0f, 0.0f, null)
        assertEquals(180.0f, angle.azimuth)
        assertEquals(null, angle.signalQuality)
    }

    // --- FakePeripheral integration ---

    @Test
    fun `requestDirectionFinding returns NotSupported on fake by default`() =
        runTest {
            val peripheral = FakePeripheral {}
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

    @Test
    fun `requestDirectionFinding throws when not connected on fake`() =
        runTest {
            val peripheral = FakePeripheral {}
            try {
                peripheral.requestDirectionFinding(
                    DirectionFindingParameters(
                        mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                        cteLength = 2,
                        cteCount = 1,
                        antennaConfig = AntennaConfig(listOf(1), 1),
                    ),
                )
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue("not connected" in e.message.orEmpty().lowercase())
            }
            peripheral.close()
        }

    @Test
    fun `requestDirectionFinding uses builder handler when configured`() =
        runTest {
            val expected = DirectionFindingResult.Angle(45.0f, 30.0f, -55.0f)
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
                        cteCount = 1,
                        antennaConfig = AntennaConfig(listOf(1), 1),
                    ),
                )
            assertIs<DirectionFindingResult.Angle>(result)
            assertEquals(45.0f, result.azimuth)
            assertEquals(30.0f, result.elevation)
            assertEquals(-55.0f, result.signalQuality)
            peripheral.close()
        }

    // --- DirectionFindingMode values ---

    @Test
    fun `DirectionFindingMode has both values`() {
        assertEquals(2, DirectionFindingMode.entries.size)
        assertEquals(
            DirectionFindingMode.ANGLES_OF_ARRIVAL,
            DirectionFindingMode.valueOf("ANGLES_OF_ARRIVAL"),
        )
        assertEquals(
            DirectionFindingMode.ANGLES_OF_DEPARTURE,
            DirectionFindingMode.valueOf("ANGLES_OF_DEPARTURE"),
        )
    }
}
