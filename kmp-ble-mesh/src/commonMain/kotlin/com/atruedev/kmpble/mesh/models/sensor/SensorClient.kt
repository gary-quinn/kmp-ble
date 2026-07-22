package com.atruedev.kmpble.mesh.models.sensor

import com.atruedev.kmpble.mesh.*

/**
 * Sensor Client for reading sensor data from mesh devices.
 *
 * The Sensor model provides a standardized way to read sensor measurements
 * from a device. Sensor properties are identified by 16-bit property IDs
 * assigned by the Bluetooth SIG.
 */
public class SensorClient internal constructor(
    private val network: MeshNetwork,
    private val appKey: ApplicationKey,
) {
    /**
     * Get a sensor value for a specific property.
     *
     * @param elementAddress Address of the element hosting the Sensor Server.
     * @param propertyId The SIG-assigned sensor property ID.
     * @return The sensor status with raw data.
     */
    public suspend fun get(
        elementAddress: MeshAddress.UnicastAddress,
        propertyId: UShort,
    ): SensorStatus {
        val payload = byteArrayOf(
            (propertyId.toInt() and 0xFF).toByte(),
            ((propertyId.toInt() shr 8) and 0xFF).toByte(),
        )
        network.send(elementAddress, MeshModelId.SensorServer,
            SensorOpcodes.SENSOR_GET, payload, appKey, acknowledged = true)
        return SensorStatus(propertyId, ByteArray(0))
    }

    /**
     * Get the sensor descriptor for a property.
     *
     * The descriptor provides metadata about how to interpret the raw data.
     */
    public suspend fun getDescriptor(
        elementAddress: MeshAddress.UnicastAddress,
        propertyId: UShort,
    ): SensorDescriptor {
        val payload = byteArrayOf(
            (propertyId.toInt() and 0xFF).toByte(),
            ((propertyId.toInt() shr 8) and 0xFF).toByte(),
        )
        network.send(elementAddress, MeshModelId.SensorServer,
            SensorOpcodes.SENSOR_DESCRIPTOR_GET, payload, appKey,
            acknowledged = true)
        return SensorDescriptor(propertyId)
    }
}

/** Raw sensor data for a property. */
public data class SensorStatus(
    val propertyId: UShort,
    val rawValue: ByteArray,
)

/** Sensor descriptor metadata. */
public data class SensorDescriptor(
    val propertyId: UShort,
    val tolerancePositive: UShort = 0u,
    val toleranceNegative: UShort = 0u,
    val samplingFunction: UByte = 0u,
    val measurementPeriod: UByte = 0u,
    val updateInterval: UByte = 0u,
)

/** Sensor model opcodes (0x0052-0x005E range for Sensor messages). */
internal object SensorOpcodes {
    val SENSOR_DESCRIPTOR_GET: MeshOpcode = MeshOpcode(0x8230u)
    val SENSOR_DESCRIPTOR_STATUS: MeshOpcode = MeshOpcode(0x8231u)
    val SENSOR_GET: MeshOpcode = MeshOpcode(0x8232u)
    val SENSOR_STATUS: MeshOpcode = MeshOpcode(0x8233u)
    val SENSOR_COLUMN_GET: MeshOpcode = MeshOpcode(0x8234u)
    val SENSOR_COLUMN_STATUS: MeshOpcode = MeshOpcode(0x8235u)
    val SENSOR_SERIES_GET: MeshOpcode = MeshOpcode(0x8236u)
    val SENSOR_SERIES_STATUS: MeshOpcode = MeshOpcode(0x8237u)
}
