package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.l2cap.DEFAULT_L2CAP_MTU
import com.atruedev.kmpble.l2cap.IosL2capChannel
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBL2CAPChannel

/**
 * L2CAP channel management for [IosPeripheral].
 */

internal suspend fun IosPeripheral.openL2capChannelInternal(
    psm: Int,
    secure: Boolean,
    mtu: Int?,
): L2capChannel {
    checkNotClosed()
    if (mtu != null) require(mtu > 0) { "mtu must be positive, was $mtu" }
    if (peripheralContext.state.value !is State.Connected) {
        throw L2capException.NotConnected("Peripheral is not connected (state: ${peripheralContext.state.value})")
    }

    return withContext(peripheralContext.dispatcher) {
        if (pendingL2capChannel != null) {
            throw L2capException.OpenFailed(psm, "Another L2CAP channel open is already in progress")
        }
        val deferred = CompletableDeferred<CBL2CAPChannel>()
        pendingL2capChannel = deferred
        bridge.openL2CAPChannel(psm.toUShort())

        try {
            val cbChannel = withTimeout(currentTimeouts.l2capOpen) { deferred.await() }
            val channel = IosL2capChannel(cbChannel, peripheralContext.scope, mtu ?: DEFAULT_L2CAP_MTU)
            activeL2capChannels.update { it + channel }
            channel
        } catch (_: TimeoutCancellationException) {
            pendingL2capChannel = null
            throw L2capException.OpenFailed(psm, "Timeout waiting for L2CAP channel")
        } catch (e: L2capException) {
            pendingL2capChannel = null
            throw e
        } catch (e: CancellationException) {
            pendingL2capChannel = null
            throw e
        } catch (e: Exception) {
            pendingL2capChannel = null
            throw L2capException.OpenFailed(psm, e.message ?: "Unknown error", e)
        }
    }
}

internal fun IosPeripheral.handleDidOpenL2CAPChannel(event: AppleCallbackEvent.DidOpenL2CAPChannel) {
    val deferred = pendingL2capChannel ?: return
    pendingL2capChannel = null

    when {
        event.error != null ->
            deferred.completeExceptionally(
                L2capException.OpenFailed(
                    psm = event.channel?.PSM?.toInt() ?: -1,
                    message = event.error.localizedDescription,
                ),
            )
        event.channel != null -> deferred.complete(event.channel)
        else ->
            deferred.completeExceptionally(
                L2capException.OpenFailed(psm = -1, message = "Channel is null with no error"),
            )
    }
}

internal fun IosPeripheral.closeL2capChannels() {
    activeL2capChannels.getAndUpdate { emptyList() }.forEach { it.close() }
}
