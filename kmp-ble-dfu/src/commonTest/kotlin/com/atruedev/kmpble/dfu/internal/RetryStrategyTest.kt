package com.atruedev.kmpble.dfu.internal

import com.atruedev.kmpble.dfu.DfuError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class RetryStrategyTest {

    @Test
    fun succeedsOnFirstAttempt() = runTest {
        val result = retryOnFailure(3, 10.milliseconds) { 42 }
        assertEquals(42, result)
    }

    @Test
    fun retriesAndSucceeds() = runTest {
        var attempts = 0
        val result = retryOnFailure(3, 10.milliseconds) { attempt ->
            attempts++
            if (attempt < 2) throw RuntimeException("fail")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun exhaustsRetriesAndThrows() = runTest {
        assertFailsWith<DfuError.TransferFailed> {
            retryOnFailure(2, 10.milliseconds) {
                throw RuntimeException("always fails")
            }
        }
    }

    @Test
    fun cancellationExceptionNotRetried() = runTest {
        var attempts = 0
        assertFailsWith<CancellationException> {
            retryOnFailure(3, 10.milliseconds) {
                attempts++
                throw CancellationException("cancelled")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun abortNotRetried() = runTest {
        var attempts = 0
        assertFailsWith<DfuError.Aborted> {
            retryOnFailure(3, 10.milliseconds) {
                attempts++
                throw DfuError.Aborted()
            }
        }
        assertEquals(1, attempts)
    }
}
