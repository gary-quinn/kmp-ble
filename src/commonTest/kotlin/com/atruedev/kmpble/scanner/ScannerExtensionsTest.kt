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
}
