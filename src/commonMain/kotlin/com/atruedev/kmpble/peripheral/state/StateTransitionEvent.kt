package com.atruedev.kmpble.peripheral.state

import com.atruedev.kmpble.error.BleError

internal sealed interface StateTransitionEvent {
    data object ConnectRequested : StateTransitionEvent

    data object LinkEstablished : StateTransitionEvent

    data object BondRequired : StateTransitionEvent

    data object BondSucceeded : StateTransitionEvent

    data class BondFailed(
        val error: BleError? = null,
    ) : StateTransitionEvent

    data object ServicesDiscovered : StateTransitionEvent

    data class DiscoveryFailed(
        val error: BleError? = null,
    ) : StateTransitionEvent

    data object ConfigurationComplete : StateTransitionEvent

    data class ConfigurationFailed(
        val error: BleError? = null,
    ) : StateTransitionEvent

    data object InsufficientAuthentication : StateTransitionEvent

    data object BondStateChanged : StateTransitionEvent

    data object ServiceChangedIndication : StateTransitionEvent

    data object DisconnectRequested : StateTransitionEvent

    data class ConnectionLost(
        val error: BleError? = null,
    ) : StateTransitionEvent

    data object RemoteDisconnected : StateTransitionEvent

    data object AdapterOff : StateTransitionEvent

    data object SupervisionTimeout : StateTransitionEvent

    data object RediscoverySucceeded : StateTransitionEvent

    data class RediscoveryFailed(
        val error: BleError? = null,
    ) : StateTransitionEvent

    data object BondChangeProcessed : StateTransitionEvent
}
