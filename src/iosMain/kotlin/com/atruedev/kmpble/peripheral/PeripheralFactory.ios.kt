package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.scanner.Advertisement
import platform.CoreBluetooth.CBPeripheral

public actual fun Advertisement.toPeripheral(): Peripheral {
    val cbPeripheral =
        platformContext as? CBPeripheral
            ?: throw IllegalStateException(
                "Cannot create Peripheral: Advertisement was not produced by IosScanner",
            )
    return PeripheralRegistry.getOrCreate(identifier) {
        IosPeripheral(cbPeripheral)
    }
}
