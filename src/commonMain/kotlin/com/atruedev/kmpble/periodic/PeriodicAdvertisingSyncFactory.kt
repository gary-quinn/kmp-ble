package com.atruedev.kmpble.periodic

import com.atruedev.kmpble.Identifier
import kotlin.time.Duration

/**
 * Create a platform-specific [PeriodicAdvertisingSync] for the given advertiser.
 *
 * - **Android**: Uses `BluetoothLeScanner` to start a periodic advertising sync
 *   for the advertiser identified by [advertiserAddress] and [advertisingSid].
 *   PAST transfer ([PeriodicAdvertisingSync.transferTo]) requires API 31+.
 * - **iOS**: CoreBluetooth does not expose periodic advertising sync.
 *   Throws [PastException.NotSupported].
 * - **JVM**: No Bluetooth stack. Throws [PastException.NotSupported].
 *
 * @param advertiserAddress Identifier of the advertiser to sync to.
 * @param advertisingSid Advertising set ID from the extended advertisement.
 * @param timeout Maximum time to wait for sync establishment.
 * @return Active [PeriodicAdvertisingSync] receiving periodic reports.
 * @throws PastException.NotSupported on platforms without periodic sync support.
 * @throws PastException.SyncFailed if sync establishment fails or times out.
 */
public expect fun PeriodicAdvertisingSync(
    advertiserAddress: Identifier,
    advertisingSid: Int,
    timeout: Duration,
): PeriodicAdvertisingSync
