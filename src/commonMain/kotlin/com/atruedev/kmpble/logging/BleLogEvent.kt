package com.atruedev.kmpble.logging

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.error.GattStatus
import kotlin.time.Duration
import kotlin.uuid.Uuid

public sealed interface BleLogEvent {

    /**
     * Human-readable log line for this event. Loggers can use this instead of
     * exhaustive `when` blocks — new event subtypes get a sensible format
     * automatically (OCP: loggers don't break when events are added).
     *
     * Computed on each access — log events are typically format-once-discard,
     * so a plain `get()` avoids the synchronization overhead of `lazy`.
     */
    public val formatted: String

    public data class ScanStarted(val filterCount: Int) : BleLogEvent {
        override val formatted: String get() = "[Scan] Started ($filterCount filters)"
    }
    public data class ScanStopped(val reason: String) : BleLogEvent {
        override val formatted: String get() = "[Scan] Stopped: $reason"
    }
    public data class AdvertisementReceived(val identifier: Identifier, val name: String?, val rssi: Int) : BleLogEvent {
        override val formatted: String get() = "[Scan] ${name ?: "Unknown"} (${identifier.value}) rssi=$rssi"
    }

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
    ) : BleLogEvent {
        override val formatted: String get() {
            val dur = durationInPreviousState.inWholeMilliseconds
            return "[${identifier.value}] ${from.displayName} → ${to.displayName} (${dur}ms in previous)"
        }
    }
    public data class GattOperation(val identifier: Identifier, val operation: String, val uuid: Uuid?, val status: GattStatus?) : BleLogEvent {
        override val formatted: String get() = "[${identifier.value}] $operation uuid=$uuid status=$status"
    }
    public data class DataTransfer(val identifier: Identifier, val direction: Direction, val uuid: Uuid, val bytes: Int) : BleLogEvent {
        override val formatted: String get() = "[${identifier.value}] $direction uuid=$uuid $bytes bytes"
    }
    public data class BondEvent(val identifier: Identifier, val event: String) : BleLogEvent {
        override val formatted: String get() = "[${identifier.value}] Bond: $event"
    }
    public data class Error(val identifier: Identifier?, val message: String, val cause: Throwable?) : BleLogEvent {
        override val formatted: String get() = "[${identifier?.value ?: "global"}] ERROR: $message"
    }

    public data class StateRestoration(val identifier: Identifier?, val event: String) : BleLogEvent {
        override val formatted: String get() = "[StateRestoration] ${identifier?.value ?: "global"}: $event"
    }

    public data class ServerLifecycle(val event: String) : BleLogEvent {
        override val formatted: String get() = "[Server] $event"
    }
    public data class ServerClientEvent(val device: Identifier, val event: String) : BleLogEvent {
        override val formatted: String get() = "[Server] ${device.value}: $event"
    }
    public data class ServerRequest(val device: Identifier, val operation: String, val uuid: Uuid?, val status: GattStatus?) : BleLogEvent {
        override val formatted: String get() = "[Server] ${device.value} $operation uuid=$uuid status=$status"
    }
}

public enum class Direction {
    Read,
    Write,
    Notify,
}
