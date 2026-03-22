package com.atruedev.kmpble.profiles.glucose

import com.atruedev.kmpble.profiles.parsing.BleByteReader

public data class GlucoseFeature(
    val lowBatteryDetectionSupported: Boolean,
    val sensorMalfunctionDetectionSupported: Boolean,
    val sensorSampleSizeSupported: Boolean,
    val sensorStripInsertionErrorDetectionSupported: Boolean,
    val sensorStripTypeErrorDetectionSupported: Boolean,
    val sensorResultHighLowDetectionSupported: Boolean,
    val sensorTemperatureHighLowDetectionSupported: Boolean,
    val sensorReadInterruptDetectionSupported: Boolean,
    val generalDeviceFaultSupported: Boolean,
    val timeFaultSupported: Boolean,
    val multipleBondSupported: Boolean,
)

public fun parseGlucoseFeature(data: ByteArray): GlucoseFeature? {
    if (data.size < 2) return null
    val reader = BleByteReader(data)
    val flags = reader.readUInt16()
    return GlucoseFeature(
        lowBatteryDetectionSupported = flags and 0x0001 != 0,
        sensorMalfunctionDetectionSupported = flags and 0x0002 != 0,
        sensorSampleSizeSupported = flags and 0x0004 != 0,
        sensorStripInsertionErrorDetectionSupported = flags and 0x0008 != 0,
        sensorStripTypeErrorDetectionSupported = flags and 0x0010 != 0,
        sensorResultHighLowDetectionSupported = flags and 0x0020 != 0,
        sensorTemperatureHighLowDetectionSupported = flags and 0x0040 != 0,
        sensorReadInterruptDetectionSupported = flags and 0x0080 != 0,
        generalDeviceFaultSupported = flags and 0x0100 != 0,
        timeFaultSupported = flags and 0x0200 != 0,
        multipleBondSupported = flags and 0x0400 != 0,
    )
}
