package com.atruedev.kmpble.dfu.protocol

import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.flow.Flow

public interface DfuProtocol {

    public fun performDfu(
        transport: DfuTransport,
        firmware: FirmwarePackage,
        options: DfuOptions,
    ): Flow<DfuProgress>
}
