package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.error.ConnectionLost

/**
 * Maps platform throwables that mean "the BLE link or adapter IPC is gone" to
 * [ConnectionLost]. Returns null when [this] is not a known connection-loss signal.
 */
internal expect fun Throwable.asPlatformConnectionLoss(): ConnectionLost?
