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

/**
 * SMP (Simple Management Protocol) transport for MCUboot DFU.
 *
 * SMP uses a single characteristic for both request and response. Responses
 * may be fragmented across multiple BLE notifications when the payload exceeds
 * the MTU. This transport reassembles fragmented responses based on the SMP
 * header length field before returning to the caller.
 */
internal class SmpTransport(
    private val peripheral: Peripheral,
    private val commandTimeout: Duration,
) : DfuTransport {

    private val smpChar: Characteristic = resolveSmpCharacteristic(peripheral)

    override val mtu: Int get() = peripheral.maximumWriteValueLength.value

    override val notifications: Flow<ByteArray> =
        peripheral.observeValues(smpChar, BackpressureStrategy.Unbounded)

    override suspend fun sendCommand(data: ByteArray): ByteArray = coroutineScope {
        val notificationChannel = notifications.produceIn(this)
        try {
            peripheral.write(smpChar, data, WriteType.WithoutResponse)
            withTimeout(commandTimeout) {
                reassembleSmpResponse(notificationChannel)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw DfuError.Timeout("No SMP response within $commandTimeout")
        } finally {
            notificationChannel.cancel()
        }
    }

    override suspend fun sendData(data: ByteArray) {
        peripheral.write(smpChar, data, WriteType.WithoutResponse)
    }

    override fun close() {}

    companion object {
        private const val SMP_HEADER_SIZE = 8
        private const val SMP_LENGTH_OFFSET = 2

        private fun resolveSmpCharacteristic(peripheral: Peripheral): Characteristic =
            peripheral.findCharacteristic(SmpUuids.SMP_SERVICE, SmpUuids.SMP_CHARACTERISTIC)
                ?: throw DfuError.ServiceNotFound("SMP service not found on peripheral")

        /**
         * Reassemble a potentially fragmented SMP response.
         *
         * The SMP header's length field (bytes 2-3, big-endian) indicates the
         * payload length after the 8-byte header. If the total expected size
         * exceeds a single notification, subsequent notifications are concatenated.
         */
        private suspend fun reassembleSmpResponse(
            channel: kotlinx.coroutines.channels.ReceiveChannel<ByteArray>,
        ): ByteArray {
            val first = channel.receive()
            if (first.size < SMP_HEADER_SIZE) return first

            val payloadLength = ((first[SMP_LENGTH_OFFSET].toInt() and 0xFF) shl 8) or
                (first[SMP_LENGTH_OFFSET + 1].toInt() and 0xFF)
            val expectedTotal = SMP_HEADER_SIZE + payloadLength

            if (first.size >= expectedTotal) return first

            val buffer = ByteArray(expectedTotal)
            first.copyInto(buffer)
            var received = first.size

            while (received < expectedTotal) {
                val fragment = channel.receive()
                fragment.copyInto(buffer, received)
                received += fragment.size
            }

            return buffer
        }
    }
}
