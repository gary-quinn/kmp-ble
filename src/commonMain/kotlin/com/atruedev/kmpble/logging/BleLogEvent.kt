package com.atruedev.kmpble.logging

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.error.GattStatus
import kotlin.uuid.Uuid

public sealed interface BleLogEvent {
    public data class ScanStarted(val filterCount: Int) : BleLogEvent
    public data class ScanStopped(val reason: String) : BleLogEvent
    public data class AdvertisementReceived(val identifier: Identifier, val name: String?, val rssi: Int) : BleLogEvent
    public data class StateTransition(val identifier: Identifier, val from: State, val to: State) : BleLogEvent
    public data class GattOperation(val identifier: Identifier, val operation: String, val uuid: Uuid?, val status: GattStatus?) : BleLogEvent
    public data class DataTransfer(val identifier: Identifier, val direction: Direction, val uuid: Uuid, val bytes: Int) : BleLogEvent
    public data class BondEvent(val identifier: Identifier, val event: String) : BleLogEvent
    public data class Error(val identifier: Identifier?, val message: String, val cause: Throwable?) : BleLogEvent
}

public enum class Direction {
    Read,
    Write,
    Notify,
}
