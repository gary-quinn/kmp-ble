package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.AdapterTransport
import com.atruedev.kmpble.backend.AdvertiserTransport
import com.atruedev.kmpble.backend.BleBackend
import com.atruedev.kmpble.backend.GattServerTransport
import com.atruedev.kmpble.backend.PeripheralTransport
import com.atruedev.kmpble.backend.ScanTransport
import com.atruedev.kmpble.permissions.PermissionResult

/**
 * Linux BlueZ backend, registered through `META-INF/services` so the portable JVM factories
 * (`Scanner { }`, `Advertisement.toPeripheral()`, `GattServer { }`, `Advertiser()`,
 * `BluetoothAdapter()`) use it automatically on Linux.
 */
public class BlueZBackend : BleBackend {
    override val id: String = BlueZ.BACKEND_ID

    override fun isSupported(): Boolean = BlueZ.isLinux()

    override fun checkPermissions(): PermissionResult = BlueZPermissions.check()

    override fun createAdapterTransport(): AdapterTransport = BlueZAdapterTransport()

    override fun createScanTransport(): ScanTransport = BlueZScanTransport()

    override fun createPeripheralTransport(
        identifier: Identifier,
        handle: String?,
    ): PeripheralTransport = BlueZPeripheralTransport(address = identifier.value, devicePath = handle)

    override fun createGattServerTransport(): GattServerTransport = BlueZGattServerTransport()

    override fun createAdvertiserTransport(): AdvertiserTransport = BlueZAdvertiserTransport()

    internal companion object {
        val shared: BlueZBackend = BlueZBackend()
    }
}
