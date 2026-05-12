@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.internal.PeripheralManagerProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBL2CAPPSM

internal class IosL2capListener : L2capListener {
    private val _isOpen = MutableStateFlow(false)
    override val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private var _psm: Int = 0
    override val psm: Int get() = _psm

    private val _incoming = MutableSharedFlow<L2capChannel>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incoming: Flow<L2capChannel> = _incoming.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var publishedPsm: CBL2CAPPSM = 0u
    private var closed: Boolean = false

    override suspend fun open(secure: Boolean) {
        if (closed) throw L2capException.InvalidState("Listener has been closed")
        if (_isOpen.value) throw L2capException.InvalidState("Listener already open")

        val delegate = PeripheralManagerProvider.delegate
        val manager = PeripheralManagerProvider.manager

        val publishResult = CompletableDeferred<CBL2CAPPSM>()
        delegate.onPublishL2cap = { psmAssigned, error ->
            if (error != null) {
                publishResult.completeExceptionally(
                    L2capException.PublishFailed(error.localizedDescription),
                )
            } else {
                publishResult.complete(psmAssigned)
            }
        }

        delegate.onOpenL2capChannel = { cbChannel, error ->
            if (cbChannel != null && error == null) {
                val channelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val channel = IosL2capChannel(
                    cbChannel = cbChannel,
                    scope = channelScope,
                    mtu = DEFAULT_L2CAP_MTU,
                )
                if (!_incoming.tryEmit(channel)) {
                    channel.close()
                    channelScope.cancel()
                }
            }
        }

        manager.publishL2CAPChannelWithEncryption(secure)

        val assigned = try {
            publishResult.await()
        } catch (e: L2capException) {
            delegate.onPublishL2cap = null
            throw e
        }

        publishedPsm = assigned
        _psm = assigned.toInt()
        _isOpen.value = true
        delegate.onPublishL2cap = null
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

        scope.cancel()
    }
}
