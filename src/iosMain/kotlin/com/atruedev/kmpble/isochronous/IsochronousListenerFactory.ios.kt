package com.atruedev.kmpble.isochronous

public actual fun IsochronousListener(): IsochronousListener =
    throw IsochronousException.NotSupported(
        "LE Audio isochronous listeners are not available on iOS",
    )
