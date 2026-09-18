package com.atruedev.kmpble.gatt.internal

import android.os.DeadObjectException
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost

internal actual fun Throwable.asPlatformConnectionLoss(): ConnectionLost? =
    if (this is DeadObjectException) {
        ConnectionLost(
            reason = "Binder connection to the Bluetooth process died",
            failureReason = ConnectionFailureReason.LINK_LOSS,
        )
    } else {
        null
    }
