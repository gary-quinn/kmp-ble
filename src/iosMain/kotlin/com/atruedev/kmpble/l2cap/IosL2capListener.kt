@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.internal.PeripheralManagerProvider
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
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

internal class IosL2capListener : L2capListener {
    private val _isOpen = MutableStateFlow(false)
    override val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    @Volatile
    private var _psm: Int = 0
    override val psm: Int get() = _psm

    private val _incoming =
        MutableSharedFlow<L2capChannel>(
            replay = 0,
            extraBufferCapacity = 16,
        )
    override val incoming: SharedFlow<L2capChannel> = _incoming.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val acceptedChannels = Channel<CBL2CAPChannel>(Channel.BUFFERED)

    @Volatile
    private var publishedPsm: CBL2CAPPSM = 0u

    @Volatile
    private var closed: Boolean = false

    private var drainJob: Job? = null

    override suspend fun open(
        secure: Boolean,
        mtu: Int?,
    ) {
        if (closed) throw L2capException.InvalidState("Listener has been closed")
        if (_isOpen.value) throw L2capException.InvalidState("Listener already open")

        val delegate = PeripheralManagerProvider.delegate
        val manager = PeripheralManagerProvider.manager

        require(delegate.onPublishL2cap == null) {
            "Another L2capListener is publishing; only one listener can publish at a time"
        }
        require(delegate.onOpenL2capChannel == null) {
            "Another L2capListener is accepting connections"
        }

        val effectiveMtu = mtu ?: DEFAULT_L2CAP_MTU
        val publishResult = CompletableDeferred<CBL2CAPPSM>()

        delegate.onOpenL2capChannel = { cbChannel, error ->
            if (closed || cbChannel == null || error != null) {
                cbChannel?.inputStream?.close()
                cbChannel?.outputStream?.close()
            } else if (acceptedChannels.trySend(cbChannel).isFailure) {
                cbChannel.inputStream?.close()
                cbChannel.outputStream?.close()
            }
        }

        delegate.onPublishL2cap = { psmAssigned, error ->
            if (error != null) {
                publishResult.completeExceptionally(
                    L2capException.PublishFailed(
                        "CBPeripheralManager publishL2CAPChannel error: ${error.localizedDescription}",
                    ),
                )
            } else {
                publishResult.complete(psmAssigned)
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
                closed = true
                delegate.onOpenL2capChannel = null
                drainJob?.cancel()
                drainJob = null
                acceptedChannels.close()
                throw when (e) {
                    is L2capException -> e
                    is TimeoutCancellationException ->
                        L2capException.PublishFailed(
                            "publishL2CAPChannel timed out after $PUBLISH_TIMEOUT",
                            e,
                        )
                    else -> e
                }
            } finally {
                delegate.onPublishL2cap = null
            }

        publishedPsm = assigned
        _psm = assigned.toInt()
        _isOpen.value = true
    }

    private suspend fun drainAcceptedChannels(mtu: Int) {
        for (cbChannel in acceptedChannels) {
            if (closed) {
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
        if (closed) return
        closed = true
        _isOpen.value = false

        val delegate = PeripheralManagerProvider.delegate
        delegate.onOpenL2capChannel = null

        if (publishedPsm != 0.toUShort()) {
            try {
                PeripheralManagerProvider.manager.unpublishL2CAPChannel(publishedPsm)
            } catch (_: Throwable) {
            }
        }

        acceptedChannels.close()
        scope.cancel()
    }

    private companion object {
        val PUBLISH_TIMEOUT = 10.seconds
    }
}
