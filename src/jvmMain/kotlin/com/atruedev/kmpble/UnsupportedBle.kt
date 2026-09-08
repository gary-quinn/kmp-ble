package com.atruedev.kmpble

internal fun unsupportedBle(operation: String): Nothing =
    throw UnsupportedOperationException("$operation is not supported on this JVM target")
