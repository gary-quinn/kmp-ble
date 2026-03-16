package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.WriteType

internal object LargeWriteHandler {

    fun shouldChunk(data: ByteArray, maxLength: Int): Boolean = data.size > maxLength

    fun chunk(data: ByteArray, maxLength: Int): List<ByteArray> {
        if (data.size <= maxLength) return listOf(data)
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + maxLength, data.size)
            chunks.add(data.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    fun validateForWriteType(data: ByteArray, maxLength: Int, writeType: WriteType) {
        if (writeType == WriteType.Signed && data.size > maxLength) {
            throw MtuExceededException(attempted = data.size, maximum = maxLength)
        }
    }
}

internal class MtuExceededException(
    val attempted: Int,
    val maximum: Int,
) : Exception("Data size ($attempted) exceeds MTU limit ($maximum). Signed writes cannot be chunked.")
