package com.atruedev.kmpble.l2cap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream

/**
 * Adapts [BluetoothSocket] to the [L2capSocket] interface used by
 * [AndroidL2capChannel]. Thin delegation — no added logic.
 */
@SuppressLint("NewApi")
internal class BluetoothL2capSocket(
    private val socket: BluetoothSocket,
) : L2capSocket {
    override val inputStream: InputStream get() = socket.inputStream
    override val outputStream: OutputStream get() = socket.outputStream
    override val isConnected: Boolean get() = socket.isConnected
    override val maxTransmitPacketSize: Int get() = socket.maxTransmitPacketSize

    override fun close() {
        socket.close()
    }
}
