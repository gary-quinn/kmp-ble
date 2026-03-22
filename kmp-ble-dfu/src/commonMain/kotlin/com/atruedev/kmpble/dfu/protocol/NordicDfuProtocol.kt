package com.atruedev.kmpble.dfu.protocol

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.internal.ObjectTransfer
import com.atruedev.kmpble.dfu.internal.ThroughputTracker
import com.atruedev.kmpble.dfu.internal.retryOnFailure
import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.min

public class NordicDfuProtocol : DfuProtocol {

    override fun performDfu(
        transport: DfuTransport,
        firmware: FirmwarePackage,
        options: DfuOptions,
    ): Flow<DfuProgress> = flow {
        emit(DfuProgress.Starting)

        val transfer = ObjectTransfer(transport, options.prnInterval)
        transfer.setPrn(options.prnInterval)

        val tracker = ThroughputTracker()
        var totalSent = 0L

        transferInitPacket(transfer, firmware, options) { bytesSent ->
            totalSent = bytesSent.toLong()
            tracker.record(totalSent)
        }

        val initPacketSize = firmware.initPacket.size
        val objectInfo = transfer.select(DfuObjectType.DATA)
        val maxObjectSize = objectInfo.maxSize
        val firmwareData = firmware.firmware
        val totalObjects = (firmwareData.size + maxObjectSize - 1) / maxObjectSize

        var firmwareOffset = 0
        var objectIndex = 0

        while (firmwareOffset < firmwareData.size) {
            currentCoroutineContext().ensureActive()

            val chunkEnd = min(firmwareOffset + maxObjectSize, firmwareData.size)
            val chunk = firmwareData.copyOfRange(firmwareOffset, chunkEnd)

            emit(DfuProgress.Verifying(objectIndex))

            retryOnFailure(options.retryCount, options.retryDelay) {
                transfer.transferObject(DfuObjectType.DATA, chunk) { bytesSent ->
                    val absoluteSent = initPacketSize + firmwareOffset + bytesSent
                    totalSent = absoluteSent.toLong()
                    tracker.record(totalSent)

                    emit(
                        DfuProgress.Transferring(
                            currentObject = objectIndex,
                            totalObjects = totalObjects,
                            bytesSent = totalSent,
                            totalBytes = firmware.totalBytes,
                            bytesPerSecond = tracker.bytesPerSecond(),
                        )
                    )
                }
            }

            firmwareOffset = chunkEnd
            objectIndex++
        }

        emit(DfuProgress.Completing)
        emit(DfuProgress.Completed)
    }

    private suspend fun transferInitPacket(
        transfer: ObjectTransfer,
        firmware: FirmwarePackage,
        options: DfuOptions,
        onProgress: suspend (Int) -> Unit,
    ) {
        retryOnFailure(options.retryCount, options.retryDelay) {
            transfer.transferObject(DfuObjectType.COMMAND, firmware.initPacket) { bytesSent ->
                onProgress(bytesSent)
            }
        }
    }
}
