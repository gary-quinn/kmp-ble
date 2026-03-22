package com.atruedev.kmpble.dfu.protocol

internal data class DfuObjectInfo(
    val maxSize: Int,
    val offset: Int,
    val crc32: UInt,
)

internal data class DfuChecksum(
    val offset: Int,
    val crc32: UInt,
)
