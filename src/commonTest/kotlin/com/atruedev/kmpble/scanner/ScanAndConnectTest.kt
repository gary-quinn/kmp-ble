package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.testing.FakeScanner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ScanAndConnectTest {
    @Test
    fun throwsScanTimeoutExceptionWhenNoMatch() =
        runTest {
            val scanner = FakeScanner {}
            val ex =
                assertFailsWith<ScanTimeoutException> {
                    scanner.scanAndConnect(
                        scanTimeout = 10.milliseconds,
                        predicate = { true },
                    )
                }
            assertEquals(10.milliseconds, ex.scanTimeout)
        }

    @Test
    fun throwsScanFailedExceptionWhenTheScanFails() =
        runTest {
            val scanner = FakeScanner {}
            scanner.emitScanFailed(7)
            val ex =
                assertFailsWith<ScanFailedException> {
                    scanner.scanAndConnect(scanTimeout = 5.seconds, predicate = { true })
                }
            assertEquals(7, ex.errorCode)
        }

    @Test
    fun throwsScanTimeoutExceptionWhenPredicateNeverMatches() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement { name("A") }
                }
            assertFailsWith<ScanTimeoutException> {
                scanner.scanAndConnect(
                    scanTimeout = 50.milliseconds,
                    predicate = { it.name == "Z" },
                )
            }
        }
}
