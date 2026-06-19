package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent

/**
 * Accepts a [PairingHandler] for API parity with Android but does not
 * intercept pairing requests. CoreBluetooth manages pairing transparently
 * via the system UI and does not expose a programmatic pairing API.
 *
 * All mutable state is confined to [serialDispatcher] inherited from
 * [PeripheralContext][com.atruedev.kmpble.peripheral.internal.PeripheralContext].
 */
@ExperimentalBleApi
internal class IosPairingRequestHandler(
    private val identifier: Identifier,
) {
    private var handler: PairingHandler? = null

    fun setHandler(pairingHandler: PairingHandler?) {
        handler = pairingHandler
        if (pairingHandler != null) {
            logEvent(
                BleLogEvent.BondEvent(
                    identifier,
                    "PairingHandler set but iOS CoreBluetooth handles pairing " +
                        "transparently; programmatic pairing responses are not available.",
                ),
            )
        }
    }

    /** No-op: CoreBluetooth manages pairing lifecycle. */
    fun start() {}

    /** No-op: CoreBluetooth manages pairing lifecycle. */
    fun stop() {}

    /** No-op: CoreBluetooth manages pairing lifecycle. */
    fun closeSync() {}
}
