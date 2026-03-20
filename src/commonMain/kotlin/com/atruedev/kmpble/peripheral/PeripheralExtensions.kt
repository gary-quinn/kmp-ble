package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

/**
 * Returns a human-readable GATT service/characteristic/descriptor tree.
 *
 * Useful for debugging — answers "what does this device expose?" in one call.
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
    val sb = StringBuilder()
    sb.appendLine("Peripheral: ${identifier.value} (state: ${state.value})")

    val discovered = services.value
    if (discovered.isNullOrEmpty()) {
        sb.appendLine("  (no services discovered)")
        return sb.toString()
    }

    discovered.forEachIndexed { svcIdx, service ->
        val isLastService = svcIdx == discovered.lastIndex
        val svcPrefix = if (isLastService) "└── " else "├── "
        val childPrefix = if (isLastService) "    " else "│   "

        sb.appendLine("${svcPrefix}Service ${service.uuid}")

        service.characteristics.forEachIndexed { charIdx, char ->
            val isLastChar = charIdx == service.characteristics.lastIndex
            val charPrefix = if (isLastChar) "${childPrefix}└── " else "${childPrefix}├── "
            val descPrefix = if (isLastChar) "${childPrefix}    " else "${childPrefix}│   "

            val props = buildProperties(char.properties)
            sb.appendLine("${charPrefix}Char ${char.uuid} [$props]")

            char.descriptors.forEachIndexed { descIdx, desc ->
                val isLastDesc = descIdx == char.descriptors.lastIndex
                val dp = if (isLastDesc) "${descPrefix}└── " else "${descPrefix}├── "
                sb.appendLine("${dp}Desc ${desc.uuid}")
            }
        }
    }

    return sb.toString().trimEnd()
}

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
 * - If already connected: throws [IllegalStateException] (use [connect] directly for
 *   existing connections — whenReady owns the full lifecycle)
 * - If connection drops mid-block: the block's coroutine is cancelled with
 *   [kotlinx.coroutines.CancellationException], then close() runs in finally
 * - [close] always runs in a finally block, regardless of success or failure
 */
@OptIn(ExperimentalUuidApi::class)
public suspend fun Peripheral.whenReady(
    options: ConnectionOptions = ConnectionOptions(),
    block: suspend Peripheral.() -> Unit,
) {
    check(state.value is State.Disconnected) {
        "whenReady{} manages the full connection lifecycle — peripheral must be disconnected (current: ${state.value})"
    }
    try {
        connect(options)
        block()
    } finally {
        close()
    }
}

/**
 * Find the first advertisement matching [predicate], or null after [timeout].
 *
 * The most common scan pattern in one line:
 * ```
 * val ad = scanner.firstOrNull(timeout = 10.seconds) { it.name == "HeartSensor" }
 * ```
 */
public suspend fun Scanner.firstOrNull(
    timeout: Duration = 30.seconds,
    predicate: (Advertisement) -> Boolean = { true },
): Advertisement? {
    return withTimeoutOrNull(timeout) {
        advertisements.firstOrNull(predicate)
    }
}
