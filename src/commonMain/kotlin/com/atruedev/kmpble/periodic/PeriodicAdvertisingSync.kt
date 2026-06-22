package com.atruedev.kmpble.periodic

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BleCapabilities
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow

/**
 * Active sync to a BLE 5.0 periodic advertising train.
 *
 * Once synced, the device receives periodic advertising reports on secondary
 * channels without needing continuous scanning. Obtained from the scanner when
 * a periodic advertising train is detected, or received via PAST from a
 * connected peer.
 *
 * ## Periodic Advertising (BLE 5.0)
 *
 * Extended advertising can include a periodic advertising train -- a sequence
 * of advertisements broadcast on secondary channels at a fixed interval. The
 * train carries time-sensitive or broadcast data (e.g., audio stream metadata,
 * sensor readings) and is more power-efficient than continuous primary-channel
 * scanning.
 *
 * ## PAST (Periodic Advertising Sync Transfer, BLE 5.1)
 *
 * A device with an active [PeriodicAdvertisingSync] can transfer the sync
 * information to a connected peripheral via [transferTo]. The peripheral can
 * then receive the same periodic advertising train without scanning for it.
 *
 * Check [BleCapabilities.supportsPast] before using transfer features.
 *
 * ## Platform Support
 *
 * - **Android**: Full support via `BluetoothLeScanner` (API 26+ periodic sync;
 *   API 31+ PAST transfer).
 * - **iOS**: Not supported by CoreBluetooth. Throws [PastException.NotSupported].
 * - **JVM**: No Bluetooth stack. Throws [PastException.NotSupported].
 */
@ExperimentalBleApi
public interface PeriodicAdvertisingSync : AutoCloseable {
    /** Address of the advertiser whose periodic advertisements we are synced to. */
    public val advertiserAddress: Identifier

    /** Advertising set ID identifying the periodic advertising set. */
    public val advertisingSid: Int

    /** Whether the sync is active and receiving reports. */
    public val isActive: Boolean

    /**
     * Flow of periodic advertising reports from the synced advertiser.
     *
     * Emits [PeriodicReport] for each received periodic advertising packet.
     * Completes when the sync is closed or lost.
     */
    public val reports: Flow<PeriodicReport>

    /**
     * Transfer this sync to a connected peripheral via PAST (BLE 5.1).
     *
     * The peripheral can then receive periodic advertising reports from the
     * same advertiser without performing its own scan and sync procedure.
     *
     * @param peripheral A connected peripheral to receive the sync transfer.
     * @throws PastException.NotConnected if the peripheral is not connected.
     * @throws PastException.TransferFailed if the transfer protocol fails.
     * @throws PastException.NotSupported on platforms without PAST support.
     */
    public suspend fun transferTo(peripheral: Peripheral)

    /** End the sync and stop receiving periodic advertising reports. */
    override fun close()
}
