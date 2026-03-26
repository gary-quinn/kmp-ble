package com.atruedev.kmpble

/**
 * Marks APIs that are only meaningful on Android.
 *
 * On iOS these APIs are available but return no-op or platform-default results.
 * Opt in with `@OptIn(AndroidOnly::class)` to acknowledge the platform restriction.
 */
@RequiresOptIn(
    message =
        "This API is only available on Android. " +
            "Use @OptIn(AndroidOnly::class) to acknowledge.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class AndroidOnly
