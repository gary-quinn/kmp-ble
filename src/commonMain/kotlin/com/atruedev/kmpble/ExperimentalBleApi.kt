package com.atruedev.kmpble

/**
 * Marks BLE APIs that are experimental and may change or be removed in future releases.
 *
 * Opt in with `@OptIn(ExperimentalBleApi::class)` to acknowledge instability.
 */
@RequiresOptIn(
    message = "This API is experimental and may change or be removed in future versions.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class ExperimentalBleApi
