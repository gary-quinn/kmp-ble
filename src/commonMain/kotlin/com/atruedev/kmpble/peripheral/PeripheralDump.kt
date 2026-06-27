package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Returns a human-readable GATT service/characteristic/descriptor tree.
 *
 * Useful for debugging - answers "what does this device expose?" in one call.
 * Only meaningful after service discovery completes (i.e., in [com.atruedev.kmpble.connection.State.Connected.Ready]).
 *
 * Takes a consistent snapshot of [PeripheralConnection.services] before formatting,
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
