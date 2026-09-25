@file:OptIn(KmpBleBackendApi::class)

package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.backend.BleBackends
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.newL2capListener

public actual fun L2capListener(): L2capListener {
    val backend =
        BleBackends.current() ?: throw L2capException.NotSupported(BleBackends.unavailableMessage("L2capListener"))
    return backend.newL2capListener()
}
