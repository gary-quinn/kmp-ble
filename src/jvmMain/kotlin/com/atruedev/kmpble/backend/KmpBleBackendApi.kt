package com.atruedev.kmpble.backend

/**
 * Marks the JVM backend SPI. Backend modules such as `kmp-ble-bluez` and `kmp-ble-macos`
 * implement it; application code uses the portable factories (`Scanner { }`,
 * `Advertisement.toPeripheral()`, `GattServer { }`) instead.
 *
 * The SPI may change between minor releases without deprecation.
 */
@RequiresOptIn(
    message = "kmp-ble JVM backend SPI. Intended for backend modules, not application code.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class KmpBleBackendApi
