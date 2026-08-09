package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.testing.FakeScanner
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class ScannerExtensionsTest {
    private fun ad(
        identifier: String = "a",
        name: String? = "Sensor",
    ) = Advertisement(
        identifier = Identifier(identifier),
        name = name,
        rssi = -60,
        txPower = null,
        isConnectable = true,
        serviceUuids = emptyList(),
        manufacturerData = emptyMap(),
        serviceData = emptyMap(),
        timestampNanos = 0L,
    )

    @Test
    fun firstOrNullMatchesPredicate() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement { name("A") }
                    advertisement { name("B") }
                }
            val result = scanner.firstOrNull(timeout = 500.milliseconds) { it.name == "B" }
            assertNotNull(result)
            assertEquals("B", result.name)
        }

    @Test
    fun firstOrNullReturnsNullOnTimeout() =
        runTest {
            val scanner = FakeScanner {}
            val result = scanner.firstOrNull(timeout = 10.milliseconds)
            assertNull(result)
        }

    @Test
    fun firstOrNullReturnsNullWhenNoMatch() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement { name("A") }
                }
            val result = scanner.firstOrNull(timeout = 50.milliseconds) { it.name == "Z" }
            assertNull(result)
        }

    @Test
    fun firstOrThrowMatchesPredicate() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement { name("A") }
                    advertisement { name("Target") }
                }
            val result = scanner.firstOrThrow(timeout = 500.milliseconds) { it.name == "Target" }
            assertEquals("Target", result.name)
        }

    @Test
    fun firstOrThrowThrowsOnTimeout() =
        runTest {
            val scanner = FakeScanner {}
            assertFailsWith<TimeoutCancellationException> {
                scanner.firstOrThrow(timeout = 10.milliseconds)
            }
        }

    @Test
    fun firstOrThrowRethrowsScanFailed() =
        runTest {
            val scanner = FakeScanner {}
            scanner.emitScanFailed(2)

            val ex =
                assertFailsWith<ScanFailedException> {
                    scanner.firstOrThrow(timeout = 500.milliseconds)
                }
            assertEquals(2, ex.errorCode)
        }

    @Test
    fun firstOrThrowThrowsTimeoutWhenNoMatch() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement { name("A") }
                }
            assertFailsWith<TimeoutCancellationException> {
                scanner.firstOrThrow(timeout = 50.milliseconds) { it.name == "Z" }
            }
        }

    // --- scanBatch ---

    @Test
    fun scanBatchReturnsTopNByRssi() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Weak")
                        rssi(-80)
                    }
                    advertisement {
                        name("Strong")
                        rssi(-40)
                    }
                    advertisement {
                        name("Mid")
                        rssi(-60)
                    }
                }
            val result = scanner.scanBatch(limit = 2, timeout = 500.milliseconds)
            assertEquals(listOf("Strong", "Mid"), result.map { it.name })
        }

    @Test
    fun scanBatchTracksBestRssiPerDevice() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement { identifier("a"); name("A"); rssi(-80) }
                }
            scanner.emit(
                Advertisement(
                    identifier = Identifier("a"),
                    name = "A",
                    rssi = -50, // stronger than the configured -80
                    txPower = null,
                    isConnectable = true,
                    serviceUuids = emptyList(),
                    manufacturerData = emptyMap(),
                    serviceData = emptyMap(),
                    timestampNanos = 1L,
                ),
            )
            val result = scanner.scanBatch(limit = 1, timeout = 500.milliseconds)
            assertEquals(1, result.size)
            assertEquals(-50, result.first().rssi)
        }

    @Test
    fun scanBatchReturnsEmptyOnTimeout() =
        runTest {
            val scanner = FakeScanner {}
            val result = scanner.scanBatch(limit = 3, timeout = 10.milliseconds)
            assertEquals(emptyList(), result)
        }

    @Test
    fun scanBatchRejectsNonPositiveLimit() =
        runTest {
            val scanner = FakeScanner {}
            assertFailsWith<IllegalArgumentException> {
                scanner.scanBatch(limit = 0)
            }
        }
}
