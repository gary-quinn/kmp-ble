package com.atruedev.kmpble.dfu

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Configuration for a firmware update.
 *
 * Defaults are tuned for Nordic nRF5 SDK Secure DFU over GATT.
 *
 * @property prnInterval how many data packets to send before waiting for a
 *   receipt confirmation (Packet Receipt Notification). `0` disables PRNs —
 *   faster but removes flow control. Default `10`.
 * @property retryCount maximum attempts per DFU object before giving up
 * @property retryDelay pause between retry attempts
 * @property commandTimeout how long to wait for a response to a DFU command
 * @property transport BLE transport to use — [GATT][DfuTransportConfig.Gatt]
 *   (default) or [L2CAP][DfuTransportConfig.L2cap] for higher throughput
 */
public data class DfuOptions(
    val prnInterval: Int = 10,
    val retryCount: Int = 3,
    val retryDelay: Duration = 2.seconds,
    val commandTimeout: Duration = 10.seconds,
    val transport: DfuTransportConfig = DfuTransportConfig.Gatt,
)

/**
 * Selects the BLE transport layer for DFU data transfer.
 */
public sealed interface DfuTransportConfig {
    /** Standard GATT-based transport using DFU Packet characteristic writes. */
    public data object Gatt : DfuTransportConfig

    /**
     * L2CAP Connection-Oriented Channel transport for higher throughput.
     *
     * @property psm Protocol/Service Multiplexer value advertised by the peripheral
     */
    public data class L2cap(val psm: Int) : DfuTransportConfig

    /** MCUboot SMP transport using a single SMP characteristic for commands and data. */
    public data object Smp : DfuTransportConfig

    /**
     * Espressif ESP-IDF OTA transport.
     *
     * ESP OTA service UUIDs vary between vendor implementations. Supply custom
     * UUIDs to override the defaults, or leave `null` to use the standard UUIDs.
     *
     * @property serviceUuid custom OTA service UUID, or `null` for default
     * @property controlUuid custom OTA control characteristic UUID, or `null` for default
     * @property dataUuid custom OTA data characteristic UUID, or `null` for default
     */
    @OptIn(ExperimentalUuidApi::class)
    public data class EspOta(
        val serviceUuid: Uuid? = null,
        val controlUuid: Uuid? = null,
        val dataUuid: Uuid? = null,
    ) : DfuTransportConfig
}
