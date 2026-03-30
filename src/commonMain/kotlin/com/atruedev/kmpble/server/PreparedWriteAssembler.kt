package com.atruedev.kmpble.server

import kotlin.uuid.Uuid

/** Single fragment from a BLE Prepared Write (Write Long) request. */
internal class WriteFragment(
    val charUuid: Uuid,
    val offset: Int,
    val bytes: ByteArray,
)

/** Assembled result of all fragments for a single characteristic. */
internal class AssembledWrite(
    val charUuid: Uuid,
    val data: ByteArray,
)

/** BLE spec maximum characteristic value length. */
internal const val MAX_CHARACTERISTIC_VALUE_SIZE = 512

/**
 * Assemble Prepared Write fragments into contiguous byte arrays, grouped by characteristic.
 *
 * Fragments are sorted by offset before assembly. Overlapping offset ranges use
 * last-write-wins semantics (per BLE Reliable Write behavior).
 */
internal fun assembleWriteFragments(fragments: List<WriteFragment>): List<AssembledWrite> =
    fragments
        .groupBy { it.charUuid }
        .map { (charUuid, charFragments) ->
            val sorted = charFragments.sortedBy { it.offset }
            val totalSize = sorted.maxOf { it.offset + it.bytes.size }
            val assembled = ByteArray(totalSize)
            for (fragment in sorted) {
                fragment.bytes.copyInto(assembled, destinationOffset = fragment.offset)
            }
            AssembledWrite(charUuid, assembled)
        }
