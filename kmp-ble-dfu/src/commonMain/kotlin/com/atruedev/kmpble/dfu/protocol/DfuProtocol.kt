package com.atruedev.kmpble.dfu.protocol

import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.flow.Flow

/**
 * Strategy for executing a firmware update over a [DfuTransport].
 *
 * Implement this interface to support DFU protocols beyond the built-in
 * [NordicDfuProtocol]. The protocol is responsible for sequencing DFU commands
 * (create, write, execute) and emitting [DfuProgress] states.
 *
 * @see NordicDfuProtocol
 */
public interface DfuProtocol {

    /**
     * Execute a complete firmware update and emit progress.
     *
     * @param transport BLE transport for sending commands and data
     * @param firmware parsed firmware package to install
     * @param options DFU configuration (PRN interval, retries, timeouts)
     * @return a cold [Flow] of [DfuProgress] states from [Starting][DfuProgress.Starting]
     *   through [Completed][DfuProgress.Completed]
     */
    public fun performDfu(
        transport: DfuTransport,
        firmware: FirmwarePackage,
        options: DfuOptions,
    ): Flow<DfuProgress>
}
