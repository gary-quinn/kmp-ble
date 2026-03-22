package com.atruedev.kmpble.dfu.internal

import com.atruedev.kmpble.dfu.DfuError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration

internal suspend fun <T> retryOnFailure(
    maxAttempts: Int,
    delay: Duration,
    block: suspend (attempt: Int) -> T,
): T {
    var lastException: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block(attempt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DfuError.Aborted) {
            throw e
        } catch (e: Throwable) {
            lastException = e
            if (attempt < maxAttempts - 1) {
                delay(delay)
            }
        }
    }
    throw DfuError.TransferFailed(
        "Failed after $maxAttempts attempts: ${lastException?.message}",
        lastException,
    )
}
