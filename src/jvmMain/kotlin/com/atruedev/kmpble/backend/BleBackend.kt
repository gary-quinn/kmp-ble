package com.atruedev.kmpble.backend

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.permissions.PermissionResult
import com.atruedev.kmpble.server.AdvertiserException
import com.atruedev.kmpble.server.ServerException

/**
 * A desktop Bluetooth stack that the portable JVM factories can delegate to.
 *
 * Implementations are discovered with [java.util.ServiceLoader] from
 * `META-INF/services/com.atruedev.kmpble.backend.BleBackend`. Factory methods must be cheap:
 * they may not touch the Bluetooth stack until the returned transport is used.
 */
@KmpBleBackendApi
public interface BleBackend {
    /** Stable backend id, used by [BleBackends.BACKEND_PROPERTY]. For example `bluez` or `macos`. */
    public val id: String

    /** Higher wins when several supported backends are on the classpath. */
    public val priority: Int get() = 0

    /** OS and architecture check only. Must not perform I/O. */
    public fun isSupported(): Boolean

    public fun checkPermissions(): PermissionResult

    public fun createAdapterTransport(): AdapterTransport

    public fun createScanTransport(): ScanTransport

    /**
     * Creates the transport for one remote device. [handle] is the value a [ScanTransport]
     * reported in [ScanRecord.handle], or `null` when the peripheral is created from an
     * identifier alone.
     */
    public fun createPeripheralTransport(
        identifier: Identifier,
        handle: String?,
    ): PeripheralTransport

    public fun createGattServerTransport(): GattServerTransport =
        throw ServerException.NotSupported("GATT server is not supported by the $id backend")

    public fun createAdvertiserTransport(): AdvertiserTransport =
        throw AdvertiserException.NotSupported("Advertising is not supported by the $id backend")

    public fun createL2capListenerTransport(): L2capListenerTransport =
        throw L2capException.NotSupported("L2CAP listeners are not supported by the $id backend")
}
