package com.atruedev.kmpble.adapter

import com.atruedev.kmpble.unsupportedBle

public actual fun BluetoothAdapter(): BluetoothAdapter = unsupportedBle("BluetoothAdapter")
