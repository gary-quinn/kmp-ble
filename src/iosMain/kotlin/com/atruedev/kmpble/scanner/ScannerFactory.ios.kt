package com.atruedev.kmpble.scanner

public actual fun Scanner(configure: ScannerConfig.() -> Unit): Scanner =
    IosScanner(configure)
