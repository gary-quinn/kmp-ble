package com.atruedev.kmpble.dfu

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.internal.DfuSession
import com.atruedev.kmpble.dfu.protocol.DfuProtocol
import com.atruedev.kmpble.dfu.protocol.EspOtaDfuProtocol
import com.atruedev.kmpble.dfu.protocol.McuBootDfuProtocol
import com.atruedev.kmpble.dfu.protocol.NordicDfuProtocol
import com.atruedev.kmpble.dfu.transport.DfuTransport
import com.atruedev.kmpble.dfu.transport.EspOtaTransport
import com.atruedev.kmpble.dfu.transport.GattDfuTransport
import com.atruedev.kmpble.dfu.transport.L2capDfuTransport
import com.atruedev.kmpble.dfu.transport.SmpTransport
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Orchestrates a firmware update over BLE.
 *
 * Connects the DFU [protocol] to the BLE [peripheral] and exposes the update
 * as a cold [Flow] of [DfuProgress] states. Collecting the flow starts the
 * transfer; cancelling collection or calling [abort] stops it.
 *
 * @param peripheral a connected [Peripheral] running a DFU service
 * @param protocol DFU protocol implementation. Defaults to [NordicDfuProtocol].
 */
public class DfuController(
    private val peripheral: Peripheral,
    private val protocol: DfuProtocol = NordicDfuProtocol(),
) {

    @kotlin.concurrent.Volatile
    private var activeSession: DfuSession? = null

    /**
     * Start a firmware update and observe its progress.
     *
     * Returns a **cold** [Flow] — the DFU begins when you call `collect`.
     *
     * @param firmware the firmware package to install
     * @param options DFU configuration
     * @return a cold [Flow] of [DfuProgress] states
     */
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
        } catch (e: Exception) {
            if (e is DfuError) emit(DfuProgress.Failed(e)) else throw e
        }
    }

    /** Cancel a DFU that is currently in progress. Safe to call if no DFU is active. */
    public fun abort() {
        activeSession?.abort()
    }

    public companion object {
        /**
         * Create a [DfuController] with automatic protocol detection.
         *
         * Inspects the peripheral's discovered services to determine which
         * DFU protocol it supports.
         *
         * @param peripheral a connected peripheral with discovered services
         * @return a [DfuController] configured for the detected protocol
         * @throws DfuError.ServiceNotFound if no DFU service is detected
         */
        public fun create(peripheral: Peripheral): DfuController {
            val protocolType = DfuDetector.detect(peripheral)
                ?: throw DfuError.ServiceNotFound("No known DFU service found on peripheral")

            val protocol: DfuProtocol = when (protocolType) {
                DfuProtocolType.NORDIC -> NordicDfuProtocol()
                DfuProtocolType.MCUBOOT -> McuBootDfuProtocol()
                DfuProtocolType.ESP_OTA -> EspOtaDfuProtocol()
            }

            return DfuController(peripheral, protocol)
        }
    }

    private fun validateConnected() {
        val state = peripheral.state.value
        if (state !is State.Connected) {
            throw DfuError.NotConnected("Peripheral state is $state, expected Connected.Ready")
        }
    }

    private suspend fun createTransport(options: DfuOptions): DfuTransport =
        when (val config = options.transport) {
            is DfuTransportConfig.Gatt -> GattDfuTransport(peripheral, options.commandTimeout)
            is DfuTransportConfig.L2cap -> {
                val channel = peripheral.openL2capChannel(config.psm)
                L2capDfuTransport(peripheral, channel, options.commandTimeout)
            }
            is DfuTransportConfig.Smp -> SmpTransport(peripheral, options.commandTimeout)
            is DfuTransportConfig.EspOta -> EspOtaTransport(peripheral, config, options.commandTimeout)
        }
}
