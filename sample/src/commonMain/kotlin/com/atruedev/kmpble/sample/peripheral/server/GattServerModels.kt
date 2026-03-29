package com.atruedev.kmpble.sample.peripheral.server

sealed interface ServerState {
    data object Stopped : ServerState

    data object Running : ServerState

    data class Error(
        val message: String,
    ) : ServerState
}

enum class CharProperty { READ, WRITE, NOTIFY }

data class ServerCharConfig(
    val uuid: String = "",
    val label: String = "",
    val properties: Set<CharProperty> = emptySet(),
    val readValue: ByteArray = byteArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServerCharConfig) return false
        return uuid == other.uuid &&
            label == other.label &&
            properties == other.properties &&
            readValue.contentEquals(other.readValue)
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + readValue.contentHashCode()
        return result
    }
}

data class ServerServiceConfig(
    val uuid: String = "",
    val characteristics: List<ServerCharConfig> = emptyList(),
)

data class GattServerUiState(
    val state: ServerState = ServerState.Stopped,
    val services: List<ServerServiceConfig> = emptyList(),
    val connectedClients: Int = 0,
    val error: String? = null,
)

enum class ServerPreset(
    val label: String,
    val services: List<ServerServiceConfig>,
) {
    HEART_RATE_SENSOR(
        label = "Heart Rate Sensor",
        services =
            listOf(
                ServerServiceConfig(
                    uuid = "0000180d-0000-1000-8000-00805f9b34fb",
                    characteristics =
                        listOf(
                            ServerCharConfig(
                                uuid = "00002a37-0000-1000-8000-00805f9b34fb",
                                label = "Heart Rate Measurement",
                                properties = setOf(CharProperty.READ, CharProperty.NOTIFY),
                                readValue = byteArrayOf(0x00, 72), // flags=0, bpm=72
                            ),
                            ServerCharConfig(
                                uuid = "00002a38-0000-1000-8000-00805f9b34fb",
                                label = "Body Sensor Location",
                                properties = setOf(CharProperty.READ),
                                readValue = byteArrayOf(0x01), // 1 = Chest
                            ),
                        ),
                ),
            ),
    ),
    BATTERY_SERVICE(
        label = "Battery Service",
        services =
            listOf(
                ServerServiceConfig(
                    uuid = "0000180f-0000-1000-8000-00805f9b34fb",
                    characteristics =
                        listOf(
                            ServerCharConfig(
                                uuid = "00002a19-0000-1000-8000-00805f9b34fb",
                                label = "Battery Level",
                                properties = setOf(CharProperty.READ, CharProperty.NOTIFY),
                                readValue = byteArrayOf(85), // 85%
                            ),
                        ),
                ),
            ),
    ),
    UART_SERVICE(
        label = "Nordic UART (NUS)",
        services =
            listOf(
                ServerServiceConfig(
                    uuid = "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
                    characteristics =
                        listOf(
                            ServerCharConfig(
                                uuid = "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
                                label = "RX (Write)",
                                properties = setOf(CharProperty.WRITE),
                            ),
                            ServerCharConfig(
                                uuid = "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
                                label = "TX (Notify)",
                                properties = setOf(CharProperty.NOTIFY),
                            ),
                        ),
                ),
            ),
    ),
    DEVICE_INFO(
        label = "Device Information",
        services =
            listOf(
                ServerServiceConfig(
                    uuid = "0000180a-0000-1000-8000-00805f9b34fb",
                    characteristics =
                        listOf(
                            ServerCharConfig(
                                uuid = "00002a29-0000-1000-8000-00805f9b34fb",
                                label = "Manufacturer Name",
                                properties = setOf(CharProperty.READ),
                                readValue = "BLE Toolkit".encodeToByteArray(),
                            ),
                            ServerCharConfig(
                                uuid = "00002a24-0000-1000-8000-00805f9b34fb",
                                label = "Model Number",
                                properties = setOf(CharProperty.READ),
                                readValue = "v1.0".encodeToByteArray(),
                            ),
                        ),
                ),
            ),
    ),
}
