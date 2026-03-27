package com.atruedev.kmpble.dfu.protocol

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.testing.FakeDfuTransport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EspOtaDfuProtocolTest {

    private val protocol = EspOtaDfuProtocol()

    @Test
    fun happyPathSmallFirmware() = runTest {
        val transport = FakeDfuTransport(mtu = 128)
        val firmwareData = buildEspImage(64)
        val firmware = FirmwarePackage.EspOta(firmwareData)

        launch {
            // OTA Begin response: success
            transport.enqueueResponse(byteArrayOf(0x00))
            // OTA End response: success
            transport.enqueueResponse(byteArrayOf(0x00))
            // OTA Reboot response
            transport.enqueueResponse(byteArrayOf(0x00))
        }

        val states = protocol.performDfu(transport, firmware, DfuOptions()).toList()

        assertIs<DfuProgress.Starting>(states.first())
        assertTrue(states.any { it is DfuProgress.Transferring })
        assertIs<DfuProgress.Verifying>(states.first { it is DfuProgress.Verifying })
        assertIs<DfuProgress.Completing>(states.first { it is DfuProgress.Completing })
        assertIs<DfuProgress.Completed>(states.last())
    }

    @Test
    fun multiChunkTransfer() = runTest {
        val transport = FakeDfuTransport(mtu = 32)
        val firmwareData = buildEspImage(200)
        val firmware = FirmwarePackage.EspOta(firmwareData)

        launch {
            transport.enqueueResponse(byteArrayOf(0x00)) // begin
            transport.enqueueResponse(byteArrayOf(0x00)) // end
            transport.enqueueResponse(byteArrayOf(0x00)) // reboot
        }

        val states = protocol.performDfu(transport, firmware, DfuOptions()).toList()

        val transferringStates = states.filterIsInstance<DfuProgress.Transferring>()
        assertTrue(transferringStates.size > 1)
        assertEquals(1f, transferringStates.last().fraction)
        assertIs<DfuProgress.Completed>(states.last())
    }

    @Test
    fun otaBeginFailure() = runTest {
        val transport = FakeDfuTransport(mtu = 64)
        val options = DfuOptions(retryCount = 1)
        val firmware = FirmwarePackage.EspOta(buildEspImage(32))

        launch {
            transport.enqueueResponse(byteArrayOf(0x01)) // failure on sole attempt
        }

        assertFailsWith<DfuError.TransferFailed> {
            protocol.performDfu(transport, firmware, options).toList()
        }
    }

    @Test
    fun sendsDataViaDataChannel() = runTest {
        val transport = FakeDfuTransport(mtu = 64)
        val firmwareData = buildEspImage(128)
        val firmware = FirmwarePackage.EspOta(firmwareData)

        launch {
            transport.enqueueResponse(byteArrayOf(0x00))
            transport.enqueueResponse(byteArrayOf(0x00))
            transport.enqueueResponse(byteArrayOf(0x00))
        }

        protocol.performDfu(transport, firmware, DfuOptions()).toList()

        val dataLog = transport.getDataLog()
        assertTrue(dataLog.isNotEmpty(), "Expected firmware data to be sent via sendData")

        val totalDataBytes = dataLog.sumOf { it.size }
        assertEquals(firmwareData.size, totalDataBytes)
    }

    private fun buildEspImage(size: Int): ByteArray {
        val data = ByteArray(size)
        data[0] = 0xE9.toByte()
        return data
    }
}
