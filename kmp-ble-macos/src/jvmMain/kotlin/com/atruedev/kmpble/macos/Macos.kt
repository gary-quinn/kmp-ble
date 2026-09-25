package com.atruedev.kmpble.macos

import com.atruedev.kmpble.macos.internal.NativeLibrary

/** macOS CoreBluetooth helpers for the `kmp-ble-macos` JVM backend. */
public object Macos {
    /** [com.atruedev.kmpble.backend.BleBackend.id] of this backend, for `-Dkmpble.backend=macos`. */
    public const val BACKEND_ID: String = "macos"

    /** Optional absolute path of a `libkmpble_macos.dylib` to load instead of the bundled one. */
    public const val LIBRARY_PATH_PROPERTY: String = "kmpble.macos.library"

    /**
     * Set to `false` to skip the pre-flight check that refuses to start CoreBluetooth when the app
     * macOS holds responsible for this process has no `NSBluetoothAlwaysUsageDescription`.
     */
    public const val USAGE_DESCRIPTION_CHECK_PROPERTY: String = "kmpble.macos.usageDescriptionCheck"

    /** Scan / connect error code: the Bluetooth adapter is powered off. */
    public const val ERROR_ADAPTER_OFF: Int = -30

    /** The user or MDM denied Bluetooth access for this process (TCC). */
    public const val ERROR_UNAUTHORIZED: Int = -31

    /** This Mac has no Bluetooth LE controller. */
    public const val ERROR_UNSUPPORTED: Int = -32

    /** The app bundle's Info.plist lacks `NSBluetoothAlwaysUsageDescription`. */
    public const val ERROR_USAGE_DESCRIPTION_MISSING: Int = -33

    /** The native library could not be loaded. */
    public const val ERROR_NATIVE_UNAVAILABLE: Int = -34

    /** True on macOS running on Apple silicon, the only architecture this backend ships for. */
    public fun isMacosArm64(): Boolean {
        val os = System.getProperty("os.name")?.lowercase() ?: return false
        val arch = System.getProperty("os.arch")?.lowercase() ?: return false
        return os.startsWith("mac") && (arch == "aarch64" || arch == "arm64")
    }

    /** True when the jar carries the native CoreBluetooth shim (it is built on macOS hosts only). */
    public fun isNativeLibraryBundled(): Boolean = NativeLibrary.isBundled()
}
