package com.atruedev.kmpble.dfu.protocol

@OptIn(ExperimentalUnsignedTypes::class)
internal object Crc32 {

    private val table: UIntArray = UIntArray(256) { i ->
        var crc = i.toUInt()
        repeat(8) {
            crc = if (crc and 1u != 0u) (crc shr 1) xor 0xEDB88320u else crc shr 1
        }
        crc
    }

    fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): UInt =
        update(0xFFFFFFFFu, data, offset, length) xor 0xFFFFFFFFu

    fun update(crc: UInt, data: ByteArray, offset: Int = 0, length: Int = data.size - offset): UInt {
        var acc = crc
        for (i in offset until offset + length) {
            val index = ((acc xor data[i].toUInt()) and 0xFFu).toInt()
            acc = table[index] xor (acc shr 8)
        }
        return acc
    }

    fun resume(previousCrc: UInt): UInt = previousCrc xor 0xFFFFFFFFu

    fun finalize(runningCrc: UInt): UInt = runningCrc xor 0xFFFFFFFFu
}
