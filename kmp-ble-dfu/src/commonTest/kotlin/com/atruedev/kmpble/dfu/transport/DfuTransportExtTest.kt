package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.dfu.DfuError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DfuTransportExtTest {

    @Test
    fun swallowsTimeoutFromSendCommand() = runTest {
        val transport = ThrowingTransport(DfuError.Timeout("test timeout"))
        transport.sendCommandExpectingDisconnect(byteArrayOf(0x01))
    }

    @Test
    fun swallowsTransferFailedFromSendCommand() = runTest {
        val transport = ThrowingTransport(DfuError.TransferFailed("link dropped"))
        transport.sendCommandExpectingDisconnect(byteArrayOf(0x01))
    }

    @Test
    fun propagatesUnexpectedErrors() = runTest {
        val transport = ThrowingTransport(DfuError.ProtocolError(0, 1, "unexpected"))
        try {
            transport.sendCommandExpectingDisconnect(byteArrayOf(0x01))
            error("Expected exception to propagate")
        } catch (_: DfuError.ProtocolError) {
            // Expected: non-disconnect errors must propagate
        }
    }
}

private class ThrowingTransport(private val error: Throwable) : DfuTransport {
    override val mtu: Int = 20
    override val notifications: Flow<ByteArray> = emptyFlow()
    override suspend fun sendCommand(data: ByteArray): ByteArray = throw error
    override suspend fun sendData(data: ByteArray) {}
    override fun close() {}
}
