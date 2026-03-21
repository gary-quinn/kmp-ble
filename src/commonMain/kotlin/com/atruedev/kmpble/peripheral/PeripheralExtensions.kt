package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.gatt.Characteristic
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

/**
 * Returns a human-readable GATT service/characteristic/descriptor tree.
 *
 * Useful for debugging — answers "what does this device expose?" in one call.
 * Only meaningful after service discovery completes (i.e., in [State.Connected.Ready]).
 *
 * Note: reads [state] and [services] as separate snapshots — under concurrent state
 * changes, the output may be momentarily inconsistent (e.g., state shows Ready but
 * services is null if a disconnect raced in between).
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
public fun Peripheral.dump(): String = buildString {
    appendLine("Peripheral: ${identifier.value} (state: ${state.value})")

    val discovered = services.value
    if (discovered.isNullOrEmpty()) {
        appendLine("  (no services discovered)")
        return@buildString
    }

    discovered.forEachIndexed { svcIdx, service ->
        val isLastService = svcIdx == discovered.lastIndex
        val svcPrefix = if (isLastService) "└── " else "├── "
        val childPrefix = if (isLastService) "    " else "│   "

        appendLine("${svcPrefix}Service ${service.uuid}")

        service.characteristics.forEachIndexed { charIdx, char ->
            val isLastChar = charIdx == service.characteristics.lastIndex
            val charPrefix = if (isLastChar) "${childPrefix}└── " else "${childPrefix}├── "
            val descPrefix = if (isLastChar) "${childPrefix}    " else "${childPrefix}│   "

            val props = buildProperties(char.properties)
            appendLine("${charPrefix}Char ${char.uuid} [$props]")

            char.descriptors.forEachIndexed { descIdx, desc ->
                val isLastDesc = descIdx == char.descriptors.lastIndex
                val dp = if (isLastDesc) "${descPrefix}└── " else "${descPrefix}├── "
                appendLine("${dp}Desc ${desc.uuid}")
            }
        }
    }
}.trimEnd()

private fun buildProperties(p: Characteristic.Properties): String {
    return buildList {
        if (p.read) add("read")
        if (p.write) add("write")
        if (p.writeWithoutResponse) add("writeNoResp")
        if (p.signedWrite) add("signedWrite")
        if (p.notify) add("notify")
        if (p.indicate) add("indicate")
    }.joinToString(", ")
}

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
