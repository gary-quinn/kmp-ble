package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

internal class GattDfuTransport(
    private val peripheral: Peripheral,
    private val commandTimeout: Duration,
) : DfuTransport {

    private val controlPoint = resolveControlPoint(peripheral)
    private val dataPacket = resolveDataPacket(peripheral)

    override val mtu: Int get() = peripheral.maximumWriteValueLength.value

    override val notifications: Flow<ByteArray> =
        controlPointNotifications(peripheral, controlPoint)

    override suspend fun sendCommand(data: ByteArray): ByteArray =
        sendCommandViaGatt(peripheral, controlPoint, notifications, data, commandTimeout)

    override suspend fun sendData(data: ByteArray) {
        peripheral.write(dataPacket, data, WriteType.WithoutResponse)
    }

    override fun close() {}
}
