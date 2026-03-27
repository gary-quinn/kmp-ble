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
import com.atruedev.kmpble.dfu.transport.sendCommandExpectingDisconnect
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

    override fun performDfu(
        transport: DfuTransport,
        firmware: FirmwarePackage,
        options: DfuOptions,
    ): Flow<DfuProgress> = flow {
        require(firmware is FirmwarePackage.McuBoot) {
            "McuBootDfuProtocol requires FirmwarePackage.McuBoot, got ${firmware::class.simpleName}"
        }

        var sequenceNumber = 0
        fun nextSequence(): Int =
            sequenceNumber.also { sequenceNumber = (it + 1) and 0xFF }

        suspend fun sendSmpCommand(
            group: Int,
            commandId: Int,
            cborPayload: ByteArray,
        ): ByteArray {
            val header = SmpHeader(
                op = SmpOp.WRITE,
                flags = 0,
                length = cborPayload.size,
                group = group,
                sequence = nextSequence(),
                commandId = commandId,
            )
            val packet = header.encode() + cborPayload
            return transport.sendCommand(packet)
        }

        emit(DfuProgress.Starting)

        val imageData = firmware.image
        val sha256 = Sha256.digest(imageData)
        val tracker = ThroughputTracker()
        val availableForData = transport.mtu - SmpHeader.SIZE

        var offset = 0L

        retryOnFailure(options.retryCount, options.retryDelay) {
            while (offset < imageData.size) {
                currentCoroutineContext().ensureActive()

                val isFirstChunk = offset == 0L
                val overhead = if (isFirstChunk) CBOR_OVERHEAD_FIRST else CBOR_OVERHEAD
                val chunkSize = maxOf(availableForData - overhead, 1)
                val end = min(offset + chunkSize, imageData.size.toLong()).toInt()
                val chunk = imageData.copyOfRange(offset.toInt(), end)

                val payload = buildUploadPayload(
                    imageIndex = firmware.imageIndex,
                    offset = offset,
                    data = chunk,
                    totalLen = if (isFirstChunk) imageData.size.toLong() else null,
                    sha256 = if (isFirstChunk) sha256 else null,
                )

                val response = sendSmpCommand(
                    SmpGroup.IMAGE_MGMT,
                    SmpCommand.IMAGE_UPLOAD,
                    payload,
                )

                val responseMap = parseSmpResponsePayload(response)
                val returnCode = (responseMap[SmpField.RC] as? Long) ?: 0L
                if (returnCode != 0L) {
                    throw DfuError.ProtocolError(
                        opcode = SmpCommand.IMAGE_UPLOAD,
                        resultCode = returnCode.toInt(),
                        message = "SMP upload rejected: rc=$returnCode",
                    )
                }

                val nextOffset = (responseMap[SmpField.OFF] as? Long) ?: (offset + chunk.size)
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

        val confirmPayload = Cbor.encodeStringMap(
            mapOf(SmpField.HASH to sha256, SmpField.CONFIRM to false),
        )
        val confirmResponse = sendSmpCommand(SmpGroup.IMAGE_MGMT, SmpCommand.IMAGE_STATE, confirmPayload)
        val confirmRc = (parseSmpResponsePayload(confirmResponse)[SmpField.RC] as? Long) ?: 0L
        if (confirmRc != 0L) {
            throw DfuError.ImageSlotError("Failed to mark image as test: rc=$confirmRc")
        }

        emit(DfuProgress.Completing)

        val resetPayload = Cbor.encodeStringMap(emptyMap())
        val resetPacket = SmpHeader(
            op = SmpOp.WRITE,
            flags = 0,
            length = resetPayload.size,
            group = SmpGroup.OS_MGMT,
            sequence = nextSequence(),
            commandId = SmpCommand.OS_RESET,
        ).encode() + resetPayload
        transport.sendCommandExpectingDisconnect(resetPacket)

        emit(DfuProgress.Completed)
    }

    private fun parseSmpResponsePayload(response: ByteArray): Map<String, Any> {
        if (response.size <= SmpHeader.SIZE) return emptyMap()
        return Cbor.decodeStringMap(response.copyOfRange(SmpHeader.SIZE, response.size))
    }

    internal companion object {
        // CBOR overhead for subsequent upload chunks (without "len" and "sha"):
        // map(3) header (1) + "image" key (6) + int value (3)
        // + "off" key (4) + int value (9) + "data" key (5) + byte-string header (3) = 31
        private const val CBOR_OVERHEAD = 31

        // First chunk includes "len" (4+9=13) and "sha" (4+35=39) fields
        private const val CBOR_OVERHEAD_FIRST = CBOR_OVERHEAD + 13 + 39

        internal fun buildUploadPayload(
            imageIndex: Int,
            offset: Long,
            data: ByteArray,
            totalLen: Long?,
            sha256: ByteArray?,
        ): ByteArray {
            val map = mutableMapOf<String, Any>(
                SmpField.IMAGE to imageIndex,
                SmpField.OFF to offset,
                SmpField.DATA to data,
            )
            if (totalLen != null) map[SmpField.LEN] = totalLen
            if (sha256 != null) map[SmpField.SHA] = sha256
            return Cbor.encodeStringMap(map)
        }
    }
}

/** SMP CBOR payload field names per the MCUboot SMP spec. */
private object SmpField {
    const val RC = "rc"
    const val OFF = "off"
    const val IMAGE = "image"
    const val DATA = "data"
    const val LEN = "len"
    const val SHA = "sha"
    const val HASH = "hash"
    const val CONFIRM = "confirm"
}
