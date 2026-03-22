package com.atruedev.kmpble.codec

/** Big-endian 16-bit unsigned int encoder for tests. */
internal val TestIntEncoder = BleEncoder<Int> { value ->
    byteArrayOf((value shr 8).toByte(), value.toByte())
}

/** Big-endian 16-bit unsigned int decoder (ByteArray) for tests. */
internal val TestIntDecoder = BleDecoder<Int> { data ->
    (data[0].toInt() and 0xFF shl 8) or (data[1].toInt() and 0xFF)
}

/** Big-endian 16-bit unsigned int decoder (BleData) for tests. */
internal val TestIntBleDataDecoder = BleDataDecoder<Int> { data ->
    (data[0].toInt() and 0xFF shl 8) or (data[1].toInt() and 0xFF)
}

/** UTF-8 string encoder for tests. */
internal val TestStringEncoder = BleEncoder<String> { it.encodeToByteArray() }

/** UTF-8 string decoder for tests. */
internal val TestStringDecoder = BleDecoder<String> { it.decodeToString() }
