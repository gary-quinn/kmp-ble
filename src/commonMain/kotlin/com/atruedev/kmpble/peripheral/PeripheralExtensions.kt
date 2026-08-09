package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.error.PeripheralTimeout
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Returns a human-readable GATT service/characteristic/descriptor tree.
 *
 * Useful for debugging - answers "what does this device expose?" in one call.
 * Only meaningful after service discovery completes (i.e., in [com.atruedev.kmpble.peripheral.state.State.Connected.Ready]).
 *
 * Takes a consistent snapshot of [Peripheral.state] and [Peripheral.services] before formatting,
 * so the output is internally coherent even under concurrent state changes.
 *
 * ```
 * peripheral.connect()
 * println(peripheral.dump())
 * ```
 *
 * Output:
 * ```
 * Peripheral: AB:CD:EF:12:34:56 (state: Connected.Ready)
 * ├── Service 0000180d-...
 * │   ├── Char 00002a37-... [notify, read]
 * │   │   └── Desc 00002902-...
 * │   └── Char 00002a38-... [read]
 * └── Service 0000180f-...
 *     └── Char 00002a19-... [read, notify]
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
public fun Peripheral.dump(): String {
    val snapshotState = state.value
    val snapshotServices = services.value

    return buildString {
        appendLine("Peripheral: ${identifier.value} (state: $snapshotState)")

        if (snapshotServices.isNullOrEmpty()) {
            appendLine("  (no services discovered)")
            return@buildString
        }

        snapshotServices.forEachIndexed { svcIdx, service ->
            val isLastService = svcIdx == snapshotServices.lastIndex
            val svcPrefix = if (isLastService) "└── " else "├── "
            val childPrefix = if (isLastService) "    " else "│   "

            appendLine("${svcPrefix}Service ${service.uuid}")

            service.characteristics.forEachIndexed { charIdx, char ->
                val isLastChar = charIdx == service.characteristics.lastIndex
                val charPrefix = if (isLastChar) "$childPrefix└── " else "$childPrefix├── "
                val descPrefix = if (isLastChar) "$childPrefix    " else "$childPrefix│   "

                appendLine("${charPrefix}Char ${char.uuid} [${char.properties.displayName}]")

                char.descriptors.forEachIndexed { descIdx, desc ->
                    val isLastDesc = descIdx == char.descriptors.lastIndex
                    val dp = if (isLastDesc) "$descPrefix└── " else "$descPrefix├── "
                    val label = WELL_KNOWN_DESCRIPTORS[desc.uuid] ?: ""
                    val suffix = if (label.isNotEmpty()) " ($label)" else ""
                    appendLine("${dp}Desc ${desc.uuid}$suffix")
                }
            }
        }
    }.trimEnd()
}

/** Well-known descriptor UUIDs for human-readable labels in dump(). */
@OptIn(ExperimentalUuidApi::class)
private val WELL_KNOWN_DESCRIPTORS: Map<Uuid, String> =
    mapOf(
        uuidFrom("2902") to "CCCD",
        uuidFrom("2901") to "User Description",
        uuidFrom("2900") to "Extended Properties",
        uuidFrom("2904") to "Presentation Format",
    )

/**
 * Connect, execute [block] in the Ready state, then disconnect and close.
 *
 * The "one quick read" pattern:
 * ```
 * advertisement.toPeripheral().whenReady {
 *     val value = read(findCharacteristic(svcUuid, charUuid)!!)
 *     println("Battery: ${value[0]}%")
 * }
 * ```
 *
 * Behavior:
 * - Delegates state validation to [Peripheral.connect] - if the peripheral is already connected
 *   or connecting, [Peripheral.connect]'s own invariants apply
 * - If connection drops mid-block: the block's coroutine is cancelled with
 *   [kotlinx.coroutines.CancellationException], then close() runs in finally
 * - [Peripheral.close] always runs in a [NonCancellable] context, guaranteeing cleanup
 *   even if the coroutine is cancelled mid-block
 *
 * Not thread-safe - callers must ensure exclusive access to this peripheral.
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun Peripheral.whenReady(
    options: ConnectionOptions = ConnectionOptions(),
    block: suspend Peripheral.() -> Unit,
) {
    try {
        connect(options)
        block()
    } finally {
        withContext(NonCancellable) { close() }
    }
}

/**
 * Connect to a peripheral and automatically discover its GATT services.
 *
 * Combines the two-step connect-then-discover workflow into a single call,
 * returning the discovered services on success and cleaning up the
 * connection if discovery fails.
 *
 * ```kotlin
 * val services = peripheral.connectAndDiscover(
 *     ConnectionOptions.Balanced,
 * )
 * services.forEach { svc ->
 *     println("Service: ${svc.uuid}")
 * }
 * ```
 *
 * @param options Connection configuration. Timeouts for both connect and
 *   service discovery phases are taken from [ConnectionOptions.timeouts].
 * @return The list of discovered GATT services.
 * @throws BleException if connection fails.
 * @throws BleException if service discovery fails (connection is released).
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun Peripheral.connectAndDiscover(
    options: ConnectionOptions = ConnectionOptions(),
): List<DiscoveredService> {
    connect(options)
    return try {
        refreshServices()
    } catch (e: Exception) {
        // Release connection on discovery failure so the peripheral
        // is not left in a half-connected state.
        withContext(NonCancellable) { disconnect() }
        throw e
    }
}

/**
 * Read multiple characteristics, returning each result individually with
 * per-characteristic failure isolation.
 *
 * Reads execute sequentially: each [read] goes through the peripheral's internal
 * GATT operation queue, which serializes operations on the radio anyway, so there
 * is no parallel-radio win to be had. Sequential execution also gives every read a
 * fresh, correct deadline -- each uses the connection's configured
 * [OperationTimeouts.read] (set via [ConnectionOptions.timeouts], 5s default).
 *
 * A failure on one characteristic (timeout, GATT error, disconnect) does not abort
 * the remaining reads.
 *
 * Failures surface as [BleException] values in the [Result], consistent with the
 * library's error model:
 * - queue deadline exceeded -> [PeripheralTimeout]
 * - disconnect mid-batch -> [ConnectionLost]
 * - GATT error -> the original [BleException]
 * - unexpected exception -> [OperationFailed]
 *
 * [CancellationException] (external cancellation of the calling coroutine)
 * propagates instead of being converted to a failure.
 *
 * ```
 * val results = peripheral.batchRead(listOf(batteryChar, tempChar, hrChar))
 * val battery = results[batteryChar]?.getOrNull()
 * val temp = results[tempChar]?.getOrThrow()
 * ```
 *
 * @throws IllegalArgumentException if [characteristics] is empty or contains
 *   duplicates.
 * @throws kotlinx.coroutines.CancellationException if the calling coroutine is
 *   cancelled while reads are in flight.
 */
public suspend fun Peripheral.batchRead(
    characteristics: List<Characteristic>,
): Map<Characteristic, Result<ByteArray>> {
    require(characteristics.isNotEmpty()) { "characteristics must not be empty" }
    require(characteristics.distinct().size == characteristics.size) {
        "characteristics must not contain duplicates"
    }
    return characteristics.associateWith { readBatchValue(it) }
}

private suspend fun Peripheral.readBatchValue(characteristic: Characteristic): Result<ByteArray> =
    try {
        Result.success(read(characteristic))
    } catch (e: TimeoutCancellationException) {
        // MUST precede CancellationException -- TimeoutCancellationException extends it.
        val timeout = lastConnectionOptions?.let { it.timeouts.read } ?: DEFAULT_READ_TIMEOUT
        Result.failure(BleException(PeripheralTimeout(operation = "read", timeout = timeout)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: BleException) {
        Result.failure(e)
    } catch (e: NotConnectedException) {
        Result.failure(BleException(ConnectionLost("Connection lost during batch read")))
    } catch (e: Exception) {
        Result.failure(BleException(OperationFailed(e.message ?: "Batch read failed")))
    }

private val DEFAULT_READ_TIMEOUT: Duration = OperationTimeouts().read
