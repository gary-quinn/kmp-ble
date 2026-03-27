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
        val beginResponse = transport.sendCommand(encodeOtaBegin(firmwareData.size))
        validateResponse(beginResponse, "OTA Begin")

        val tracker = ThroughputTracker()
        val chunkSize = transport.mtu
        var offset = 0

        retryOnFailure(options.retryCount, options.retryDelay) {
            while (offset < firmwareData.size) {
                currentCoroutineContext().ensureActive()

                val end = min(offset + chunkSize, firmwareData.size)
                val chunk = firmwareData.copyOfRange(offset, end)
                transport.sendData(chunk)
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
        }

        emit(DfuProgress.Verifying(0))

        val sha256 = Sha256.digest(firmwareData)
        val endResponse = transport.sendCommand(encodeOtaEnd(sha256))
        validateResponse(endResponse, "OTA End")

        emit(DfuProgress.Completing)

        try {
            transport.sendCommand(encodeOtaReboot())
        } catch (_: DfuError.Timeout) {
            // Expected: device reboots and disconnects before responding
        }

        emit(DfuProgress.Completed)
    }

    private fun validateResponse(response: ByteArray, commandName: String) {
        if (response.isEmpty()) {
            throw DfuError.ProtocolError(
                opcode = 0,
                resultCode = -1,
                message = "$commandName: empty response",
            )
        }
        if (response[0] != EspOtaResult.SUCCESS) {
            throw DfuError.ProtocolError(
                opcode = response.getOrElse(1) { 0 }.toInt(),
                resultCode = response[0].toInt(),
                message = "$commandName failed: result=${response[0]}",
            )
        }
    }
}
