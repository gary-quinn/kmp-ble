package com.atruedev.kmpble.dfu.protocol.smp

internal object SmpOp {
    const val READ = 0
    const val READ_RSP = 1
    const val WRITE = 2
    const val WRITE_RSP = 3
}

internal object SmpGroup {
    const val OS_MGMT = 0
    const val IMAGE_MGMT = 1
}

internal object SmpCommand {
    const val IMAGE_STATE = 0
    const val IMAGE_UPLOAD = 1
    const val OS_RESET = 5
}
