package com.atruedev.kmpble

/**
 * Zero-copy wrapper around platform-native byte buffers.
 *
 * - Android: wraps `ByteArray` directly (already native representation)
 * - iOS: wraps `NSData` — no memcpy on construction
 *
 * Provides indexed read access and slicing without copying. Call [toByteArray]
 * only when you need a mutable copy (e.g., for protocol parsing in consumer code).
 */
public expect class BleData {
    /** Number of bytes. */
    public val size: Int

    /** Read a single byte at [index]. */
    public operator fun get(index: Int): Byte

    /** Copy contents into a new ByteArray. Use sparingly — this allocates. */
    public fun toByteArray(): ByteArray

    /** Zero-copy slice from [fromIndex] (inclusive) to [toIndex] (exclusive). */
    public fun slice(
        fromIndex: Int,
        toIndex: Int,
    ): BleData

    /** Content-based equality. */
    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int
}

/** Create [BleData] from a ByteArray (copies on iOS, wraps on Android). */
public expect fun BleData(bytes: ByteArray): BleData

/** Empty [BleData] singleton. */
public expect fun emptyBleData(): BleData
