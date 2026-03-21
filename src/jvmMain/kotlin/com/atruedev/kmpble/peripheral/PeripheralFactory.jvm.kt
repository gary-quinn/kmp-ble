package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.scanner.Advertisement

public actual fun Advertisement.toPeripheral(): Peripheral =
    throw UnsupportedOperationException("BLE peripherals are not available on JVM")
