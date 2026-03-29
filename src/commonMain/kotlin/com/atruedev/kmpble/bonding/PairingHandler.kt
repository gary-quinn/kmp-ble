package com.atruedev.kmpble.bonding

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier

/**
 * Callback for responding to pairing requests that require user interaction.
 *
 * ## Platform Behavior
 *
 * - **Android**: The handler is invoked when `ACTION_PAIRING_REQUEST` is received.
 *   Returning a response suppresses the system pairing dialog.
 * - **iOS**: CoreBluetooth handles pairing via the system UI. The handler
 *   receives [PairingEvent] notifications for observability but cannot
 *   suppress or customize the system dialog.
 *
 * ## Example
 *
 * ```kotlin
 * val handler = PairingHandler { event ->
 *     when (event) {
 *         is PairingEvent.NumericComparison -> {
 *             val confirmed = showConfirmationDialog(event.numericValue)
 *             PairingResponse.Confirm(confirmed)
 *         }
 *         is PairingEvent.PasskeyRequest -> {
 *             val pin = promptUserForPin()
 *             PairingResponse.ProvidePin(pin)
 *         }
 *         is PairingEvent.JustWorksConfirmation ->
 *             PairingResponse.Confirm(true)
 *         is PairingEvent.OutOfBandDataRequest ->
 *             PairingResponse.ProvideOobData(retrieveOobData())
 *     }
 * }
 * ```
 */
@ExperimentalBleApi
public fun interface PairingHandler {
    public suspend fun onPairingEvent(event: PairingEvent): PairingResponse
}

/**
 * Pairing event requiring user interaction or acknowledgment.
 */
@ExperimentalBleApi
public sealed interface PairingEvent {
    public val deviceIdentifier: Identifier

    /**
     * Both devices display a 6-digit number. User must confirm they match.
     * Respond with [PairingResponse.Confirm].
     */
    public data class NumericComparison(
        override val deviceIdentifier: Identifier,
        val numericValue: Int,
    ) : PairingEvent

    /**
     * Remote device requests a passkey/PIN.
     * Respond with [PairingResponse.ProvidePin].
     */
    public data class PasskeyRequest(
        override val deviceIdentifier: Identifier,
    ) : PairingEvent

    /**
     * Just Works pairing — no user verification needed but handler is notified.
     * Respond with [PairingResponse.Confirm] (typically `true`).
     */
    public data class JustWorksConfirmation(
        override val deviceIdentifier: Identifier,
    ) : PairingEvent

    /**
     * Out-of-band data requested for pairing.
     * Respond with [PairingResponse.ProvideOobData].
     */
    public data class OutOfBandDataRequest(
        override val deviceIdentifier: Identifier,
    ) : PairingEvent

    /**
     * Passkey displayed on the remote device. Informational only.
     * No response required — respond with [PairingResponse.Confirm].
     */
    public data class PasskeyNotification(
        override val deviceIdentifier: Identifier,
        val passkey: Int,
    ) : PairingEvent
}

/**
 * Response to a [PairingEvent].
 */
@ExperimentalBleApi
public sealed interface PairingResponse {
    /** Confirm or reject pairing (for [PairingEvent.NumericComparison], [PairingEvent.JustWorksConfirmation]). */
    public data class Confirm(
        val accepted: Boolean,
    ) : PairingResponse

    /** Provide a PIN/passkey (for [PairingEvent.PasskeyRequest]). */
    public data class ProvidePin(
        val pin: Int,
    ) : PairingResponse

    /** Provide out-of-band data (for [PairingEvent.OutOfBandDataRequest]). */
    public data class ProvideOobData(
        val data: ByteArray,
    ) : PairingResponse {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ProvideOobData) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }
}
