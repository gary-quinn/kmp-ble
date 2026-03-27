package com.atruedev.kmpble.dfu.protocol

import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.internal.Cbor
import com.atruedev.kmpble.dfu.protocol.smp.SmpHeader
import com.atruedev.kmpble.dfu.protocol.smp.SmpOp
import com.atruedev.kmpble.dfu.testing.FakeDfuTransport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class McuBootDfuProtocolTest {

    private val protocol = McuBootDfuProtocol()

    @Test
    fun happyPathSmallImage() = runTest {
        val transport = FakeDfuTransport(mtu = 256)
        val imageData = buildMcuBootImage(64)
        val firmware = FirmwarePackage.McuBoot(imageData, imageIndex = 0)

        launch {
            // Upload response: next offset = full size (single chunk)
            transport.enqueueResponse(smpResponse(mapOf("rc" to 0, "off" to imageData.size.toLong())))
            // Confirm image response
            transport.enqueueResponse(smpResponse(mapOf("rc" to 0)))
            // Reset response
            transport.enqueueResponse(smpResponse(mapOf("rc" to 0)))
        }

        val states = protocol.performDfu(transport, firmware, DfuOptions()).toList()

        assertIs<DfuProgress.Starting>(states.first())
        assertTrue(states.any { it is DfuProgress.Transferring })
        assertIs<DfuProgress.Verifying>(states.first { it is DfuProgress.Verifying })
        assertIs<DfuProgress.Completing>(states.first { it is DfuProgress.Completing })
        assertIs<DfuProgress.Completed>(states.last())
    }

    @Test
    fun multiChunkUpload() = runTest {
        val transport = FakeDfuTransport(mtu = 100)
        val imageData = buildMcuBootImage(200)
        val firmware = FirmwarePackage.McuBoot(imageData)

        launch {
            // Use the worst-case (first chunk) overhead to conservatively simulate
            // chunk boundaries. The protocol uses a smaller overhead for subsequent
            // chunks, but the server's "off" response controls progression either way.
            val chunkSize = maxOf(transport.mtu - SmpHeader.SIZE - FIRST_CHUNK_OVERHEAD, 1)
            var offset = 0L
            while (offset < imageData.size) {
                offset = minOf(offset + chunkSize, imageData.size.toLong())
                transport.enqueueResponse(smpResponse(mapOf("rc" to 0, "off" to offset)))
            }
            transport.enqueueResponse(smpResponse(mapOf("rc" to 0)))
            transport.enqueueResponse(smpResponse(mapOf("rc" to 0)))
        }

        val states = protocol.performDfu(transport, firmware, DfuOptions()).toList()

        val transferringStates = states.filterIsInstance<DfuProgress.Transferring>()
        assertTrue(transferringStates.size > 1, "Expected multiple transfer progress updates")
        assertIs<DfuProgress.Completed>(states.last())
    }

    @Test
    fun progressFractionIncreasesMonotonically() = runTest {
        val transport = FakeDfuTransport(mtu = 100)
        val imageData = buildMcuBootImage(300)
        val firmware = FirmwarePackage.McuBoot(imageData)

        launch {
            val chunkSize = maxOf(transport.mtu - SmpHeader.SIZE - FIRST_CHUNK_OVERHEAD, 1)
            var offset = 0L
            while (offset < imageData.size) {
                offset = minOf(offset + chunkSize, imageData.size.toLong())
                transport.enqueueResponse(smpResponse(mapOf("rc" to 0, "off" to offset)))
            }
            transport.enqueueResponse(smpResponse(mapOf("rc" to 0)))
            transport.enqueueResponse(smpResponse(mapOf("rc" to 0)))
        }

        val states = protocol.performDfu(transport, firmware, DfuOptions()).toList()
        val fractions = states.filterIsInstance<DfuProgress.Transferring>().map { it.fraction }

        for (i in 1 until fractions.size) {
            assertTrue(fractions[i] >= fractions[i - 1], "Fraction decreased at index $i")
        }
    }

    private fun buildMcuBootImage(payloadSize: Int): ByteArray {
        val headerSize = 32
        val data = ByteArray(headerSize + payloadSize)
        data[0] = 0x3D; data[1] = 0xB8.toByte(); data[2] = 0xF3.toByte(); data[3] = 0x96.toByte()
        data[8] = headerSize.toByte()
        data[12] = (payloadSize and 0xFF).toByte()
        data[13] = ((payloadSize shr 8) and 0xFF).toByte()
        return data
    }

    private fun smpResponse(cborMap: Map<String, Any>): ByteArray {
        val payload = Cbor.encodeStringMap(cborMap)
        val header = SmpHeader(
            op = SmpOp.WRITE_RSP,
            flags = 0,
            length = payload.size,
            group = 1,
            sequence = 0,
            commandId = 0,
        )
        return header.encode() + payload
    }

    companion object {
        // Mirrors McuBootDfuProtocol.CBOR_OVERHEAD_FIRST for test chunk simulation.
        // The first SMP upload chunk includes "len" and "sha" fields.
        private const val FIRST_CHUNK_OVERHEAD = 83
    }
}
