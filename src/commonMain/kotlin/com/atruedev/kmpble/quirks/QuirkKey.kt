package com.atruedev.kmpble.quirks

/**
 * Typed identifier for a device quirk.
 * Well-known keys are defined in [BleQuirks]. Uses reference equality for matching.
 *
 * @param describe formats a resolved value for diagnostics; return `null` to omit from [QuirkRegistry.describe].
 *   Receives the resolved value and the [default] for comparison.
 */
public class QuirkKey<T : Any>(
    public val name: String,
    public val default: T,
    private val describeImpl: (value: T, default: T) -> String? = { _, _ -> null },
) {
    internal fun describe(value: T): String? = describeImpl(value, default)

    override fun toString(): String = "QuirkKey($name)"
}
