package com.atruedev.kmpble.dfu.internal

/**
 * Minimal CBOR encoder/decoder covering only the subset needed by MCUboot SMP.
 *
 * Supports major types 0 (unsigned int), 1 (negative int), 2 (byte string),
 * 3 (text string), and 5 (map). This is sufficient for all SMP request/response
 * payloads. See RFC 8949 for the full CBOR specification.
 */
internal object Cbor {

    fun encodeMap(entries: Map<Int, Any>): ByteArray {
        val buffer = CborBuffer()
        buffer.writeHead(5, entries.size.toLong())
        for ((key, value) in entries) {
            buffer.writeInt(key.toLong())
            buffer.writeValue(value)
        }
        return buffer.toByteArray()
    }

    fun encodeStringMap(entries: Map<String, Any>): ByteArray {
        val buffer = CborBuffer()
        buffer.writeHead(5, entries.size.toLong())
        for ((key, value) in entries) {
            buffer.writeValue(key)
            buffer.writeValue(value)
        }
        return buffer.toByteArray()
    }

    fun decodeMap(data: ByteArray): Map<Int, Any> = decodeIntKeyMap(data, 0).first

    fun decodeStringMap(data: ByteArray): Map<String, Any> = decodeStringKeyMap(data, 0).first

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
 * Growable byte buffer that avoids per-byte boxing overhead of MutableList<Byte>.
 * Doubles capacity on overflow, similar to ArrayList's growth strategy.
 */
private class CborBuffer(initialCapacity: Int = 64) {
    private var data = ByteArray(initialCapacity)
    private var position = 0

    fun writeByte(b: Byte) {
        ensureCapacity(1)
        data[position++] = b
    }

    fun writeBytes(src: ByteArray) {
        ensureCapacity(src.size)
        src.copyInto(data, position)
        position += src.size
    }

    fun toByteArray(): ByteArray = data.copyOfRange(0, position)

    // Supported value types: Int, Long, ByteArray, String, Boolean.
    // If this codec needs to support additional types, consider replacing
    // Any with a sealed CborValue hierarchy for compile-time safety.
    fun writeValue(value: Any) {
        when (value) {
            is Int -> writeInt(value.toLong())
            is Long -> writeInt(value)
            is ByteArray -> {
                writeHead(2, value.size.toLong())
                writeBytes(value)
            }
            is String -> {
                val bytes = value.encodeToByteArray()
                writeHead(3, bytes.size.toLong())
                writeBytes(bytes)
            }
            is Boolean -> writeByte(if (value) 0xF5.toByte() else 0xF4.toByte())
            else -> throw IllegalArgumentException("Unsupported CBOR value type: ${value::class.simpleName}")
        }
    }

    fun writeInt(value: Long) {
        if (value >= 0) {
            writeHead(0, value)
        } else {
            writeHead(1, -1 - value)
        }
    }

    fun writeHead(majorType: Int, value: Long) {
        val mt = majorType shl 5
        when {
            value < 24 -> writeByte((mt or value.toInt()).toByte())
            value <= 0xFF -> {
                writeByte((mt or 24).toByte())
                writeByte(value.toByte())
            }
            value <= 0xFFFF -> {
                writeByte((mt or 25).toByte())
                writeByte((value shr 8).toByte())
                writeByte(value.toByte())
            }
            value <= 0xFFFFFFFFL -> {
                writeByte((mt or 26).toByte())
                writeByte((value shr 24).toByte())
                writeByte((value shr 16).toByte())
                writeByte((value shr 8).toByte())
                writeByte(value.toByte())
            }
            else -> {
                writeByte((mt or 27).toByte())
                for (i in 7 downTo 0) writeByte((value shr (i * 8)).toByte())
            }
        }
    }

    private fun ensureCapacity(needed: Int) {
        val required = position + needed
        if (required <= data.size) return
        var newSize = data.size
        while (newSize < required) newSize *= 2
        data = data.copyOf(newSize)
    }
}
