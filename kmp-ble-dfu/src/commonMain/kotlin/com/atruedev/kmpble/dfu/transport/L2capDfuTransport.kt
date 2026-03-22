package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

internal class L2capDfuTransport(
    private val peripheral: Peripheral,
    private val channel: L2capChannel,
    private val commandTimeout: Duration,
) : DfuTransport {

    private val controlPoint = resolveControlPoint(peripheral)

    override val mtu: Int get() = channel.mtu

    override val notifications: Flow<ByteArray> =
        controlPointNotifications(peripheral, controlPoint)

    override suspend fun sendCommand(data: ByteArray): ByteArray =
        sendCommandViaGatt(peripheral, controlPoint, notifications, data, commandTimeout)

    override suspend fun sendData(data: ByteArray) {
        channel.write(data)
    }

    override fun close() {
        channel.close()
    }
}
