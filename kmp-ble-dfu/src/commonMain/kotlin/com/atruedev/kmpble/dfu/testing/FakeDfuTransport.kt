package com.atruedev.kmpble.dfu.testing

import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class FakeDfuTransport(
    override val mtu: Int = 20,
) : DfuTransport {

    private val _notifications = Channel<ByteArray>(Channel.BUFFERED)
    override val notifications: Flow<ByteArray> = _notifications.receiveAsFlow()

    private val mutex = Mutex()
    private val commandLog = mutableListOf<ByteArray>()
    private val dataLog = mutableListOf<ByteArray>()
    private val responseQueue = Channel<ByteArray>(Channel.BUFFERED)
    private var _closed = false

    override suspend fun sendCommand(data: ByteArray): ByteArray {
        mutex.withLock { commandLog.add(data.copyOf()) }
        return responseQueue.receive()
    }

    override suspend fun sendData(data: ByteArray) {
        mutex.withLock { dataLog.add(data.copyOf()) }
    }

    override fun close() {
        if (_closed) return
        _closed = true
        _notifications.close()
        responseQueue.close()
    }

    public suspend fun enqueueResponse(response: ByteArray) {
        responseQueue.send(response)
    }

    public suspend fun emitNotification(data: ByteArray) {
        _notifications.send(data)
    }

    public suspend fun getCommandLog(): List<ByteArray> =
        mutex.withLock { commandLog.toList() }

    public suspend fun getDataLog(): List<ByteArray> =
        mutex.withLock { dataLog.toList() }

    public suspend fun clearLogs() {
        mutex.withLock {
            commandLog.clear()
            dataLog.clear()
        }
    }
}
