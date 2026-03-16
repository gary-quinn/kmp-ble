package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.KmpBle

public actual fun Scanner(configure: ScannerConfig.() -> Unit): Scanner =
    AndroidScanner(KmpBle.requireContext(), configure)
