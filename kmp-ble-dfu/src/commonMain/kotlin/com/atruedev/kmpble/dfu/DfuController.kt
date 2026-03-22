package com.atruedev.kmpble.dfu

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.internal.DfuSession
import com.atruedev.kmpble.dfu.protocol.DfuProtocol
import com.atruedev.kmpble.dfu.protocol.NordicDfuProtocol
import com.atruedev.kmpble.dfu.transport.DfuTransport
import com.atruedev.kmpble.dfu.transport.GattDfuTransport
import com.atruedev.kmpble.dfu.transport.L2capDfuTransport
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

public class DfuController(
    private val peripheral: Peripheral,
    private val protocol: DfuProtocol = NordicDfuProtocol(),
) {

    @kotlin.concurrent.Volatile
    private var activeSession: DfuSession? = null

    public fun performDfu(
        firmware: FirmwarePackage,
        options: DfuOptions = DfuOptions(),
    ): Flow<DfuProgress> = flow {
        try {
            validateConnected()
            val transport = createTransport(options)
            val session = DfuSession(transport, protocol)
            activeSession = session
            try {
                emitAll(session.start(firmware, options))
            } finally {
                activeSession = null
            }
        } catch (e: DfuError) {
            emit(DfuProgress.Failed(e))
        }
    }

    public fun abort() {
        activeSession?.abort()
    }

    private fun validateConnected() {
        val state = peripheral.state.value
        if (state !is State.Connected) {
            throw DfuError.NotConnected("Peripheral state is $state, expected Connected.Ready")
        }
    }

    private suspend fun createTransport(options: DfuOptions): DfuTransport =
        if (options.useL2cap) {
            val psm = options.l2capPsm
                ?: throw DfuError.TransferFailed("L2CAP PSM must be specified when useL2cap is true")
            val channel = peripheral.openL2capChannel(psm)
            L2capDfuTransport(peripheral, channel, options.commandTimeout)
        } else {
            GattDfuTransport(peripheral, options.commandTimeout)
        }
}
