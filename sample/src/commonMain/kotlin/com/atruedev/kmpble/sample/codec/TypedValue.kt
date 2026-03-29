package com.atruedev.kmpble.sample.codec

sealed interface TypedValue {
    data class Parsed<T>(
        val value: T,
        val raw: ByteArray,
    ) : TypedValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Parsed<*>) return false
            return value == other.value && raw.contentEquals(other.raw)
        }

        override fun hashCode(): Int = 31 * (value?.hashCode() ?: 0) + raw.contentHashCode()
    }

    data class Raw(
        val data: ByteArray,
    ) : TypedValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Raw) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }
}
