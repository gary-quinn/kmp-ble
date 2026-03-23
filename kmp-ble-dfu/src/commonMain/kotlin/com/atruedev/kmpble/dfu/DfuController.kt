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

/**
 * Orchestrates a firmware update over BLE.
 *
 * Connects the DFU [protocol] to the BLE [peripheral] and exposes the update
 * as a cold [Flow] of [DfuProgress] states. Collecting the flow starts the
 * transfer; cancelling collection or calling [abort] stops it.
 *
 * ## Usage
 * ```
 * val controller = DfuController(peripheral)
 * controller.performDfu(firmware)
 *     .collect { progress ->
 *         when (progress) {
 *             is DfuProgress.Transferring -> updateUi(progress.fraction)
 *             is DfuProgress.Completed -> showSuccess()
 *             is DfuProgress.Failed -> showError(progress.error)
 *             else -> {}
 *         }
 *     }
 * ```
 *
 * @param peripheral a connected [Peripheral] running a DFU service
 * @param protocol DFU protocol implementation. Defaults to [NordicDfuProtocol].
 * @see DfuProgress
 * @see DfuOptions
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
     * The flow emits [DfuProgress] states in order:
     * [Starting][DfuProgress.Starting] → [Transferring][DfuProgress.Transferring] →
     * [Completing][DfuProgress.Completing] → [Completed][DfuProgress.Completed].
     * On error, a [Failed][DfuProgress.Failed] state is emitted instead of throwing.
     *
     * @param firmware the firmware package to install — obtain via [FirmwarePackage.fromZipBytes]
     * @param options DFU configuration. Defaults are tuned for Nordic nRF5 SDK DFU.
     * @return a cold [Flow] of [DfuProgress] states. Cancelling collection aborts the update.
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
        }
}
