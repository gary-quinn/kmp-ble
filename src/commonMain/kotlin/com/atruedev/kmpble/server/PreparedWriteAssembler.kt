package com.atruedev.kmpble.server

import kotlin.uuid.Uuid

internal class WriteFragment(
    val charUuid: Uuid,
    val offset: Int,
    val bytes: ByteArray,
)

internal class AssembledWrite(
    val charUuid: Uuid,
    val data: ByteArray,
)

/** BLE spec: maximum characteristic value length (Core Spec Vol 3, Part F, 3.2.9). */
internal const val MAX_CHARACTERISTIC_VALUE_SIZE = 512

/** Server-side cap on buffered Prepared Write bytes per device to bound memory usage. */
internal const val MAX_PREPARED_WRITE_BUFFER_BYTES = 4096

/**
 * Assemble Prepared Write fragments into contiguous byte arrays, grouped by characteristic.
 *
 * Fragments are sorted by offset. Overlapping ranges use last-write-wins semantics
 * (BLE Reliable Write behavior). Throws [IllegalArgumentException] if the assembled
 * size exceeds [MAX_CHARACTERISTIC_VALUE_SIZE].
 */
internal fun assembleWriteFragments(fragments: List<WriteFragment>): List<AssembledWrite> =
    fragments
        .groupBy { it.charUuid }
        .map { (charUuid, charFragments) ->
            val sorted = charFragments.sortedBy { it.offset }
            val totalSize = sorted.maxOf { it.offset + it.bytes.size }
            require(totalSize <= MAX_CHARACTERISTIC_VALUE_SIZE) {
                "Assembled write ($totalSize bytes) exceeds BLE max characteristic value size ($MAX_CHARACTERISTIC_VALUE_SIZE)"
            }
            val assembled = ByteArray(totalSize)
            for (fragment in sorted) {
                fragment.bytes.copyInto(assembled, destinationOffset = fragment.offset)
            }
            AssembledWrite(charUuid, assembled)
        }
