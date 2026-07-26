package com.atruedev.kmpble.mesh.internal

import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.proxy.MeshProxyService
import com.atruedev.kmpble.mesh.proxy.ProxyProtocol
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Internal implementation of [ProxyConnection].
 *
 * Manages the GATT connection to a proxy node:
 * - Discovers the Mesh Proxy Service (UUID 0x1828)
 * - Finds Proxy Data In (0x2ADD) and Proxy Data Out (0x2ADE)
 * - Enables notifications on Proxy Data Out for incoming mesh messages
 * - Writes to Proxy Data In for outgoing mesh messages
 *
 * ## Proxy SAR
 *
 * Outgoing PDUs are segmented via [ProxyProtocol.segmentForGatt] when they
 * exceed the GATT MTU minus the 2-byte proxy header. Incoming segments are
 * reassembled via [ProxyProtocol.reassemble] before emission.
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

    /** Buffer for reassembling multi-segment incoming Proxy PDUs. */
    private val reassemblyBuffer = mutableListOf<ByteArray>()

    init {
        scope.launch {
            try {
                discoverAndSubscribe()
            } catch (e: Exception) {
                _isConnected.value = false
                MeshLogger.w(MeshLogger.TAG_PROXY,
                    "Failed to initialize proxy: ${e.message}", e)
            }
        }

        // Monitor peripheral connection state
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
        val rawPdu = pduToBytes(pdu)

        // Segment for GATT MTU if needed, then write each segment
        val proxyDataIn = peripheral.findCharacteristic(
            MeshProxyService.SERVICE_UUID, MeshProxyService.DATA_IN_UUID)
            ?: throw MeshException(ProxyConnectionFailed(
                "Proxy Data In characteristic not found"))

        val segments = ProxyProtocol.segmentForGatt(rawPdu, mtu = effectiveMtu())
        for (segment in segments) {
            peripheral.write(proxyDataIn, segment, WriteType.WithoutResponse)
        }
    }

    override fun close() {
        scope.cancel()
        _isConnected.value = false
    }

    // --- Private helpers ---

    /**
     * Discover the Mesh Proxy Service, find its characteristics, and
     * enable notifications on Proxy Data Out.
     */
    private suspend fun discoverAndSubscribe() {
        // Ensure services are discovered
        peripheral.refreshServices()

        // Find Proxy Data Out characteristic
        val dataOut = peripheral.findCharacteristic(
            MeshProxyService.SERVICE_UUID, MeshProxyService.DATA_OUT_UUID)
            ?: throw MeshException(ProxyConnectionFailed(
                "Mesh Proxy Service or Data Out characteristic not found"))

        // Enable notifications and observe incoming data
        peripheral.observe(dataOut).collect { observation ->
            when (observation) {
                is Observation.Value -> handleIncomingData(observation.data)
                is Observation.Disconnected -> { /* flow stays active on reconnect */ }
            }
        }
    }

    /**
     * Handle incoming raw data from the Proxy Data Out characteristic.
     *
     * Incoming data may be:
     * - A complete Proxy PDU (SAR type = COMPLETE)
     * - A segment of a multi-segment Proxy PDU (FIRST/CONTINUATION/LAST)
     */
    private fun handleIncomingData(rawData: ByteArray) {
        val proxyPdu = ProxyProtocol.decode(rawData) ?: return

        when (proxyPdu.sar) {
            ProxySarType.COMPLETE -> {
                // Single complete PDU -- emit directly
                val networkPdu = bytesToPdu(proxyPdu.data)
                if (networkPdu != null) {
                    _incomingPdus.tryEmit(networkPdu)
                }
            }
            ProxySarType.FIRST -> {
                // Start of a multi-segment message
                reassemblyBuffer.clear()
                reassemblyBuffer.add(rawData)
            }
            ProxySarType.CONTINUATION -> {
                reassemblyBuffer.add(rawData)
            }
            ProxySarType.LAST -> {
                reassemblyBuffer.add(rawData)
                val reassembled = ProxyProtocol.reassemble(reassemblyBuffer.toList())
                reassemblyBuffer.clear()
                if (reassembled != null) {
                    val networkPdu = bytesToPdu(reassembled)
                    if (networkPdu != null) {
                        _incomingPdus.tryEmit(networkPdu)
                    }
                }
            }
        }
    }

    /** Convert a NetworkPdu to raw bytes for transmission. */
    private fun pduToBytes(pdu: NetworkPdu): ByteArray {
        val buffer = ByteArray(29)
        buffer[0] = ((pdu.ivi and 1) or ((pdu.nid and 0x7F) shl 1)).toByte()
        buffer[1] = ((pdu.ctl and 1) or ((pdu.ttl and 0x7F) shl 1)).toByte()
        val seq = pdu.seq.toInt()
        buffer[2] = ((seq shr 16) and 0xFF).toByte()
        buffer[3] = ((seq shr 8) and 0xFF).toByte()
        buffer[4] = (seq and 0xFF).toByte()
        val src = pdu.src.value.toInt()
        buffer[5] = ((src shr 8) and 0xFF).toByte()
        buffer[6] = (src and 0xFF).toByte()
        val dst = pdu.dst.value.toInt()
        buffer[7] = ((dst shr 8) and 0xFF).toByte()
        buffer[8] = (dst and 0xFF).toByte()
        pdu.transportPdu.copyInto(buffer, 9)
        pdu.netMic.copyInto(buffer, 9 + pdu.transportPdu.size)
        return buffer.copyOf(9 + pdu.transportPdu.size + pdu.netMic.size)
    }

    /** Parse raw bytes into a NetworkPdu. Returns null if data is malformed. */
    private fun bytesToPdu(data: ByteArray): NetworkPdu? {
        if (data.size < 9) return null
        val ivi = data[0].toInt() and 1
        val nid = (data[0].toInt() and 0xFE) shr 1
        val ctl = data[1].toInt() and 1
        val ttl = (data[1].toInt() and 0xFE) shr 1
        val seq = ((data[2].toInt() and 0xFF) shl 16) or
            ((data[3].toInt() and 0xFF) shl 8) or
            (data[4].toInt() and 0xFF)
        val src = MeshAddress.UnicastAddress(
            (((data[5].toInt() and 0xFF) shl 8) or
                (data[6].toInt() and 0xFF)).toUShort())
        val dstValue = (((data[7].toInt() and 0xFF) shl 8) or
            (data[8].toInt() and 0xFF)).toUShort()
        val dst = try {
            MeshAddress.fromValue(dstValue)
        } catch (_: Exception) {
            return null
        }
        // Transport PDU is bytes 9..(len-4), NetMIC is last 4 bytes
        val payloadSize = data.size - 9 - 4
        if (payloadSize < 0) return null
        val transportPdu = data.copyOfRange(9, 9 + payloadSize)
        val netMic = data.copyOfRange(9 + payloadSize, data.size)

        return NetworkPdu(
            ivi = ivi, nid = nid, ctl = ctl, ttl = ttl,
            seq = seq.toUInt(), src = src, dst = dst,
            transportPdu = transportPdu, netMic = netMic,
        )
    }

    /** Effective ATT MTU for proxy PDU segmentation. */
    private fun effectiveMtu(): Int = 69 // Default: BLE 4.2+ MTU of 72 - 3 ATT header
}
