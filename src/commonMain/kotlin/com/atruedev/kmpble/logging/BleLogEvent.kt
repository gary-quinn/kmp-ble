package com.atruedev.kmpble.logging

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.error.GattStatus
import kotlin.time.Duration
import kotlin.uuid.Uuid

public sealed interface BleLogEvent {
    public data class ScanStarted(val filterCount: Int) : BleLogEvent
    public data class ScanStopped(val reason: String) : BleLogEvent
    public data class AdvertisementReceived(val identifier: Identifier, val name: String?, val rssi: Int) : BleLogEvent

    /**
     * Connection state transition with duration tracking.
     * [durationInPreviousState] is how long the peripheral spent in [from] before
     * transitioning to [to]. Enables connection timeline analysis:
     * "Transport 1.1s → Discovering 0.8s → Configuring 0.3s → Ready"
     */
    public data class StateTransition(
        val identifier: Identifier,
        val from: State,
        val to: State,
        val durationInPreviousState: Duration = Duration.ZERO,
    ) : BleLogEvent
    public data class GattOperation(val identifier: Identifier, val operation: String, val uuid: Uuid?, val status: GattStatus?) : BleLogEvent
    public data class DataTransfer(val identifier: Identifier, val direction: Direction, val uuid: Uuid, val bytes: Int) : BleLogEvent
    public data class BondEvent(val identifier: Identifier, val event: String) : BleLogEvent
    public data class Error(val identifier: Identifier?, val message: String, val cause: Throwable?) : BleLogEvent

    // State restoration events
    public data class StateRestoration(val identifier: Identifier?, val event: String) : BleLogEvent

    // Server events
    public data class ServerLifecycle(val event: String) : BleLogEvent
    public data class ServerClientEvent(val device: Identifier, val event: String) : BleLogEvent
    public data class ServerRequest(val device: Identifier, val operation: String, val uuid: Uuid?, val status: GattStatus?) : BleLogEvent
}

public enum class Direction {
    Read,
    Write,
    Notify,
}
