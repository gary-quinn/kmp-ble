package com.atruedev.kmpble.dfu.protocol

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.internal.Sha256
import com.atruedev.kmpble.dfu.internal.ThroughputTracker
import com.atruedev.kmpble.dfu.internal.retryOnFailure
import com.atruedev.kmpble.dfu.protocol.esp.EspOtaOpcode
import com.atruedev.kmpble.dfu.protocol.esp.EspOtaResult
import com.atruedev.kmpble.dfu.protocol.esp.encodeOtaBegin
import com.atruedev.kmpble.dfu.protocol.esp.encodeOtaEnd
import com.atruedev.kmpble.dfu.protocol.esp.encodeOtaReboot
import com.atruedev.kmpble.dfu.transport.DfuTransport
import com.atruedev.kmpble.dfu.transport.sendCommandExpectingDisconnect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.min

/**
 * Espressif ESP-IDF OTA DFU protocol implementation.
 *
 * Transfers firmware by writing chunks to the OTA data characteristic,
 * then verifies integrity via SHA256 hash and switches the boot partition.
 *
 * ## Transfer flow
 * 1. Send OTA Begin command with firmware size
 * 2. Write firmware in MTU-sized chunks
 * 3. Send OTA End with SHA256 hash for verification
 * 4. Send reboot command to boot into new firmware
 *
 * @see DfuProtocol
 */
public class EspOtaDfuProtocol : DfuProtocol {

    override fun performDfu(
        transport: DfuTransport,
        firmware: FirmwarePackage,
        options: DfuOptions,
    ): Flow<DfuProgress> = flow {
        require(firmware is FirmwarePackage.EspOta) {
            "EspOtaDfuProtocol requires FirmwarePackage.EspOta, got ${firmware::class.simpleName}"
        }

        emit(DfuProgress.Starting)

        val firmwareData = firmware.firmware
        val tracker = ThroughputTracker()
        val chunkSize = transport.mtu
        val sha256 = Sha256.digest(firmwareData)

        // Pre-allocate a single reusable buffer for full-sized chunks.
        // Only the final (potentially shorter) chunk allocates a fresh array.
        val chunkBuffer = ByteArray(chunkSize)

        // ESP OTA does not support resume — a fresh OTA Begin is required on
        // each attempt because the device resets its OTA state on error.
        // Hash verification and OTA End are inside retry scope: if the
        // device drops mid-verify, the entire transfer retries from scratch.
        retryOnFailure(options.retryCount, options.retryDelay) {
            val beginResponse = transport.sendCommand(encodeOtaBegin(firmwareData.size))
            validateResponse(beginResponse, EspOtaOpcode.OTA_BEGIN.toInt(), "OTA Begin")

            var offset = 0

            while (offset < firmwareData.size) {
                currentCoroutineContext().ensureActive()

                val end = min(offset + chunkSize, firmwareData.size)
                val len = end - offset
                firmwareData.copyInto(chunkBuffer, 0, offset, end)
                if (len == chunkSize) {
                    transport.sendData(chunkBuffer)
                } else {
                    transport.sendData(chunkBuffer.copyOfRange(0, len))
                }
                offset = end

                tracker.record(offset.toLong())
                emit(
                    DfuProgress.Transferring(
                        currentObject = 0,
                        totalObjects = 1,
                        bytesSent = offset.toLong(),
                        totalBytes = firmware.totalBytes,
                        bytesPerSecond = tracker.bytesPerSecond(),
                    ),
                )
            }

            emit(DfuProgress.Verifying(0))

            val endResponse = transport.sendCommand(encodeOtaEnd(sha256))
            validateResponse(endResponse, EspOtaOpcode.OTA_END.toInt(), "OTA End")
        }

        emit(DfuProgress.Completing)

        transport.sendCommandExpectingDisconnect(encodeOtaReboot())

        emit(DfuProgress.Completed)
    }

    private fun validateResponse(response: ByteArray, opcode: Int, commandName: String) {
        if (response.isEmpty()) {
            throw DfuError.ProtocolError(
                opcode = opcode,
                resultCode = -1,
                message = "$commandName: empty response",
            )
        }
        if (response[0] != EspOtaResult.SUCCESS) {
            throw DfuError.ProtocolError(
                opcode = opcode,
                resultCode = response[0].toInt(),
                message = "$commandName failed: result=${response[0]}",
            )
        }
    }
}
