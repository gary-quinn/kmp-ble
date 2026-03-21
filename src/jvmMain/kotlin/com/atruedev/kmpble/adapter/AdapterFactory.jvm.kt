package com.atruedev.kmpble.adapter

public actual fun BluetoothAdapter(): BluetoothAdapter =
    throw UnsupportedOperationException("BLE is not available on JVM")
