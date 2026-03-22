package com.atruedev.kmpble.profiles.heartrate

public enum class BodySensorLocation {
    Other,
    Chest,
    Wrist,
    Finger,
    Hand,
    EarLobe,
    Foot;

    public companion object {
        private val mapping = mapOf(
            0 to Other, 1 to Chest, 2 to Wrist, 3 to Finger,
            4 to Hand, 5 to EarLobe, 6 to Foot,
        )

        public fun fromByte(value: Int): BodySensorLocation? = mapping[value]
    }
}

public fun parseBodySensorLocation(data: ByteArray): BodySensorLocation? {
    if (data.isEmpty()) return null
    return BodySensorLocation.fromByte(data[0].toInt() and 0xFF)
}
