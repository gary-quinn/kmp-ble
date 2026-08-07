package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.direction.AntennaConfig
import com.atruedev.kmpble.direction.DirectionFindingMode
import com.atruedev.kmpble.direction.DirectionFindingParameters
import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

public abstract class DirectionFindingConformanceTest {
    protected abstract fun buildPeripheral(): FakePeripheral

    // --- cteLength validation ---

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
            antennaConfig = AntennaConfig(listOf(1, 2), 2),
        )
    }

    // --- cteCount validation ---

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
    fun `AntennaConfig rejects zero numberOfAntennas`() {
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
    fun `AntennaConfig rejects negative indices`() {
        assertFailsWith<IllegalArgumentException> {
            AntennaConfig(listOf(-1, 1), 2)
        }
    }

    @Test
    fun `AntennaConfig accepts single antenna`() {
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

    @Test
    fun `AntennaConfig accepts duplicate IDs in pattern`() {
        val config = AntennaConfig(listOf(1, 1, 2, 2), 2)
        assertEquals(listOf(1, 1, 2, 2), config.antennaSwitchPattern)
        assertEquals(2, config.numberOfAntennas)
    }

    @Test
    fun `AntennaConfig accepts long switch pattern`() {
        val config = AntennaConfig(listOf(1, 2, 1, 2, 1, 2, 1, 2), 2)
        assertEquals(8, config.antennaSwitchPattern.size)
        assertEquals(2, config.numberOfAntennas)
    }

    // --- DirectionFindingMode ---

    @Test
    fun `DirectionFindingMode has both AoA and AoD values`() {
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

    // --- DirectionFindingResult: NotSupported ---

    @Test
    fun `DirectionFindingResult NotSupported is singleton`() {
        assertEquals(
            DirectionFindingResult.NotSupported,
            DirectionFindingResult.NotSupported,
        )
    }

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

    // --- DirectionFindingResult: Failed ---

    @Test
    fun `DirectionFindingResult Failed carries reason string`() {
        val failed = DirectionFindingResult.Failed("hardware unsupported")
        assertEquals("hardware unsupported", failed.reason)
    }

    @Test
    fun `DirectionFindingResult Failed null reason`() {
        val failed = DirectionFindingResult.Failed()
        assertNull(failed.reason)
    }

    @Test
    fun `DirectionFindingResult Failed empty reason`() {
        val failed = DirectionFindingResult.Failed("")
        assertNotNull(failed.reason)
        assertEquals("", failed.reason)
    }

    // --- DirectionFindingResult: Angle ---

    @Test
    fun `DirectionFindingResult Angle carries azimuth elevation signalQuality`() {
        val angle = DirectionFindingResult.Angle(45.0f, 30.0f, -50.0f)
        assertEquals(45.0f, angle.azimuth)
        assertEquals(30.0f, angle.elevation)
        assertEquals(-50.0f, angle.signalQuality)
    }

    @Test
    fun `DirectionFindingResult Angle signalQuality nullable`() {
        val angle = DirectionFindingResult.Angle(180.0f, 0.0f, null)
        assertEquals(180.0f, angle.azimuth)
        assertEquals(0.0f, angle.elevation)
        assertNull(angle.signalQuality)
    }

    @Test
    fun `DirectionFindingResult Angle accepts boundary azimuth`() {
        val north = DirectionFindingResult.Angle(0.0f, 0.0f, -60.0f)
        assertEquals(0.0f, north.azimuth)
        val fullCircle = DirectionFindingResult.Angle(360.0f, 0.0f, -60.0f)
        assertEquals(360.0f, fullCircle.azimuth)
    }

    @Test
    fun `DirectionFindingResult Angle rejects out of range azimuth`() {
        assertFailsWith<IllegalArgumentException> {
            DirectionFindingResult.Angle(-1.0f, 0.0f, -60.0f)
        }
        assertFailsWith<IllegalArgumentException> {
            DirectionFindingResult.Angle(361.0f, 0.0f, -60.0f)
        }
    }

    @Test
    fun `DirectionFindingResult Angle accepts boundary elevation`() {
        val horizontal = DirectionFindingResult.Angle(90.0f, 0.0f, -55.0f)
        assertEquals(0.0f, horizontal.elevation)
        val upward = DirectionFindingResult.Angle(90.0f, 90.0f, -55.0f)
        assertEquals(90.0f, upward.elevation)
        val downward = DirectionFindingResult.Angle(90.0f, -90.0f, -55.0f)
        assertEquals(-90.0f, downward.elevation)
    }

    @Test
    fun `DirectionFindingResult Angle rejects out of range elevation`() {
        assertFailsWith<IllegalArgumentException> {
            DirectionFindingResult.Angle(90.0f, 91.0f, -55.0f)
        }
        assertFailsWith<IllegalArgumentException> {
            DirectionFindingResult.Angle(90.0f, -91.0f, -55.0f)
        }
    }

    // --- Lifecycle ---

    @Test
    fun `requestDirectionFinding throws when not connected`() =
        runTest {
            val peripheral = buildPeripheral()
            assertFailsWith<IllegalStateException> {
                peripheral.requestDirectionFinding(
                    DirectionFindingParameters(
                        mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                        cteLength = 2,
                        cteCount = 1,
                        antennaConfig = AntennaConfig(listOf(1), 1),
                    ),
                )
            }
            peripheral.close()
        }

    // --- Custom handler integration ---

    @Test
    fun `requestDirectionFinding uses builder handler when configured`() =
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

    @Test
    fun `requestDirectionFinding uses builder handler returning Failed`() =
        runTest {
            val peripheral =
                FakePeripheral {
                    onDirectionFinding { DirectionFindingResult.Failed("simulated failure") }
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
            assertIs<DirectionFindingResult.Failed>(result)
            assertEquals("simulated failure", (result as DirectionFindingResult.Failed).reason)
            peripheral.close()
        }

    // --- Concurrency: structured concurrency without locks ---

    @Test
    fun `requestDirectionFinding handles concurrent calls`() =
        runTest {
            val peripheral =
                FakePeripheral {
                    onDirectionFinding { DirectionFindingResult.Angle(45.0f, 10.0f, -50.0f) }
                }
            peripheral.connect()
            coroutineScope {
                val resultA =
                    async {
                        peripheral.requestDirectionFinding(
                            DirectionFindingParameters(
                                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                                cteLength = 8,
                                cteCount = 4,
                                antennaConfig = AntennaConfig(listOf(1, 2), 2),
                            ),
                        )
                    }
                val resultB =
                    async {
                        peripheral.requestDirectionFinding(
                            DirectionFindingParameters(
                                mode = DirectionFindingMode.ANGLES_OF_DEPARTURE,
                                cteLength = 10,
                                cteCount = 8,
                                antennaConfig = AntennaConfig(listOf(1, 2, 3, 4), 4),
                            ),
                        )
                    }
                assertIs<DirectionFindingResult.Angle>(resultA.await())
                assertIs<DirectionFindingResult.Angle>(resultB.await())
            }
            peripheral.close()
        }

    // --- Cancellation ---

    @Test
    fun `requestDirectionFinding propagates cancellation`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val peripheral =
                FakePeripheral {
                    onDirectionFinding {
                        started.complete(Unit)
                        delay(60_000) // never completes unless cancelled
                        DirectionFindingResult.Angle(45.0f, 10.0f, -50.0f)
                    }
                }
            peripheral.connect()
            coroutineScope {
                val job =
                    launch {
                        peripheral.requestDirectionFinding(
                            DirectionFindingParameters(
                                mode = DirectionFindingMode.ANGLES_OF_ARRIVAL,
                                cteLength = 8,
                                cteCount = 4,
                                antennaConfig = AntennaConfig(listOf(1, 2), 2),
                            ),
                        )
                    }
                started.await()
                job.cancel()
                job.join()
                assertTrue(job.isCancelled)
            }
            peripheral.close()
        }

    // --- Cross-platform parity ---

    @Test
    fun `DirectionFindingParameters equality`() {
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
    fun `DirectionFindingParameters inequality mode`() {
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

    @Test
    fun `DirectionFindingParameters inequality cteLength`() {
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
                cteLength = 10,
                cteCount = 4,
                antennaConfig = AntennaConfig(listOf(1, 2), 2),
            )
        assertNotEquals(params1, params2)
    }

    @Test
    fun `DirectionFindingParameters inequality cteCount`() {
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
                cteCount = 8,
                antennaConfig = AntennaConfig(listOf(1, 2), 2),
            )
        assertNotEquals(params1, params2)
    }

    @Test
    fun `DirectionFindingParameters inequality antennaConfig`() {
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
                antennaConfig = AntennaConfig(listOf(1, 2, 3), 3),
            )
        assertNotEquals(params1, params2)
    }
}
