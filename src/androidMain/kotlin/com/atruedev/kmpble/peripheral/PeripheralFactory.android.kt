package com.atruedev.kmpble.peripheral

import android.bluetooth.le.ScanResult
import com.atruedev.kmpble.KmpBle
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.scanner.Advertisement

public actual fun Advertisement.toPeripheral(): Peripheral {
    val scanResult = platformContext as? ScanResult
        ?: throw IllegalStateException(
            "Cannot create Peripheral: Advertisement was not produced by AndroidScanner"
        )
    return PeripheralRegistry.getOrCreate(identifier) {
        AndroidPeripheral(scanResult.device, KmpBle.requireContext())
    }
}
