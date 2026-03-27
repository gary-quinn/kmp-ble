package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.scanner.Advertisement

/**
 * Creates a [Peripheral] from a discovered [Advertisement].
 *
 * The returned peripheral can be used to connect, perform GATT operations, and observe state.
 * Call [Peripheral.close] when done to release platform resources.
 */
public expect fun Advertisement.toPeripheral(): Peripheral
