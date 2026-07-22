package com.atruedev.kmpble.mesh.provisioning

/**
 * Out-of-Band authentication methods for BLE Mesh provisioning.
 *
 * OOB authentication provides MITM protection during provisioning:
 * - **None**: No OOB (AuthValue = 0x0000...). Development only.
 * - **StaticOob**: Pre-shared 16-byte key. Moderate security.
 * - **OutputOob**: Device outputs a number, user enters on provisioner.
 * - **InputOob**: Provisioner displays number, user enters on device.
 */
public sealed interface OobAuthentication {
    /** No OOB authentication. AuthValue is all zeros. Vulnerable to MITM. */
    public data object None : OobAuthentication

    /** Static 16-byte OOB key pre-shared between provisioner and device. */
    public data class StaticOob(val key: ByteArray) : OobAuthentication {
        init { require(key.size == 16) { "Static OOB key must be 16 bytes" } }
    }

    /** Device outputs a value, user enters it on the provisioner. */
    public data class OutputOob(
        val action: OutputOobAction,
        val size: Int,
    ) : OobAuthentication {
        init { require(size in 1..8) { "Output OOB size must be 1-8" } }
    }

    /** Provisioner outputs a value, user enters it on the device. */
    public data class InputOob(
        val action: InputOobAction,
        val size: Int,
    ) : OobAuthentication {
        init { require(size in 1..8) { "Input OOB size must be 1-8" } }
    }
}

/** Actions a device can perform for Output OOB. */
public enum class OutputOobAction(public val code: Int) {
    BLINK(0), BEEP(1), VIBRATE(2),
    DISPLAY_NUMERIC(3), DISPLAY_ALPHANUMERIC(4),
}

/** Actions a provisioner can request for Input OOB. */
public enum class InputOobAction(public val code: Int) {
    PUSH(0), TWIST(1),
    INPUT_NUMERIC(2), INPUT_ALPHANUMERIC(3),
}

/** Device OOB capabilities from provisioning beacon. */
public data class OobInfo(
    val staticOobSupported: Boolean = false,
    val outputOobActions: Set<OutputOobAction> = emptySet(),
    val outputOobSize: Int = 0,
    val inputOobActions: Set<InputOobAction> = emptySet(),
    val inputOobSize: Int = 0,
)
