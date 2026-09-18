package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.error.ConnectionLost

internal actual fun Throwable.asPlatformConnectionLoss(): ConnectionLost? = null
