@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.l2cap.AndroidL2capChannel
import com.atruedev.kmpble.l2cap.BluetoothL2capSocket
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * L2CAP channel management for [AndroidPeripheral].
 */

internal val L2CAP_OPEN_TIMEOUT = 30.seconds

/**
 * Open an L2CAP Connection-Oriented Channel.
 *
 * Requires Android 10 (API 29) or higher. [secure]=true uses
 * `createL2capChannel` (encrypted); false uses `createInsecureL2capChannel`.
 * Blocking socket I/O runs on [Dispatchers.IO].
 */
internal suspend fun AndroidPeripheral.openL2capChannelInternal(
    psm: Int,
    secure: Boolean,
    mtu: Int?,
): L2capChannel {
    checkNotClosed()
    if (mtu != null) require(mtu > 0) { "mtu must be positive, was $mtu" }

    val current = state.value
    if (current !is ConnectionState.Connected.Ready) {
        throw L2capException.NotConnected("Peripheral is not connected and ready (state: $current)")
    }

    logEvent(
        BleLogEvent.GattOperation(
            identifier,
            "L2CAP open PSM=$psm secure=$secure",
            uuid = null,
            status = null,
        ),
    )

    return withContext(peripheralContext.dispatcher) {
        val socket =
            withContext(Dispatchers.IO) {
                if (secure) device.createL2capChannel(psm) else device.createInsecureL2capChannel(psm)
            }

        try {
            withContext(Dispatchers.IO) {
                withTimeout(currentTimeouts.l2capOpen) {
                    suspendCancellableCoroutine { cont ->
                        cont.invokeOnCancellation { socket.closeQuietly() }
                        try {
                            socket.connect()
                            cont.resume(Unit)
                        } catch (e: IOException) {
                            socket.closeQuietly()
                            cont.resumeWithException(
                                L2capException.OpenFailed(psm, "Failed to connect: ${e.message}", e),
                            )
                        }
                    }
                }
            }

            val channel = AndroidL2capChannel(BluetoothL2capSocket(socket), psm, peripheralContext.scope)
            activeL2capChannels.update { it + channel }

            peripheralContext.scope.launch {
                try {
                    channel.awaitClosed()
                } finally {
                    activeL2capChannels.update { it - channel }
                }
            }

            logEvent(
                BleLogEvent.GattOperation(
                    identifier,
                    "L2CAP opened PSM=$psm mtu=${channel.mtu}",
                    uuid = null,
                    status = null,
                ),
            )

            channel
        } catch (e: L2capException) {
            throw e
        } catch (e: CancellationException) {
            socket.closeQuietly()
            throw e
        } catch (e: IOException) {
            socket.closeQuietly()
            throw L2capException.OpenFailed(psm, e.message ?: "Unknown error", e)
        } catch (e: SecurityException) {
            socket.closeQuietly()
            throw L2capException.OpenFailed(psm, "Missing BLUETOOTH_CONNECT permission", e)
        }
    }
}

internal fun android.bluetooth.BluetoothSocket.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
    }
}

internal fun AndroidPeripheral.closeL2capChannels() {
    val channels = activeL2capChannels.getAndUpdate { emptyList() }
    if (channels.isNotEmpty()) {
        logEvent(
            BleLogEvent.GattOperation(
                identifier,
                "L2CAP closing ${channels.size} channel(s)",
                uuid = null,
                status = null,
            ),
        )
    }
    channels.forEach { it.close() }
}

internal fun phyToMask(phy: Phy): Int =
    when (phy) {
        Phy.Le1M -> BluetoothDevice.PHY_LE_1M_MASK
        Phy.Le2M -> BluetoothDevice.PHY_LE_2M_MASK
        Phy.LeCoded -> BluetoothDevice.PHY_LE_CODED_MASK
    }

internal fun phyConstantToPhy(value: Int): Phy =
    when (value) {
        BluetoothDevice.PHY_LE_2M -> Phy.Le2M
        BluetoothDevice.PHY_LE_CODED -> Phy.LeCoded
        else -> Phy.Le1M
    }
