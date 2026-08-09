package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.testing.FakeScanner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

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
