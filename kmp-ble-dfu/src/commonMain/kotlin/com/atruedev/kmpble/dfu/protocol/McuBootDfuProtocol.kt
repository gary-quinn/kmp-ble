package com.atruedev.kmpble.dfu.protocol

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.internal.Cbor
import com.atruedev.kmpble.dfu.internal.Sha256
import com.atruedev.kmpble.dfu.internal.ThroughputTracker
import com.atruedev.kmpble.dfu.internal.retryOnFailure
import com.atruedev.kmpble.dfu.protocol.smp.SmpCommand
import com.atruedev.kmpble.dfu.protocol.smp.SmpGroup
import com.atruedev.kmpble.dfu.protocol.smp.SmpHeader
import com.atruedev.kmpble.dfu.protocol.smp.SmpOp
import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.min

/**
 * MCUboot DFU protocol implementation using SMP (Simple Management Protocol).
 *
 * Transfers a MCUboot firmware image via CBOR-encoded SMP commands over BLE.
 * Supports offset-based resume, SHA256 verification, and the MCUboot
 * test/confirm image activation workflow.
 *
 * ## Transfer flow
 * 1. Query current image state (slot availability)
 * 2. Upload firmware in chunks with offset-based flow control
 * 3. Mark uploaded image as "test" (pending boot)
 * 4. Reset device to boot into new image
 *
 * @see DfuProtocol
 */
public class McuBootDfuProtocol : DfuProtocol {

    private var sequenceNumber: Int = 0

    override fun performDfu(
        transport: DfuTransport,
        firmware: FirmwarePackage,
        options: DfuOptions,
    ): Flow<DfuProgress> = flow {
        require(firmware is FirmwarePackage.McuBoot) {
            "McuBootDfuProtocol requires FirmwarePackage.McuBoot, got ${firmware::class.simpleName}"
        }

        emit(DfuProgress.Starting)

        val imageData = firmware.image
        val sha256 = Sha256.digest(imageData)
        val tracker = ThroughputTracker()
        val chunkSize = maxOf(transport.mtu - SmpHeader.SIZE - CBOR_OVERHEAD, 1)

        var offset = 0L

        retryOnFailure(options.retryCount, options.retryDelay) {
            while (offset < imageData.size) {
                currentCoroutineContext().ensureActive()

                val end = min(offset + chunkSize, imageData.size.toLong()).toInt()
                val chunk = imageData.copyOfRange(offset.toInt(), end)

                val payload = buildUploadPayload(
                    imageIndex = firmware.imageIndex,
                    offset = offset,
                    data = chunk,
                    totalLen = if (offset == 0L) imageData.size.toLong() else null,
                    sha256 = if (offset == 0L) sha256 else null,
                )

                val response = sendSmpCommand(
                    transport,
                    SmpGroup.IMAGE_MGMT,
                    SmpCommand.IMAGE_UPLOAD,
                    payload,
                )

                val responseMap = parseSmpResponsePayload(response)
                val returnCode = (responseMap["rc"] as? Long) ?: 0L
                if (returnCode != 0L) {
                    throw DfuError.ProtocolError(
                        opcode = SmpCommand.IMAGE_UPLOAD,
                        resultCode = returnCode.toInt(),
                        message = "SMP upload rejected: rc=$returnCode",
                    )
                }

                val nextOffset = (responseMap["off"] as? Long) ?: (offset + chunk.size)
                offset = nextOffset

                tracker.record(offset)
                emit(
                    DfuProgress.Transferring(
                        currentObject = 0,
                        totalObjects = 1,
                        bytesSent = offset,
                        totalBytes = firmware.totalBytes,
                        bytesPerSecond = tracker.bytesPerSecond(),
                    ),
                )
            }
        }

        emit(DfuProgress.Verifying(0))

        confirmImage(transport, sha256)

        emit(DfuProgress.Completing)

        resetDevice(transport)

        emit(DfuProgress.Completed)
    }

    private suspend fun confirmImage(transport: DfuTransport, sha256: ByteArray) {
        val payload = Cbor.encodeStringMap(mapOf("hash" to sha256, "confirm" to false))
        val response = sendSmpCommand(transport, SmpGroup.IMAGE_MGMT, SmpCommand.IMAGE_STATE, payload)
        val responseMap = parseSmpResponsePayload(response)
        val rc = (responseMap["rc"] as? Long) ?: 0L
        if (rc != 0L) {
            throw DfuError.ImageSlotError("Failed to mark image as test: rc=$rc")
        }
    }

    private suspend fun resetDevice(transport: DfuTransport) {
        val payload = Cbor.encodeStringMap(emptyMap())
        try {
            sendSmpCommand(transport, SmpGroup.OS_MGMT, SmpCommand.OS_RESET, payload)
        } catch (_: DfuError.Timeout) {
            // Expected: device resets and disconnects before responding
        }
    }

    private fun parseSmpResponsePayload(response: ByteArray): Map<String, Any> {
        if (response.size <= SmpHeader.SIZE) return emptyMap()
        return Cbor.decodeStringMap(response.copyOfRange(SmpHeader.SIZE, response.size))
    }

    private suspend fun sendSmpCommand(
        transport: DfuTransport,
        group: Int,
        commandId: Int,
        cborPayload: ByteArray,
    ): ByteArray {
        val seq = nextSequence()
        val header = SmpHeader(
            op = SmpOp.WRITE,
            flags = 0,
            length = cborPayload.size,
            group = group,
            sequence = seq,
            commandId = commandId,
        )
        val packet = header.encode() + cborPayload
        return transport.sendCommand(packet)
    }

    private fun nextSequence(): Int {
        val seq = sequenceNumber
        sequenceNumber = (sequenceNumber + 1) and 0xFF
        return seq
    }

    internal companion object {
        private const val CBOR_OVERHEAD = 40

        internal fun buildUploadPayload(
            imageIndex: Int,
            offset: Long,
            data: ByteArray,
            totalLen: Long?,
            sha256: ByteArray?,
        ): ByteArray {
            val map = mutableMapOf<String, Any>(
                "image" to imageIndex,
                "off" to offset,
                "data" to data,
            )
            if (totalLen != null) map["len"] = totalLen
            if (sha256 != null) map["sha"] = sha256
            return Cbor.encodeStringMap(map)
        }
    }
}
