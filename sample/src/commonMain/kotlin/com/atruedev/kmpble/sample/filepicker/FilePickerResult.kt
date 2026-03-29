package com.atruedev.kmpble.sample.filepicker

data class FilePickerResult(
    val name: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilePickerResult) return false
        return name == other.name && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}
