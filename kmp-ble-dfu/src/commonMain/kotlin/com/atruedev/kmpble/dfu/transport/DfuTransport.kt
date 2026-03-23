package com.atruedev.kmpble.dfu.transport

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the BLE link used to send DFU commands and data.
 *
 * Two built-in implementations are provided:
 * - GATT transport — uses DFU Control Point and DFU Packet characteristics
 * - L2CAP transport — sends data over an L2CAP CoC channel for higher throughput,
 *   while commands still go through GATT
 *
 * Implement this interface to support custom transport mechanisms.
 *
 * @see com.atruedev.kmpble.dfu.DfuTransportConfig
 */
public interface DfuTransport : AutoCloseable {

    /** Maximum payload size per write, in bytes. */
    public val mtu: Int

    /** Incoming notifications from the DFU Control Point characteristic. */
    public val notifications: Flow<ByteArray>

    /**
     * Write a command to the DFU Control Point and wait for the response notification.
     *
     * @param data raw command bytes (opcode + parameters)
     * @return the response notification bytes
     * @throws com.atruedev.kmpble.dfu.DfuError.Timeout if no response arrives within the configured timeout
     */
    public suspend fun sendCommand(data: ByteArray): ByteArray

    /**
     * Write firmware data to the peripheral.
     *
     * Uses Write Without Response on GATT or a channel write on L2CAP.
     */
    public suspend fun sendData(data: ByteArray)

    /**
     * Release transport resources synchronously.
     *
     * Implementations must be non-blocking. The GATT transport is a no-op;
     * the L2CAP transport closes the underlying channel.
     */
    override fun close()
}
