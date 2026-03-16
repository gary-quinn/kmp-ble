package com.atruedev.kmpble.adapter

public actual fun BluetoothAdapter(): BluetoothAdapter =
    IosBluetoothAdapter()
