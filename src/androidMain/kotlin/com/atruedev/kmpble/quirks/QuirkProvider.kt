package com.atruedev.kmpble.quirks

/**
 * SPI for contributing device quirks. Discovered at runtime via [java.util.ServiceLoader].
 * Implementations register typed entries on the [QuirkRegistry.Builder].
 */
public interface QuirkProvider {
    public fun contribute(builder: QuirkRegistry.Builder)
}
