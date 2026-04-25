package com.atruedev.kmpble.bonding

/**
 * Result of a [com.atruedev.kmpble.peripheral.Peripheral.removeBond] operation.
 *
 * Bond removal is an Android-only capability - iOS will always return [NotSupported].
 */
public sealed interface BondRemovalResult {
    /** The bond was successfully removed. */
    public data object Success : BondRemovalResult

    /** Bond removal is not supported on this platform (iOS). */
    public data class NotSupported(
        val message: String,
    ) : BondRemovalResult

    /** Bond removal failed for the given [reason]. */
    public data class Failed(
        val reason: String,
    ) : BondRemovalResult
}
