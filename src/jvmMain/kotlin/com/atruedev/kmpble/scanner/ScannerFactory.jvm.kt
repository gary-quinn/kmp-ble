package com.atruedev.kmpble.scanner

public actual fun Scanner(configure: ScannerConfig.() -> Unit): Scanner =
    throw UnsupportedOperationException("BLE scanning is not available on JVM")
