@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothProfile
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.quirks.BleQuirks
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/*
 * Extension functions for connection lifecycle management.
 * Separated from [AndroidPeripheral] to keep the facade under 300 lines.
 */

/**
 * Samsung quirk: certain Galaxy devices must be bonded BEFORE `connectGatt()`,
 * otherwise the connection fails silently or returns GATT 133.
 */
@OptIn(ExperimentalBleApi::class)
internal suspend fun AndroidPeripheral.ensureBondedIfRequired(options: ConnectionOptions) {
    if (!quirkRegistry.resolve(BleQuirks.BondBeforeConnect)) return
    if (peripheralContext.bondState.value != com.atruedev.kmpble.bonding.BondState.NotBonded) return
    if (options.bondingPreference == BondingPreference.None) return

    val bondTimeout = quirkRegistry.resolve(BleQuirks.BondStateTimeout)
    logEvent(BleLogEvent.BondEvent(identifier, "Quirk: bond-before-connect initiated"))
    try {
        withTimeout(bondTimeout) { bondManager.createBond() }
        logEvent(BleLogEvent.BondEvent(identifier, "Quirk: bond-before-connect succeeded"))
    } catch (_: TimeoutCancellationException) {
        logEvent(
            BleLogEvent.Error(
                identifier,
                "Quirk: bond-before-connect timed out after $bondTimeout, proceeding with connection",
                cause = null,
            ),
        )
    }
}

/**
 * Attempts GATT connection with device-specific retry behavior.
 *
 * Pixel devices commonly return GATT error 133 on the first attempt - a retry with
 * a short delay (1-1.5s) typically succeeds. The retry count and delay are sourced
 * from [QuirkRegistry] so each OEM gets appropriate handling.
 *
 * The effective timeout is `max(options.timeout, quirks.connectionTimeout)` so that
 * user-configured values are respected while still accommodating OEMs that need longer
 * timeouts (e.g. Huawei at 35s vs the 30s default).
 */
internal suspend fun AndroidPeripheral.connectWithRetry(options: ConnectionOptions) {
    val maxAttempts = quirkRegistry.resolve(BleQuirks.GattRetryCount)
    val retryDelay = quirkRegistry.resolve(BleQuirks.GattRetryDelay)
    val timeout = maxOf(options.timeout, quirkRegistry.resolve(BleQuirks.ConnectionTimeout))

    repeat(maxAttempts) { attempt ->
        if (attempt > 0) {
            logEvent(
                BleLogEvent.GattOperation(
                    identifier,
                    "Connection retry ${attempt + 1}/$maxAttempts after $retryDelay",
                    uuid = null,
                    status = null,
                ),
            )
        }

        peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
        peripheralContext.gattQueue.start(options.gattOperationTimeout)

        val deferred = slots.armConnect()
        val gatt = bridge.connect(options)
        if (gatt == null) {
            peripheralContext.processEvent(
                ConnectionEvent.ConnectionLost(ConnectionFailed("connectGatt returned null")),
            )
            slots.clearConnect()
            if (attempt < maxAttempts - 1) delay(retryDelay)
            return@repeat
        }

        try {
            withTimeout(timeout) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            bridge.disconnect()
            bridge.releaseGatt()
            peripheralContext.processEvent(
                ConnectionEvent.ConnectionLost(ConnectionFailed("Connection timeout after $timeout")),
            )
        } finally {
            slots.clearConnect()
        }

        if (peripheralContext.state.value is State.Connected) return

        if (attempt < maxAttempts - 1) {
            bridge.releaseGatt()
            delay(retryDelay)
        }
    }
}

internal suspend fun AndroidPeripheral.handleConnectionStateChanged(event: GattCallbackEvent.ConnectionStateChanged) {
    val status = event.status.toGattStatus()
    when (event.newState) {
        BluetoothProfile.STATE_CONNECTED -> handleLinkUp(status, event.status)
        BluetoothProfile.STATE_DISCONNECTED -> handleLinkDown(event.status)
    }
}

internal suspend fun AndroidPeripheral.handleLinkUp(
    status: com.atruedev.kmpble.error.GattStatus,
    rawStatus: Int,
) {
    if (!status.isSuccess()) {
        peripheralContext.processEvent(
            ConnectionEvent.ConnectionLost(ConnectionFailed("GATT status: $status", rawStatus)),
        )
        slots.completeConnect()
        return
    }

    peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
    if (!bondIfRequiredForLink()) return
    bridge.discoverServices()
}

/**
 * Returns false if the connection has been failed and the caller should not proceed
 * with discovery.
 */
internal suspend fun AndroidPeripheral.bondIfRequiredForLink(): Boolean {
    val pref = currentConnectionOptions?.bondingPreference
    if (pref != BondingPreference.Required) return true
    if (device.bondState == BluetoothDevice.BOND_BONDED) return true

    peripheralContext.processEvent(ConnectionEvent.BondRequired)
    val bondTimeout = quirkRegistry.resolve(BleQuirks.BondStateTimeout)
    val bonded =
        try {
            withTimeout(bondTimeout) { bondManager.createBond() }
        } catch (_: TimeoutCancellationException) {
            logEvent(
                BleLogEvent.Error(
                    identifier,
                    "Bond state change timed out after $bondTimeout",
                    cause = null,
                ),
            )
            false
        }

    if (!bonded) {
        peripheralContext.processEvent(
            ConnectionEvent.BondFailed(ConnectionFailed("Bonding rejected or timed out")),
        )
        slots.completeConnect()
        return false
    }

    if (quirkRegistry.resolve(BleQuirks.RefreshServicesOnBond)) {
        logEvent(
            BleLogEvent.GattOperation(
                identifier,
                "Quirk: refreshing GATT cache after bond",
                uuid = null,
                status = null,
            ),
        )
        bridge.refreshDeviceCache()
    }
    return true
}

internal suspend fun AndroidPeripheral.handleLinkDown(rawStatus: Int) {
    if (peripheralContext.state.value is State.Disconnecting.Requested) {
        peripheralContext.processEvent(ConnectionEvent.ConnectionLost(OperationFailed("disconnect")))
        slots.completeDisconnect()
    } else {
        peripheralContext.processEvent(
            ConnectionEvent.ConnectionLost(ConnectionLost("Remote disconnect", rawStatus)),
        )
    }
    onDisconnectCleanup()
    slots.completeConnect()
}

@OptIn(ExperimentalBleApi::class)
internal suspend fun AndroidPeripheral.connectInternal(options: ConnectionOptions) {
    checkNotClosed()
    reconnectionHandler.start(options)
    bondManager.start()

    withContext(peripheralContext.dispatcher) {
        currentConnectionOptions = options
        pairingRequestHandler.setHandler(options.pairingHandler)
        pairingRequestHandler.start()
        ensureBondedIfRequired(options)
        connectWithRetry(options)
    }
}

@OptIn(ExperimentalBleApi::class)
internal suspend fun AndroidPeripheral.disconnectInternal() {
    checkNotClosed()
    reconnectionHandler.stop()
    bondManager.stop()
    withContext(peripheralContext.dispatcher) {
        pairingRequestHandler.stop()
        if (peripheralContext.state.value is State.Disconnected) return@withContext
        peripheralContext.processEvent(ConnectionEvent.DisconnectRequested)
        val deferred = slots.armDisconnect()
        bridge.disconnect()

        try {
            withTimeout(DISCONNECT_TIMEOUT) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            peripheralContext.processEvent(
                ConnectionEvent.ConnectionLost(OperationFailed("Disconnect timeout")),
            )
        } finally {
            slots.clearDisconnect()
            bridge.releaseGatt()
        }
    }
}
