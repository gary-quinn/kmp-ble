package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.unsupportedBle

public actual fun Scanner(configure: ScannerConfig.() -> Unit): Scanner = unsupportedBle("Scanner")
