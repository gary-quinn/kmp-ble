package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.dfu.DfuError

/**
 * Send a command where the device is expected to disconnect or reboot before
 * responding (e.g. reset commands). Swallows [DfuError.Timeout] since the
 * lack of response is the success signal.
 */
internal suspend fun DfuTransport.sendCommandExpectingDisconnect(data: ByteArray) {
    try {
        sendCommand(data)
    } catch (_: DfuError.Timeout) {
        // Expected: device reboots/disconnects before responding
    }
}
