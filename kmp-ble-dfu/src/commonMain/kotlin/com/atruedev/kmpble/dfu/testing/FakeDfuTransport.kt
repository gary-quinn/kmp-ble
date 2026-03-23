package com.atruedev.kmpble.dfu.testing

import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [DfuTransport] for testing DFU flows without BLE hardware.
 *
 * Queue responses with [enqueueResponse] before the code-under-test calls
 * [sendCommand]. After the DFU completes, inspect [getCommandLog] and
 * [getDataLog] to verify the commands and data packets that were sent.
 *
 * ## Usage
 * ```
 * val transport = FakeDfuTransport(mtu = 64)
 * // Queue the expected DFU responses...
 * transport.enqueueResponse(selectResponse)
 * transport.enqueueResponse(createResponse)
 * // ...then run the protocol under test
 * ```
 *
 * @param mtu simulated maximum write payload size
 */
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

    /** Queue a response that will be returned by the next [sendCommand] call. */
    public suspend fun enqueueResponse(response: ByteArray) {
        responseQueue.send(response)
    }

    /** Emit a notification as if it came from the DFU Control Point characteristic. */
    public suspend fun emitNotification(data: ByteArray) {
        _notifications.send(data)
    }

    /** Snapshot of all commands sent via [sendCommand], in order. */
    public suspend fun getCommandLog(): List<ByteArray> =
        mutex.withLock { commandLog.toList() }

    /** Snapshot of all data packets sent via [sendData], in order. */
    public suspend fun getDataLog(): List<ByteArray> =
        mutex.withLock { dataLog.toList() }

    /** Clear both command and data logs. */
    public suspend fun clearLogs() {
        mutex.withLock {
            commandLog.clear()
            dataLog.clear()
        }
    }
}
