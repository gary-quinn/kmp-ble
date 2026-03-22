package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal fun resolveControlPoint(peripheral: Peripheral): Characteristic =
    peripheral.findCharacteristic(DfuUuids.DFU_SERVICE, DfuUuids.DFU_CONTROL_POINT)
        ?: throw DfuError.CharacteristicNotFound("DFU Control Point")

internal fun resolveDataPacket(peripheral: Peripheral): Characteristic =
    peripheral.findCharacteristic(DfuUuids.DFU_SERVICE, DfuUuids.DFU_PACKET)
        ?: throw DfuError.CharacteristicNotFound("DFU Packet")

internal fun controlPointNotifications(
    peripheral: Peripheral,
    controlPoint: Characteristic,
): Flow<ByteArray> =
    peripheral.observeValues(controlPoint, BackpressureStrategy.Unbounded)

internal suspend fun sendCommandViaGatt(
    peripheral: Peripheral,
    controlPoint: Characteristic,
    notifications: Flow<ByteArray>,
    data: ByteArray,
    timeout: Duration,
): ByteArray = coroutineScope {
    val notificationChannel = notifications.produceIn(this)
    try {
        peripheral.write(controlPoint, data, WriteType.WithResponse)
        withTimeout(timeout) {
            notificationChannel.receive()
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw DfuError.Timeout("No response within $timeout for command 0x${data.firstOrNull()?.toString(16) ?: "??"}")
    } finally {
        notificationChannel.cancel()
    }
}
