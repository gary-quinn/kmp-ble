package com.atruedev.kmpble.quickstart

/**
 * Runtime toggles for the BLE Quickstart sample.
 *
 * Set [useFakeBle] to run scan/connect/observe against [com.atruedev.kmpble.testing.FakeScanner]
 * and [com.atruedev.kmpble.testing.FakePeripheral] without a BLE radio. Real hardware remains the
 * default for physical devices.
 */
object QuickstartConfig {
    var useFakeBle: Boolean = false
}
