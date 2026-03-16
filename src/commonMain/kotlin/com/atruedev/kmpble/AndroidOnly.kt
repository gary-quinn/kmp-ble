package com.atruedev.kmpble

@RequiresOptIn(
    message = "This API is only available on Android. " +
        "Use @OptIn(AndroidOnly::class) to acknowledge.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class AndroidOnly
