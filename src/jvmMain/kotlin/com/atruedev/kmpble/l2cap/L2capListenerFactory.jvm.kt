package com.atruedev.kmpble.l2cap

public actual fun L2capListener(): L2capListener =
    throw L2capException.NotSupported("L2capListener is not supported on JVM - BLE requires Android or iOS")
