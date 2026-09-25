package com.atruedev.kmpble.backend

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.backend.internal.BackendAdvertisementContext
import com.atruedev.kmpble.backend.internal.BackendAdvertiser
import com.atruedev.kmpble.backend.internal.BackendBluetoothAdapter
import com.atruedev.kmpble.backend.internal.BackendExtendedAdvertiser
import com.atruedev.kmpble.backend.internal.BackendGattServer
import com.atruedev.kmpble.backend.internal.BackendL2capListener
import com.atruedev.kmpble.backend.internal.BackendPeripheral
import com.atruedev.kmpble.backend.internal.BackendScanner
import com.atruedev.kmpble.l2cap.L2capListener
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.ScannerConfig
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.ExtendedAdvertiser
import com.atruedev.kmpble.server.GattServer
import com.atruedev.kmpble.server.GattServerBuilder

/** A [Scanner] backed by this backend, independent of [BleBackends.current]. */
@KmpBleBackendApi
public fun BleBackend.newScanner(configure: ScannerConfig.() -> Unit = {}): Scanner = BackendScanner(this, configure)

/**
 * The [Peripheral] for [identifier] on this backend. Instances are shared per identifier,
 * like every other `toPeripheral()` path.
 */
@KmpBleBackendApi
public fun BleBackend.peripheral(
    identifier: Identifier,
    handle: String? = null,
): Peripheral =
    PeripheralRegistry.getOrCreate(identifier) {
        BackendPeripheral(identifier, createPeripheralTransport(identifier, handle))
    }

@KmpBleBackendApi
public fun BleBackend.newBluetoothAdapter(): BluetoothAdapter = BackendBluetoothAdapter(createAdapterTransport())

@KmpBleBackendApi
public fun BleBackend.newGattServer(builder: GattServerBuilder.() -> Unit): GattServer =
    BackendGattServer(GattServerBuilder().apply(builder).services.toList(), createGattServerTransport())

@KmpBleBackendApi
public fun BleBackend.newAdvertiser(): Advertiser = BackendAdvertiser(createAdvertiserTransport())

@ExperimentalBleApi
@KmpBleBackendApi
public fun BleBackend.newExtendedAdvertiser(): ExtendedAdvertiser =
    BackendExtendedAdvertiser(createAdvertiserTransport(), id)

@KmpBleBackendApi
public fun BleBackend.newL2capListener(): L2capListener = BackendL2capListener(createL2capListenerTransport())

/** The device handle [backend] reported for this advertisement, or `null` if another source produced it. */
@KmpBleBackendApi
public fun Advertisement.backendHandle(backend: BleBackend): String? =
    (platformContext as? BackendAdvertisementContext)?.takeIf { it.backend.id == backend.id }?.handle

/** Routes a backend log event through [com.atruedev.kmpble.logging.BleLogConfig.logger]. */
@KmpBleBackendApi
public fun backendLog(event: BleLogEvent) {
    logEvent(event)
}
