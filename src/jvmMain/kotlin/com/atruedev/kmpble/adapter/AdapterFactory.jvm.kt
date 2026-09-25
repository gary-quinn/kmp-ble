@file:OptIn(KmpBleBackendApi::class)

package com.atruedev.kmpble.adapter

import com.atruedev.kmpble.backend.BleBackends
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.newBluetoothAdapter

public actual fun BluetoothAdapter(): BluetoothAdapter = BleBackends.require("BluetoothAdapter").newBluetoothAdapter()
