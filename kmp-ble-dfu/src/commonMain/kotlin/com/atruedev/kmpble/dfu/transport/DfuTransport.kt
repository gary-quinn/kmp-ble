package com.atruedev.kmpble.dfu.transport

import kotlinx.coroutines.flow.Flow

public interface DfuTransport : AutoCloseable {

    public val mtu: Int

    public val notifications: Flow<ByteArray>

    public suspend fun sendCommand(data: ByteArray): ByteArray

    public suspend fun sendData(data: ByteArray)

    /**
     * Release transport resources synchronously.
     *
     * Implementations must be non-blocking. Both [GattDfuTransport] (no-op) and
     * [L2capDfuTransport] (delegates to [com.atruedev.kmpble.l2cap.L2capChannel.close])
     * satisfy this — L2capChannel.close() is synchronous on all platforms.
     */
    override fun close()
}
