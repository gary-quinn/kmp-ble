package com.atruedev.kmpble.dfu.internal

/**
 * Minimal CBOR encoder/decoder covering only the subset needed by MCUboot SMP.
 *
 * Supports major types 0 (unsigned int), 1 (negative int), 2 (byte string),
 * 3 (text string), and 5 (map). This is sufficient for all SMP request/response
 * payloads. See RFC 8949 for the full CBOR specification.
 *
 * Encoding uses a zero-copy two-pass strategy: pass 1 computes the exact output
 * size, a single ByteArray is allocated, pass 2 writes directly into it. No
 * intermediate buffers, no per-byte boxing, no final copy.
 */
internal object Cbor {

    fun encodeMap(entries: Map<Int, Any>): ByteArray {
        val size = sizeOfHead(entries.size.toLong()) +
            entries.entries.sumOf { (k, v) -> sizeOfInt(k.toLong()) + sizeOfValue(v) }
        val output = ByteArray(size)
        val writer = CborWriter(output)
        writer.writeHead(5, entries.size.toLong())
        for ((key, value) in entries) {
            writer.writeInt(key.toLong())
            writer.writeValue(value)
        }
        return output
    }

    fun encodeStringMap(entries: Map<String, Any>): ByteArray {
        val size = sizeOfHead(entries.size.toLong()) +
            entries.entries.sumOf { (k, v) -> sizeOfValue(k) + sizeOfValue(v) }
        val output = ByteArray(size)
        val writer = CborWriter(output)
        writer.writeHead(5, entries.size.toLong())
        for ((key, value) in entries) {
            writer.writeValue(key)
            writer.writeValue(value)
        }
        return output
    }

    fun decodeMap(data: ByteArray): Map<Int, Any> = decodeIntKeyMap(data, 0).first

    fun decodeStringMap(data: ByteArray): Map<String, Any> = decodeStringKeyMap(data, 0).first

    // -- Size computation (pass 1) --

    private fun sizeOfHead(value: Long): Int = when {
        value < 24 -> 1
        value <= 0xFF -> 2
        value <= 0xFFFF -> 3
        value <= 0xFFFFFFFFL -> 5
        else -> 9
    }

    private fun sizeOfInt(value: Long): Int =
        if (value >= 0) sizeOfHead(value) else sizeOfHead(-1 - value)

    // Supported value types: Int, Long, ByteArray, String, Boolean.
    // If this codec needs to support additional types, consider replacing
    // Any with a sealed CborValue hierarchy for compile-time safety.
    private fun sizeOfValue(value: Any): Int = when (value) {
        is Int -> sizeOfInt(value.toLong())
        is Long -> sizeOfInt(value)
        is ByteArray -> sizeOfHead(value.size.toLong()) + value.size
        is String -> {
            val byteLen = utf8ByteLength(value)
            sizeOfHead(byteLen.toLong()) + byteLen
        }
        is Boolean -> 1
        else -> throw IllegalArgumentException("Unsupported CBOR value type: ${value::class.simpleName}")
    }

    /** Compute UTF-8 encoded byte length without allocating a ByteArray. */
    private fun utf8ByteLength(s: String): Int {
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.code <= 0x7F -> bytes += 1
                c.code <= 0x7FF -> bytes += 2
                c.isHighSurrogate() -> {
                    // Surrogate pair → 4 UTF-8 bytes
                    bytes += 4
                    i++ // skip low surrogate
                }
                else -> bytes += 3
            }
            i++
        }
        return bytes
    }

    // -- Decoding --

    private fun decodeIntKeyMap(data: ByteArray, offset: Int): Pair<Map<Int, Any>, Int> {
        val (majorType, count, pos) = decodeHead(data, offset)
        require(majorType == 5) { "Expected CBOR map (major type 5), got $majorType" }
        val map = mutableMapOf<Int, Any>()
        var cursor = pos
        repeat(count.toInt()) {
            val (key, keyEnd) = decodeIntValue(data, cursor)
            val (value, valueEnd) = decodeAnyValue(data, keyEnd)
            map[key.toInt()] = value
            cursor = valueEnd
        }
        return map to cursor
    }

    private fun decodeStringKeyMap(data: ByteArray, offset: Int): Pair<Map<String, Any>, Int> {
        val (majorType, count, pos) = decodeHead(data, offset)
        require(majorType == 5) { "Expected CBOR map (major type 5), got $majorType" }
        val map = mutableMapOf<String, Any>()
        var cursor = pos
        repeat(count.toInt()) {
            val (key, keyEnd) = decodeAnyValue(data, cursor)
            val (value, valueEnd) = decodeAnyValue(data, keyEnd)
            map[key.toString()] = value
            cursor = valueEnd
        }
        return map to cursor
    }

    private data class DecodedHead(val majorType: Int, val value: Long, val nextOffset: Int)

    private fun decodeHead(data: ByteArray, offset: Int): DecodedHead {
        val initial = data[offset].toInt() and 0xFF
        val majorType = initial shr 5
        val additional = initial and 0x1F
        return when {
            additional < 24 -> DecodedHead(majorType, additional.toLong(), offset + 1)
            additional == 24 -> DecodedHead(majorType, (data[offset + 1].toInt() and 0xFF).toLong(), offset + 2)
            additional == 25 -> {
                val v = ((data[offset + 1].toInt() and 0xFF).toLong() shl 8) or
                    (data[offset + 2].toInt() and 0xFF).toLong()
                DecodedHead(majorType, v, offset + 3)
            }
            additional == 26 -> {
                val v = ((data[offset + 1].toInt() and 0xFF).toLong() shl 24) or
                    ((data[offset + 2].toInt() and 0xFF).toLong() shl 16) or
                    ((data[offset + 3].toInt() and 0xFF).toLong() shl 8) or
                    (data[offset + 4].toInt() and 0xFF).toLong()
                DecodedHead(majorType, v, offset + 5)
            }
            additional == 27 -> {
                var v = 0L
                for (i in 1..8) v = (v shl 8) or (data[offset + i].toInt() and 0xFF).toLong()
                DecodedHead(majorType, v, offset + 9)
            }
            else -> throw IllegalArgumentException("Unsupported CBOR additional info: $additional")
        }
    }

    private fun decodeIntValue(data: ByteArray, offset: Int): Pair<Long, Int> {
        val (majorType, value, nextOffset) = decodeHead(data, offset)
        return when (majorType) {
            0 -> value to nextOffset
            1 -> (-1 - value) to nextOffset
            else -> throw IllegalArgumentException("Expected CBOR integer, got major type $majorType")
        }
    }

    private fun decodeAnyValue(data: ByteArray, offset: Int): Pair<Any, Int> {
        val (majorType, value, nextOffset) = decodeHead(data, offset)
        return when (majorType) {
            0 -> value to nextOffset
            1 -> (-1 - value) to nextOffset
            2 -> {
                val bytes = data.copyOfRange(nextOffset, nextOffset + value.toInt())
                bytes to (nextOffset + value.toInt())
            }
            3 -> {
                val str = data.decodeToString(nextOffset, nextOffset + value.toInt())
                str to (nextOffset + value.toInt())
            }
            5 -> decodeStringKeyMap(data, offset)
            7 -> {
                val boolValue = when (value.toInt()) {
                    20 -> false
                    21 -> true
                    else -> throw IllegalArgumentException("Unsupported CBOR simple value: $value")
                }
                boolValue to nextOffset
            }
            else -> throw IllegalArgumentException("Unsupported CBOR major type: $majorType")
        }
    }
}

/**
 * Writes CBOR-encoded values directly into a pre-allocated ByteArray.
 * No intermediate buffers — every byte goes straight to the target.
 */
private class CborWriter(private val target: ByteArray) {
    private var pos = 0

    fun writeValue(value: Any) {
        when (value) {
            is Int -> writeInt(value.toLong())
            is Long -> writeInt(value)
            is ByteArray -> {
                writeHead(2, value.size.toLong())
                value.copyInto(target, pos)
                pos += value.size
            }
            is String -> {
                val bytes = value.encodeToByteArray()
                writeHead(3, bytes.size.toLong())
                bytes.copyInto(target, pos)
                pos += bytes.size
            }
            is Boolean -> {
                target[pos++] = if (value) 0xF5.toByte() else 0xF4.toByte()
            }
            else -> throw IllegalArgumentException("Unsupported CBOR value type: ${value::class.simpleName}")
        }
    }

    fun writeInt(value: Long) {
        if (value >= 0) writeHead(0, value) else writeHead(1, -1 - value)
    }

    fun writeHead(majorType: Int, value: Long) {
        val mt = majorType shl 5
        when {
            value < 24 -> {
                target[pos++] = (mt or value.toInt()).toByte()
            }
            value <= 0xFF -> {
                target[pos++] = (mt or 24).toByte()
                target[pos++] = value.toByte()
            }
            value <= 0xFFFF -> {
                target[pos++] = (mt or 25).toByte()
                target[pos++] = (value shr 8).toByte()
                target[pos++] = value.toByte()
            }
            value <= 0xFFFFFFFFL -> {
                target[pos++] = (mt or 26).toByte()
                target[pos++] = (value shr 24).toByte()
                target[pos++] = (value shr 16).toByte()
                target[pos++] = (value shr 8).toByte()
                target[pos++] = value.toByte()
            }
            else -> {
                target[pos++] = (mt or 27).toByte()
                for (i in 7 downTo 0) target[pos++] = (value shr (i * 8)).toByte()
            }
        }
    }
}
