package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuTransportConfig
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi

/**
 * Espressif ESP-IDF OTA transport.
 *
 * Commands are sent to the OTA control characteristic (write with response),
 * firmware data is written to the OTA data characteristic (write without
 * response for throughput). Notifications on the control characteristic
 * deliver command responses.
 */
@OptIn(ExperimentalUuidApi::class)
internal class EspOtaTransport(
    private val peripheral: Peripheral,
    config: DfuTransportConfig.EspOta,
    private val commandTimeout: Duration,
) : DfuTransport {

    private val serviceUuid = config.serviceUuid ?: EspOtaUuids.OTA_SERVICE
    private val controlChar: Characteristic = resolveCharacteristic("OTA Control", config.controlUuid ?: EspOtaUuids.OTA_CONTROL)
    private val dataChar: Characteristic = resolveCharacteristic("OTA Data", config.dataUuid ?: EspOtaUuids.OTA_DATA)

    override val mtu: Int get() = peripheral.maximumWriteValueLength.value

    // OTA commands are request-response: only the latest notification matters.
    // Latest drops stale values if the consumer is slow, preventing unbounded growth.
    override val notifications: Flow<ByteArray> =
        peripheral.observeValues(controlChar, BackpressureStrategy.Latest)

    override suspend fun sendCommand(data: ByteArray): ByteArray = coroutineScope {
        val notificationChannel = notifications.produceIn(this)
        try {
            peripheral.write(controlChar, data, WriteType.WithResponse)
            withTimeout(commandTimeout) {
                notificationChannel.receive()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw DfuError.Timeout("No ESP OTA response within $commandTimeout")
        } finally {
            notificationChannel.cancel()
        }
    }

    override suspend fun sendData(data: ByteArray) {
        peripheral.write(dataChar, data, WriteType.WithoutResponse)
    }

    override fun close() {}

    private fun resolveCharacteristic(name: String, uuid: kotlin.uuid.Uuid): Characteristic =
        peripheral.findCharacteristic(serviceUuid, uuid)
            ?: throw DfuError.CharacteristicNotFound(name)
}
