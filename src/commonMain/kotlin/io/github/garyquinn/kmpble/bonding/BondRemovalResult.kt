package io.github.garyquinn.kmpble.bonding

public sealed interface BondRemovalResult {
    public data object Success : BondRemovalResult
    public data class NotSupported(val message: String) : BondRemovalResult
    public data class Failed(val reason: String) : BondRemovalResult
}
