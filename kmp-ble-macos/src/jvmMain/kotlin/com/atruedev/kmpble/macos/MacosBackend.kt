package com.atruedev.kmpble.macos

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.AdapterTransport
import com.atruedev.kmpble.backend.AdvertiserTransport
import com.atruedev.kmpble.backend.BleBackend
import com.atruedev.kmpble.backend.GattServerTransport
import com.atruedev.kmpble.backend.L2capListenerTransport
import com.atruedev.kmpble.backend.PeripheralTransport
import com.atruedev.kmpble.backend.ScanTransport
import com.atruedev.kmpble.macos.internal.MacosAdapterTransport
import com.atruedev.kmpble.macos.internal.MacosAdvertiserTransport
import com.atruedev.kmpble.macos.internal.MacosGattServerTransport
import com.atruedev.kmpble.macos.internal.MacosL2capListenerTransport
import com.atruedev.kmpble.macos.internal.MacosPeripheralTransport
import com.atruedev.kmpble.macos.internal.MacosPermissions
import com.atruedev.kmpble.macos.internal.MacosScanTransport
import com.atruedev.kmpble.macos.internal.MacosStack
import com.atruedev.kmpble.permissions.PermissionResult

/**
 * macOS (Apple silicon) CoreBluetooth backend, registered through `META-INF/services` so the
 * portable JVM factories use it automatically on macOS arm64.
 *
 * CoreBluetooth starts on first use, not at construction. A packaged `.app` must declare
 * `NSBluetoothAlwaysUsageDescription` in its Info.plist; without it the backend refuses to
 * start CoreBluetooth (macOS would terminate the process) and reports
 * [Macos.ERROR_USAGE_DESCRIPTION_MISSING].
 */
public class MacosBackend internal constructor(
    private val stackProvider: () -> MacosStack,
) : BleBackend {
    public constructor() : this({ MacosStack.shared })

    private val stack: MacosStack by lazy(stackProvider)

    override val id: String = Macos.BACKEND_ID

    override fun isSupported(): Boolean =
        Macos.isMacosArm64() &&
            (Macos.isNativeLibraryBundled() || System.getProperty(Macos.LIBRARY_PATH_PROPERTY) != null)

    override fun checkPermissions(): PermissionResult = MacosPermissions.check(stack)

    override fun createAdapterTransport(): AdapterTransport = MacosAdapterTransport(stack)

    override fun createScanTransport(): ScanTransport = MacosScanTransport(stack)

    override fun createPeripheralTransport(
        identifier: Identifier,
        handle: String?,
    ): PeripheralTransport = MacosPeripheralTransport(stack, handle ?: identifier.value)

    override fun createGattServerTransport(): GattServerTransport = MacosGattServerTransport(stack)

    override fun createAdvertiserTransport(): AdvertiserTransport = MacosAdvertiserTransport(stack)

    override fun createL2capListenerTransport(): L2capListenerTransport = MacosL2capListenerTransport(stack)
}
