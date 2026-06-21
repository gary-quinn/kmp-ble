package com.atruedev.kmpble.quirks

/**
 * SPI for contributing device quirks.
 * Implementations register typed entries on the [QuirkRegistry.Builder].
 */
public interface QuirkProvider {
    public fun contribute(builder: QuirkRegistry.Builder)
}
