@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.l2cap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

internal class AndroidL2capListener(
    private val context: Context,
) : L2capListener {
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null

    private val closed = atomic(false)

    override suspend fun open(
        secure: Boolean,
        mtu: Int?,
    ) {
        if (closed.value) throw L2capException.InvalidState("Listener has been closed")
        if (_isOpen.value) throw L2capException.InvalidState("Listener already open")

        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: throw L2capException.NotSupported("Bluetooth adapter unavailable")

        val socket =
            withContext(Dispatchers.IO) {
                try {
                    if (secure) adapter.listenUsingL2capChannel() else adapter.listenUsingInsecureL2capChannel()
                } catch (e: IOException) {
                    throw L2capException.PublishFailed(
                        "BluetoothAdapter.listenUsingL2capChannel failed: ${e.message}",
                        e,
                    )
                } catch (e: SecurityException) {
                    throw L2capException.PublishFailed(
                        "Missing BLUETOOTH_CONNECT permission for listenUsingL2capChannel",
                        e,
                    )
                }
            }

        val assignedPsm = socket.psm
        serverSocket = socket
        _psm.value = assignedPsm
        _isOpen.value = true

        acceptJob = scope.launch { acceptLoop(socket, assignedPsm) }
    }

    private suspend fun acceptLoop(
        serverSocket: BluetoothServerSocket,
        assignedPsm: Int,
    ) {
        try {
            while (coroutineContext[Job]?.isActive == true && !closed.value) {
                val accepted =
                    try {
                        serverSocket.accept()
                    } catch (_: IOException) {
                        break
                    }

                if (closed.value) {
                    try {
                        accepted.close()
                    } catch (_: IOException) {
                    }
                    break
                }

                val channelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val channel =
                    AndroidL2capChannel(
                        socket = BluetoothL2capSocket(accepted),
                        psm = assignedPsm,
                        scope = channelScope,
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
        } finally {
            _isOpen.value = false
        }
    }

    override fun close() {
        if (closed.value) return
        closed.value = true
        _isOpen.value = false

        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null

        acceptJob?.cancel()
        acceptJob = null

        scope.cancel()
    }
}
