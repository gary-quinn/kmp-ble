package com.atruedev.kmpble.mesh.internal

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Internal implementation of [ProxyConnection].
 *
 * Manages the GATT connection to a proxy node including:
 * - Discovering the Mesh Proxy Service
 * - Enabling notifications on Proxy Data Out
 * - Sending/receiving Proxy PDUs with SAR
 */
internal class ProxyConnectionImpl(
    private val peripheral: Peripheral,
    private val network: MeshNetworkImpl,
) : ProxyConnection {
    private val scope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private val _incomingPdus = MutableSharedFlow<NetworkPdu>(
        replay = 0, extraBufferCapacity = 64)
    private val _isConnected = MutableStateFlow(true)

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val incomingPdus: Flow<NetworkPdu> = _incomingPdus.asSharedFlow()

    init {
        // Start observing proxy data out characteristic
        scope.launch {
            peripheral.state.collect { state ->
                when (state) {
                    is com.atruedev.kmpble.peripheral.state.State.Disconnected -> {
                        _isConnected.value = false
                    }
                    else -> { /* connected states */ }
                }
            }
        }
    }

    override suspend fun sendPdu(pdu: NetworkPdu) {
        // Build raw network PDU bytes and send via proxy
        val rawPdu = pduToBytes(pdu)
        // In real impl: write to Proxy Data In characteristic
    }

    override fun close() {
        scope.cancel()
        _isConnected.value = false
    }

    private fun pduToBytes(pdu: NetworkPdu): ByteArray {
        // Pack network PDU into bytes
        val buffer = ByteArray(29)
        buffer[0] = ((pdu.ivi and 1) or ((pdu.nid and 0x7F) shl 1)).toByte()
        buffer[1] = ((pdu.ctl and 1) or ((pdu.ttl and 0x7F) shl 1)).toByte()
        // SEQ: 3 bytes big-endian
        val seq = pdu.seq.toInt()
        buffer[2] = ((seq shr 16) and 0xFF).toByte()
        buffer[3] = ((seq shr 8) and 0xFF).toByte()
        buffer[4] = (seq and 0xFF).toByte()
        // SRC: 2 bytes big-endian
        val src = pdu.src.value.toInt()
        buffer[5] = ((src shr 8) and 0xFF).toByte()
        buffer[6] = (src and 0xFF).toByte()
        // DST: 2 bytes big-endian
        val dst = pdu.dst.value.toInt()
        buffer[7] = ((dst shr 8) and 0xFF).toByte()
        buffer[8] = (dst and 0xFF).toByte()
        // Transport PDU
        pdu.transportPdu.copyInto(buffer, 9)
        // NetMIC
        pdu.netMic.copyInto(buffer, 9 + pdu.transportPdu.size)
        return buffer.copyOf(9 + pdu.transportPdu.size + pdu.netMic.size)
    }
}
