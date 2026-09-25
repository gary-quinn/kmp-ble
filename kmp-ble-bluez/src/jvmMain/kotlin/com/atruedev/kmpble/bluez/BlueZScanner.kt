package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.backend.newScanner
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.ScannerConfig

/**
 * Explicit Linux BlueZ [Scanner]. Equivalent to the portable `Scanner { }` when
 * `kmp-ble-bluez` is the active backend, but always uses BlueZ.
 *
 * Construction does not touch D-Bus; failures surface as
 * [com.atruedev.kmpble.scanner.ScanEvent.Failed] with one of the `ERROR_*` codes.
 */
public class BlueZScanner(
    configure: ScannerConfig.() -> Unit = {},
) : Scanner by BlueZBackend.shared.newScanner(configure) {
    public companion object {
        public const val ERROR_DBUS_UNAVAILABLE: Int = -10
        public const val ERROR_NO_ADAPTER: Int = -11
        public const val ERROR_ADAPTER_OFF: Int = -12
        public const val ERROR_DISCOVERY_FAILED: Int = -13
    }
}
