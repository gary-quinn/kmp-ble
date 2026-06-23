@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.internal.PeripheralManagerProvider
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBL2CAPPSM
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

internal class IosL2capListener : L2capListener {
    private val _isOpen = MutableStateFlow(false)
    override val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private val _psm = atomic(0)
    override val psm: Int get() = _psm.value

    private val _incoming =
        MutableSharedFlow<L2capChannel>(
            replay = 0,
            extraBufferCapacity = 16,
        )
    override val incoming: SharedFlow<L2capChannel> = _incoming.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val acceptedChannels = Channel<CBL2CAPChannel>(Channel.BUFFERED)

    private val publishedPsm = atomic<UShort>(0.toUShort())

    private val closed = atomic(false)

    private var drainJob: Job? = null

    override suspend fun open(
        secure: Boolean,
        mtu: Int?,
    ) {
        if (closed.value) throw L2capException.InvalidState("Listener has been closed")
        if (_isOpen.value) throw L2capException.InvalidState("Listener already open")

        val delegate = PeripheralManagerProvider.delegate
        val manager = PeripheralManagerProvider.manager

        if (delegate.onPublishL2cap != null) {
            throw L2capException.InvalidState(
                "Another L2capListener is publishing; only one listener can publish at a time",
            )
        }
        if (delegate.onOpenL2capChannel != null) {
            throw L2capException.InvalidState("Another L2capListener is accepting connections")
        }

        val effectiveMtu = mtu ?: DEFAULT_L2CAP_MTU
        val publishResult = CompletableDeferred<CBL2CAPPSM>()

        delegate.onOpenL2capChannel = { cbChannel, error ->
            if (closed.value || cbChannel == null || error != null) {
                cbChannel?.inputStream?.close()
                cbChannel?.outputStream?.close()
            } else if (acceptedChannels.trySend(cbChannel).isFailure) {
                cbChannel.inputStream?.close()
                cbChannel.outputStream?.close()
            }
        }

        // Callback survives the publish timeout window: if
        // `publishL2CAPChannelWithEncryption` actually succeeded at the OS
        // level but the assignment notification arrives after we already
        // gave up, the callback still runs and unpublishes the PSM itself.
        // Otherwise the PSM would stay registered on CBPeripheralManager
        // until process exit.
        //
        // Degenerate case: if CoreBluetooth never delivers the callback at
        // all (broken HW/OS state), this slot stays held and the next
        // L2capListener.open() fails the require-check with InvalidState
        // until the process exits.
        delegate.onPublishL2cap = { psmAssigned, error ->
            when {
                error != null -> {
                    if (!publishResult.isCompleted) {
                        publishResult.completeExceptionally(
                            L2capException.PublishFailed(
                                "CBPeripheralManager publishL2CAPChannel error: ${error.localizedDescription}",
                            ),
                        )
                    }
                    delegate.onPublishL2cap = null
                }
                closed.value -> {
                    // Late successful delivery after open() already gave up - clean up.
                    try {
                        manager.unpublishL2CAPChannel(psmAssigned)
                    } catch (_: Throwable) {
                    }
                    delegate.onPublishL2cap = null
                }
                else -> {
                    publishedPsm.value = psmAssigned
                    publishResult.complete(psmAssigned)
                    // Success path clears the delegate slot explicitly below.
                }
            }
        }

        drainJob = scope.launch { drainAcceptedChannels(effectiveMtu) }

        val assigned: CBL2CAPPSM =
            try {
                withTimeout(PUBLISH_TIMEOUT) {
                    if (delegate.managerState.value != CBPeripheralManagerStatePoweredOn) {
                        delegate.managerState.first { it == CBPeripheralManagerStatePoweredOn }
                    }
                    manager.publishL2CAPChannelWithEncryption(secure)
                    publishResult.await()
                }
            } catch (e: Throwable) {
                // Mark closed BEFORE giving up on the callback so that any
                // late onPublishL2cap delivery sees `closed == true` and
                // unpublishes the assigned PSM itself.
                closed.value = true
                delegate.onOpenL2capChannel = null
                drainJob?.cancel()
                drainJob = null
                acceptedChannels.close()
                // Defensive: if the callback raced ahead and stored the PSM
                // before we set closed=true, drop it ourselves.
                if (publishedPsm.value != 0.toUShort()) {
                    try {
                        manager.unpublishL2CAPChannel(publishedPsm.value)
                    } catch (_: Throwable) {
                    }
                    publishedPsm.value = 0u
                }
                throw when (e) {
                    is L2capException -> e
                    is TimeoutCancellationException ->
                        L2capException.PublishFailed(
                            "publishL2CAPChannel timed out after $PUBLISH_TIMEOUT",
                            e,
                        )
                    else -> e
                }
            }

        // Clear the slot now so a subsequent listener's require-check sees a
        // clean delegate. The success-path branch of the callback intentionally
        // skips clearing to keep this single deterministic owner.
        delegate.onPublishL2cap = null

        publishedPsm.value = assigned
        _psm.value = assigned.toInt()
        _isOpen.value = true
    }

    private suspend fun drainAcceptedChannels(mtu: Int) {
        for (cbChannel in acceptedChannels) {
            if (closed.value) {
                cbChannel.inputStream?.close()
                cbChannel.outputStream?.close()
                continue
            }
            val channelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val channel =
                IosL2capChannel(
                    cbChannel = cbChannel,
                    scope = channelScope,
                    mtu = mtu,
                )
            channelScope.launch {
                channel.awaitClosed()
                channelScope.cancel()
            }
            try {
                _incoming.emit(channel)
            } catch (e: CancellationException) {
                channel.close()
                throw e
            }
        }
    }

    override fun close() {
        if (closed.value) return
        closed.value = true
        _isOpen.value = false

        val delegate = PeripheralManagerProvider.delegate
        val manager = PeripheralManagerProvider.manager
        delegate.onOpenL2capChannel = null

        if (publishedPsm.value != 0.toUShort()) {
            try {
                manager.unpublishL2CAPChannel(publishedPsm.value)
            } catch (_: Throwable) {
            }
        }

        // Drain buffered handoff queue before cancelling the scope, otherwise
        // the drainer can be cancelled at a suspension point with cbChannels
        // still in the buffer (input/output streams open, never closed).
        acceptedChannels.close()
        while (true) {
            val orphan = acceptedChannels.tryReceive().getOrNull() ?: break
            orphan.inputStream?.close()
            orphan.outputStream?.close()
        }
        scope.cancel()
    }

    private companion object {
        val PUBLISH_TIMEOUT = 10.seconds
    }
}
