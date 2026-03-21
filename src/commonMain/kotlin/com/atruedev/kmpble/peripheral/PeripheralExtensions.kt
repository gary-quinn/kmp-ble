package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Returns a human-readable GATT service/characteristic/descriptor tree.
 *
 * Useful for debugging — answers "what does this device expose?" in one call.
 * Only meaningful after service discovery completes (i.e., in [State.Connected.Ready]).
 *
 * Takes a consistent snapshot of [state] and [services] before formatting,
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
                val charPrefix = if (isLastChar) "${childPrefix}└── " else "${childPrefix}├── "
                val descPrefix = if (isLastChar) "${childPrefix}    " else "${childPrefix}│   "

                appendLine("${charPrefix}Char ${char.uuid} [${char.properties.displayName}]")

                char.descriptors.forEachIndexed { descIdx, desc ->
                    val isLastDesc = descIdx == char.descriptors.lastIndex
                    val dp = if (isLastDesc) "${descPrefix}└── " else "${descPrefix}├── "
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
private val WELL_KNOWN_DESCRIPTORS: Map<Uuid, String> = mapOf(
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
 * - Delegates state validation to [connect] — if the peripheral is already connected
 *   or connecting, [connect]'s own invariants apply
 * - If connection drops mid-block: the block's coroutine is cancelled with
 *   [kotlinx.coroutines.CancellationException], then close() runs in finally
 * - [close] always runs in a [NonCancellable] context, guaranteeing cleanup
 *   even if the coroutine is cancelled mid-block
 *
 * Not thread-safe — callers must ensure exclusive access to this peripheral.
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
