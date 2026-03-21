package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.unsupportedBle

public actual fun Advertisement.toPeripheral(): Peripheral = unsupportedBle("Peripheral")
