package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.dfu.DfuError

/**
 * Send a command where the device is expected to disconnect or reboot before
 * responding (e.g. reset commands).
 *
 * A reboot command can fail in two expected ways: the device disconnects
 * before a response arrives ([DfuError.Timeout]), or the write itself fails
 * because the link dropped mid-transmission ([DfuError.TransferFailed]).
 * Both are treated as successful reset signals.
 */
internal suspend fun DfuTransport.sendCommandExpectingDisconnect(data: ByteArray) {
    try {
        sendCommand(data)
    } catch (_: DfuError.Timeout) {
        // Expected: device reboots before the response notification arrives
    } catch (_: DfuError.TransferFailed) {
        // Expected: BLE link drops during the write because the device reset
    }
}
