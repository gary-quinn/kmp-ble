package com.atruedev.kmpble.peripheral.internal

/**
 * Runs every step even when an earlier one throws, so one failing step cannot leak what
 * the later steps release. The first failure is rethrown after the last step, with any
 * later failures attached as suppressed.
 */
internal fun runTeardown(vararg steps: () -> Unit) {
    var failure: Throwable? = null
    for (step in steps) {
        try {
            step()
        } catch (e: Throwable) {
            val first = failure
            if (first == null) failure = e else first.addSuppressed(e)
        }
    }
    failure?.let { throw it }
}
