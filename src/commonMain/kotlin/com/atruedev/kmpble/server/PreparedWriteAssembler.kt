package com.atruedev.kmpble.server

import kotlin.uuid.Uuid

internal data class WriteFragment(
    val charUuid: Uuid,
    val offset: Int,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is WriteFragment &&
            charUuid == other.charUuid &&
            offset == other.offset &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int {
        var result = charUuid.hashCode()
        result = 31 * result + offset
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

internal data class AssembledWrite(
    val charUuid: Uuid,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is AssembledWrite && charUuid == other.charUuid && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * charUuid.hashCode() + data.contentHashCode()
}

/** BLE Core Spec Vol 3, Part F, 3.2.9: maximum characteristic value length. */
internal const val MAX_CHARACTERISTIC_VALUE_SIZE = 512

/** Per-device cap on total buffered Prepared Write bytes across all characteristics. */
internal const val MAX_PREPARED_WRITE_BUFFER_BYTES = 4096

internal sealed class AssemblyResult {
    data class Success(
        val writes: List<AssembledWrite>,
    ) : AssemblyResult()

    data class PayloadTooLarge(
        val charUuid: Uuid,
        val actualSize: Int,
    ) : AssemblyResult()
}

/**
 * Assemble Prepared Write fragments into contiguous byte arrays, grouped by characteristic.
 *
 * Fragments are sorted by offset within each group. Overlapping ranges use
 * last-write-wins semantics per BLE Reliable Write behavior.
 */
internal fun assembleWriteFragments(fragments: List<WriteFragment>): AssemblyResult {
    if (fragments.isEmpty()) return AssemblyResult.Success(emptyList())

    val writes =
        fragments
            .groupBy { it.charUuid }
            .map { (charUuid, charFragments) ->
                val sorted = charFragments.sortedBy { it.offset }
                val totalSize = sorted.maxOf { it.offset + it.bytes.size }
                if (totalSize > MAX_CHARACTERISTIC_VALUE_SIZE) {
                    return AssemblyResult.PayloadTooLarge(charUuid, totalSize)
                }
                val assembled = ByteArray(totalSize)
                for (fragment in sorted) {
                    fragment.bytes.copyInto(assembled, destinationOffset = fragment.offset)
                }
                AssembledWrite(charUuid, assembled)
            }
    return AssemblyResult.Success(writes)
}

/** Total projected byte footprint of buffered fragments (max end-offset across all entries). */
internal fun projectedBufferSize(
    buffer: List<WriteFragment>,
    newOffset: Int,
    newSize: Int,
): Int =
    maxOf(
        buffer.maxOfOrNull { it.offset + it.bytes.size } ?: 0,
        newOffset + newSize,
    )
