package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData

/**
 * Raw bluetooth LE advertising payload as a sequence of length-tag-value
 * structures (Core Spec Vol 3, Part C, §11).
 *
 * The variant tells the consumer how the bytes were obtained, which matters
 * any time byte-exact AD records are needed (vendor signature verification,
 * forensic logging, replay):
 *
 *  - [OnAir]: bytes captured directly from the controller. Bit-identical to
 *    the over-the-air payload.
 *  - [Reconstructed]: bytes re-encoded from a platform-parsed advertisement
 *    dictionary, faithful to every field the platform surfaces but not
 *    necessarily bit-identical to what was on air. Field order and any
 *    unparsed AD types are lost in translation.
 */
public sealed interface RawAdvertising {
    public val bytes: BleData

    public data class OnAir(
        override val bytes: BleData,
    ) : RawAdvertising

    public data class Reconstructed(
        override val bytes: BleData,
    ) : RawAdvertising
}
