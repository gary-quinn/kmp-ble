package com.atruedev.kmpble.monitoring

/**
 * An incoming power change request from the connected peer.
 *
 * Emitted by [LePowerController.incomingPowerRequests] when the peer
 * requests that this device change its transmit power level.
 *
 * Platform notes:
 * - Android: The [android.bluetooth.BluetoothGattCallback.onConnectionUpdated]
 *   callback may report peer-requested changes (API 29+).
 * - iOS: CoreBluetooth does not surface incoming power requests.
 *
 * @property requestedDbm The transmit power level (dBm) the peer is requesting
 *   this device to use.
 */
public data class PeerPowerRequest(
    val requestedDbm: Int,
)
