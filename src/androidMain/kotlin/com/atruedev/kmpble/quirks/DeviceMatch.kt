package com.atruedev.kmpble.quirks

/**
 * Hierarchical device matching for quirk resolution.
 *
 * Keys use colon-separated segments: `"manufacturer"`, `"manufacturer:model"`,
 * `"manufacturer:model:display"`. Matching tries most specific first.
 */
public object DeviceMatch {

    /** Captures Samsung SM-XXXX series prefixes (e.g. "sm-g99" for Galaxy S21 series). */
    public const val MODEL_PREFIX_LENGTH: Int = 6

    public fun matchesAny(device: DeviceInfo, entries: Set<String>): Boolean =
        device.matchKeys.any { it in entries }

    public fun <T> matchFirst(device: DeviceInfo, entries: Map<String, T>): T? =
        device.matchKeys.firstNotNullOfOrNull { entries[it] }
}
