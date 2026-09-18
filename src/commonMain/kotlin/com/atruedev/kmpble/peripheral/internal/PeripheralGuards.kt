package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.PeripheralClosed

internal fun requirePeripheralOpen(closed: Boolean) {
    if (closed) {
        throw BleException(PeripheralClosed())
    }
}
