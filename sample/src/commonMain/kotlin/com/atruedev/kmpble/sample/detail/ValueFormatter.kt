package com.atruedev.kmpble.sample.detail

object ValueFormatter {
    fun format(
        data: ByteArray,
        format: DisplayFormat,
    ): String =
        when (format) {
            DisplayFormat.HEX -> formatHex(data)
            DisplayFormat.UTF8 -> data.decodeToString()
            DisplayFormat.DECIMAL -> data.joinToString(" ") { (it.toInt() and 0xFF).toString() }
            DisplayFormat.BINARY ->
                data.joinToString(" ") {
                    (it.toInt() and 0xFF).toString(2).padStart(8, '0')
                }
        }

    fun formatHex(data: ByteArray): String =
        data.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

    fun parseHex(input: String): ByteArray? {
        val cleaned = input.replace(" ", "").replace(":", "").replace("-", "")
        if (cleaned.length % 2 != 0) return null
        return try {
            cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: NumberFormatException) {
            null
        }
    }
}
