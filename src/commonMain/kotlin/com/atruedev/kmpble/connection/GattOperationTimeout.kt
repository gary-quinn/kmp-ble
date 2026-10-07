package com.atruedev.kmpble.connection

import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Runs [block] with [timeout] as the time limit for each GATT operation issued
 * inside it, in place of the connection's per-operation defaults
 * ([ConnectionOptions.timeouts] and [ConnectionOptions.gattOperationTimeout]).
 *
 * Those defaults are fixed per connection, and an outer `withTimeout` cannot
 * lengthen them: the shorter per-operation limit fires first. Use this scope when
 * one operation is expected to take longer than the rest, for example a read of an
 * encrypted characteristic that makes the OS start pairing and then waits for the
 * user to type a passkey:
 *
 * ```kotlin
 * val value = withGattOperationTimeout(60.seconds) {
 *     peripheral.read(protectedCharacteristic)
 * }
 * ```
 *
 * Reads issued outside the block, such as a periodic liveness read, keep the short
 * default, so a dead link is still detected quickly.
 *
 * - The limit applies to each operation on its own, not to the block as a whole,
 *   and may be longer or shorter than the default.
 * - It covers every operation serialized on the connection's GATT queue:
 *   characteristic and descriptor reads and writes (including
 *   [com.atruedev.kmpble.peripheral.Peripheral.writeReliable]), `readRssi`,
 *   `requestMtu`, and any other operation the platform runs on that queue. The
 *   connect timeout, L2CAP channel opening, and service discovery on Android and iOS
 *   do not use the queue and keep their [OperationTimeouts] values. The JVM backends
 *   queue service discovery, so there it is covered too.
 * - The queue is serial and the limit includes time spent waiting behind earlier
 *   operations. While a lengthened operation is in flight, operations issued
 *   outside the block wait behind it and can hit their own shorter limit.
 * - The value travels in the coroutine context: coroutines launched inside the
 *   block inherit it, and the innermost of nested scopes wins.
 * - [Duration.INFINITE] removes the limit for operations inside the block.
 *
 * @throws IllegalArgumentException if [timeout] is zero or negative.
 */
public suspend fun <T> withGattOperationTimeout(
    timeout: Duration,
    block: suspend () -> T,
): T {
    require(timeout.isPositive()) { "timeout must be positive, was $timeout" }
    return withContext(GattOperationTimeoutOverride(timeout)) { block() }
}

/**
 * Carries the [withGattOperationTimeout] value to the GATT operation queue through
 * the caller's coroutine context, so no Peripheral method needs a timeout parameter.
 */
internal class GattOperationTimeoutOverride(
    val timeout: Duration,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GattOperationTimeoutOverride>
}
