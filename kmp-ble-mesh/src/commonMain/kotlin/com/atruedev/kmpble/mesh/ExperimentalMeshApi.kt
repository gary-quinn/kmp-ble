package com.atruedev.kmpble.mesh

/**
 * Marks APIs that are experimental and may change without notice.
 *
 * Pattern matches [com.atruedev.kmpble.ExperimentalBleApi] from the core library.
 * APIs annotated with this require opt-in via `@OptIn(ExperimentalMeshApi::class)`
 * and may change in future releases.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This BLE Mesh API is experimental and may change in future releases.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalMeshApi
