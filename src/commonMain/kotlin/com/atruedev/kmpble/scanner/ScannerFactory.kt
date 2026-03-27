package com.atruedev.kmpble.scanner

/**
 * Creates a platform-specific [Scanner] with optional [configure] block.
 *
 * @param configure DSL block to set scan filters, timeout, and emission policy.
 */
public expect fun Scanner(configure: ScannerConfig.() -> Unit = {}): Scanner
