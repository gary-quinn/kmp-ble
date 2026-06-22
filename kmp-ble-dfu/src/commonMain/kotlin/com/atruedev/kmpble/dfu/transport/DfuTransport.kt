package com.atruedev.kmpble.dfu.transport

import com.atruedev.kmpble.dfu.DfuTransportConfig
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.Peripheral
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the BLE link used to send DFU commands and data.
 *
 * Built-in implementations:
 * - [GattDfuTransport] - uses DFU Control Point and DFU Packet characteristics
 * - [L2capDfuTransport] - L2CAP CoC channel for higher throughput (commands via GATT)
 * - [SmpTransport] - single SMP characteristic with response reassembly (MCUboot)
 * - [EspOtaTransport] - dual-characteristic OTA control/data (Espressif ESP-IDF)
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

    public companion object {
        /**
         * Create a GATT-based DFU transport using the DFU Control Point and
         * DFU Packet characteristics on the standard Nordic DFU service.
         *
         * @param peripheral a connected peripheral with the DFU service discovered
         * @param commandTimeout how long to wait for command responses
         * @throws com.atruedev.kmpble.dfu.DfuError.CharacteristicNotFound if DFU characteristics are missing
         */
        public fun gatt(peripheral: Peripheral, commandTimeout: Duration = 10.seconds): DfuTransport =
            GattDfuTransport(peripheral, commandTimeout)

        /**
         * Create an L2CAP-based DFU transport for high-throughput firmware transfer.
         *
         * Commands are still sent via GATT; data goes over the L2CAP channel.
         *
         * @param peripheral a connected peripheral with the DFU service discovered
         * @param channel an open L2CAP CoC channel for data transfer
         * @param commandTimeout how long to wait for command responses
         * @throws com.atruedev.kmpble.dfu.DfuError.CharacteristicNotFound if DFU characteristics are missing
         */
        public fun l2cap(peripheral: Peripheral, channel: L2capChannel, commandTimeout: Duration = 10.seconds): DfuTransport =
            L2capDfuTransport(peripheral, channel, commandTimeout)

        /**
         * Create an SMP-based DFU transport for MCUboot devices.
         *
         * @param peripheral a connected peripheral with the SMP service discovered
         * @param commandTimeout how long to wait for command responses
         * @throws com.atruedev.kmpble.dfu.DfuError.ServiceNotFound if the SMP service is missing
         */
        public fun smp(peripheral: Peripheral, commandTimeout: Duration = 10.seconds): DfuTransport =
            SmpTransport(peripheral, commandTimeout)

        /**
         * Create an ESP OTA transport for Espressif ESP-IDF devices.
         *
         * @param peripheral a connected peripheral with the OTA service discovered
         * @param config ESP OTA configuration with optional custom UUIDs
         * @param commandTimeout how long to wait for command responses
         * @throws com.atruedev.kmpble.dfu.DfuError.CharacteristicNotFound if OTA characteristics are missing
         */
        public fun espOta(
            peripheral: Peripheral,
            config: DfuTransportConfig.EspOta = DfuTransportConfig.EspOta(),
            commandTimeout: Duration = 10.seconds,
        ): DfuTransport = EspOtaTransport(peripheral, config, commandTimeout)
    }
}
