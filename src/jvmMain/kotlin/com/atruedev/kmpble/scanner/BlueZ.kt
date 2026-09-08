package com.atruedev.kmpble.scanner

import com.github.hypfvieh.bluetooth.DeviceManager

/**
 * Linux BlueZ platform helpers for JVM desktop BLE.
 */
public object BlueZ {
    /** System property that must be `"true"` for [Scanner] to delegate to [BlueZScanner]. */
    public const val ENABLE_PROPERTY: String = "kmpble.bluez.enabled"

    /** Optional adapter id (for example `hci0` or adapter MAC). Defaults to the first adapter. */
    public const val ADAPTER_PROPERTY: String = "kmpble.bluez.adapter"

    /** True when running on a Linux operating system. */
    public fun isLinux(): Boolean {
        val os = System.getProperty("os.name")?.lowercase() ?: return false
        return os.contains("linux")
    }

    /** True when [ENABLE_PROPERTY] is set to `"true"`. */
    public fun isExplicitlyEnabled(): Boolean = System.getProperty(ENABLE_PROPERTY) == "true"

    /**
     * Best-effort probe: Linux + system D-Bus + at least one BlueZ adapter object.
     * Safe to call on CI; returns false when BlueZ is absent.
     *
     * Intended for app startup checks, not the [Scanner] factory hot path (use lazy
     * [BlueZScanner] construction instead).
     */
    public fun isAvailable(): Boolean {
        if (!isLinux()) return false
        return runCatching {
            val manager = DeviceManager.createInstance(false)
            try {
                manager.scanForBluetoothAdapters().isNotEmpty()
            } finally {
                manager.closeConnection()
            }
        }.getOrDefault(false)
    }

    internal fun requireLinux(): Unit = check(isLinux()) { "BlueZScanner requires Linux" }

    internal fun adapterNameOrNull(): String? = System.getProperty(ADAPTER_PROPERTY)?.takeIf { it.isNotBlank() }
}
