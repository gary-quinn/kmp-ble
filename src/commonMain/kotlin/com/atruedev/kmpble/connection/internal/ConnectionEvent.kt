package com.atruedev.kmpble.connection.internal

import com.atruedev.kmpble.error.BleError

internal sealed interface ConnectionEvent {
    data object ConnectRequested : ConnectionEvent

    data object LinkEstablished : ConnectionEvent

    data object BondRequired : ConnectionEvent

    data object BondSucceeded : ConnectionEvent

    data class BondFailed(
        val error: BleError? = null,
    ) : ConnectionEvent

    data object ServicesDiscovered : ConnectionEvent

    data class DiscoveryFailed(
        val error: BleError? = null,
    ) : ConnectionEvent

    data object ConfigurationComplete : ConnectionEvent

    data class ConfigurationFailed(
        val error: BleError? = null,
    ) : ConnectionEvent

    data object InsufficientAuthentication : ConnectionEvent

    data object BondStateChanged : ConnectionEvent

    data object ServiceChangedIndication : ConnectionEvent

    data object DisconnectRequested : ConnectionEvent

    data class ConnectionLost(
        val error: BleError? = null,
    ) : ConnectionEvent

    data object RemoteDisconnected : ConnectionEvent

    data object AdapterOff : ConnectionEvent

    data object SupervisionTimeout : ConnectionEvent

    data object RediscoverySucceeded : ConnectionEvent

    data class RediscoveryFailed(
        val error: BleError? = null,
    ) : ConnectionEvent

    data object BondChangeProcessed : ConnectionEvent
}
