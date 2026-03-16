package com.atruedev.kmpble

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
