package com.atruedev.kmpble.dfu

import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.protocol.Crc32
import com.atruedev.kmpble.dfu.protocol.DfuOpcode
import com.atruedev.kmpble.dfu.protocol.DfuProtocol
import com.atruedev.kmpble.dfu.protocol.DfuResultCode
import com.atruedev.kmpble.dfu.protocol.NordicDfuProtocol
import com.atruedev.kmpble.dfu.internal.toLittleEndianBytes
import com.atruedev.kmpble.dfu.transport.DfuTransport
import com.atruedev.kmpble.dfu.transport.DfuUuids
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DfuControllerTest {

    @Test
    fun notConnectedEmitsFailed() = runTest {
        val peripheral = FakePeripheral {}
        val controller = DfuController(peripheral)
        val firmware = FirmwarePackage(byteArrayOf(1), byteArrayOf(2))

        val progress = controller.performDfu(firmware).toList()
        val failed = progress.last()
        assertIs<DfuProgress.Failed>(failed)
        assertIs<DfuError.NotConnected>(failed.error)
    }

    @Test
    fun serviceNotFoundThrows() = runTest {
        val peripheral = FakePeripheral {
            // No DFU service
        }
        peripheral.connect()
        val controller = DfuController(peripheral)
        val firmware = FirmwarePackage(byteArrayOf(1), byteArrayOf(2))

        val progress = controller.performDfu(firmware).toList()
        val failed = progress.last()
        assertIs<DfuProgress.Failed>(failed)
        assertIs<DfuError.CharacteristicNotFound>(failed.error)
    }

    @Test
    fun customProtocolUsed() = runTest {
        var protocolCalled = false

        val customProtocol = object : DfuProtocol {
            override fun performDfu(
                transport: DfuTransport,
                firmware: FirmwarePackage,
                options: DfuOptions,
            ): Flow<DfuProgress> = flow {
                protocolCalled = true
                emit(DfuProgress.Starting)
                emit(DfuProgress.Completed)
            }
        }

        val responseChannel = Channel<ByteArray>(Channel.BUFFERED)

        val peripheral = FakePeripheral {
            service(DfuUuids.DFU_SERVICE) {
                characteristic(DfuUuids.DFU_CONTROL_POINT) {
                    properties(write = true, notify = true)
                    onWrite { _, _ -> }
                    onObserve { responseChannel.receiveAsFlow() }
                }
                characteristic(DfuUuids.DFU_PACKET) {
                    properties(writeWithoutResponse = true)
                    onWrite { _, _ -> }
                }
            }
        }

        peripheral.connect()
        val controller = DfuController(peripheral, customProtocol)
        val firmware = FirmwarePackage(byteArrayOf(1), byteArrayOf(2))

        val progress = controller.performDfu(firmware).toList()
        assertIs<DfuProgress.Completed>(progress.last())
        assertTrue(protocolCalled)
    }

    private fun kotlinx.coroutines.channels.Channel<ByteArray>.receiveAsFlow(): Flow<ByteArray> = flow {
        for (item in this@receiveAsFlow) {
            emit(item)
        }
    }
}
