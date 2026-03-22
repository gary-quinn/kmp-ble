package com.atruedev.kmpble.adapter

import com.atruedev.kmpble.KmpBle

public actual fun BluetoothAdapter(): BluetoothAdapter = AndroidBluetoothAdapter(KmpBle.requireContext())
