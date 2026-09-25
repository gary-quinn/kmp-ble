package com.atruedev.kmpble.macos.internal

import java.nio.ByteBuffer

/** Raw `kind` values sent by the native shim. Must match the enum in `KmpBleMacos.m`. */
internal object MacosEventKind {
    const val CENTRAL_STATE = 1
    const val DISCOVERED = 2
    const val CONNECTED = 3
    const val CONNECT_FAILED = 4
    const val DISCONNECTED = 5
    const val SERVICES_DISCOVERED = 6
    const val CHARACTERISTICS_DISCOVERED = 7
    const val DESCRIPTORS_DISCOVERED = 8
    const val VALUE_UPDATED = 9
    const val VALUE_WRITTEN = 10
    const val DESCRIPTOR_VALUE = 11
    const val DESCRIPTOR_WRITTEN = 12
    const val NOTIFY_STATE = 13
    const val RSSI = 14
    const val READY_TO_WRITE = 15
    const val SERVICES_MODIFIED = 16
    const val L2CAP_OPENED = 17
    const val L2CAP_DATA = 18
    const val L2CAP_CLOSED = 19
    const val PM_STATE = 20
    const val PM_SERVICE_ADDED = 21
    const val PM_ADVERTISING_STARTED = 22
    const val PM_READ_REQUEST = 23
    const val PM_WRITE_REQUESTS = 24
    const val PM_SUBSCRIBED = 25
    const val PM_UNSUBSCRIBED = 26
    const val PM_READY_TO_UPDATE = 27
    const val PM_L2CAP_PUBLISHED = 28
    const val PM_L2CAP_OPENED = 29
}

/** Raw `CBManagerState` values. */
internal object CbManagerState {
    const val UNKNOWN = 0
    const val RESETTING = 1
    const val UNSUPPORTED = 2
    const val UNAUTHORIZED = 3
    const val POWERED_OFF = 4
    const val POWERED_ON = 5
}

/** One discovered attribute: `handle|uuid[|properties]` in the native payload. */
internal data class AttributeLine(
    val handle: Long,
    val uuid: String,
    val properties: Int = 0,
)

internal data class PmWrite(
    val characteristic: Long,
    val offset: Int,
    val value: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is PmWrite &&
            characteristic == other.characteristic &&
            offset == other.offset &&
            value.contentEquals(other.value)

    override fun hashCode(): Int = (31 * characteristic.hashCode() + offset) * 31 + value.contentHashCode()
}

/**
 * CoreBluetooth callbacks as plain values, mirroring `AppleCallbackEvent` on iOS but with
 * handles, status codes, and byte arrays instead of CoreBluetooth objects. `status` is 0 on
 * success, the ATT error code (1..255), `1000 + CBError` code, or -1 for other domains.
 */
internal sealed interface MacosEvent {
    data class CentralState(
        val state: Int,
    ) : MacosEvent

    class Discovered(
        val peripheral: Long,
        val identifier: String,
        val peripheralName: String?,
        val rssi: Int,
        val connectable: Boolean?,
        val advertising: ByteArray,
    ) : MacosEvent

    data class Connected(
        val peripheral: Long,
    ) : MacosEvent

    data class ConnectFailed(
        val peripheral: Long,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    data class Disconnected(
        val peripheral: Long,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    data class ServicesDiscovered(
        val peripheral: Long,
        val status: Int,
        val message: String?,
        val services: List<AttributeLine>,
    ) : MacosEvent

    data class CharacteristicsDiscovered(
        val peripheral: Long,
        val service: Long,
        val status: Int,
        val message: String?,
        val characteristics: List<AttributeLine>,
    ) : MacosEvent

    data class DescriptorsDiscovered(
        val peripheral: Long,
        val characteristic: Long,
        val status: Int,
        val message: String?,
        val descriptors: List<AttributeLine>,
    ) : MacosEvent

    class ValueUpdated(
        val peripheral: Long,
        val characteristic: Long,
        val status: Int,
        val message: String?,
        val value: ByteArray,
    ) : MacosEvent

    data class ValueWritten(
        val peripheral: Long,
        val characteristic: Long,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    class DescriptorValue(
        val peripheral: Long,
        val descriptor: Long,
        val status: Int,
        val message: String?,
        val value: ByteArray,
    ) : MacosEvent

    data class DescriptorWritten(
        val peripheral: Long,
        val descriptor: Long,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    data class NotifyState(
        val peripheral: Long,
        val characteristic: Long,
        val status: Int,
        val message: String?,
        val notifying: Boolean,
    ) : MacosEvent

    data class Rssi(
        val peripheral: Long,
        val rssi: Int,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    data class ReadyToWrite(
        val peripheral: Long,
    ) : MacosEvent

    data class ServicesModified(
        val peripheral: Long,
    ) : MacosEvent

    data class L2capOpened(
        val peripheral: Long,
        val channel: Long,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    class L2capData(
        val channel: Long,
        val data: ByteArray,
    ) : MacosEvent

    data class L2capClosed(
        val channel: Long,
        val status: Int,
    ) : MacosEvent

    data class PmState(
        val state: Int,
    ) : MacosEvent

    data class PmServiceAdded(
        val service: Long,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    data class PmAdvertisingStarted(
        val status: Int,
        val message: String?,
    ) : MacosEvent

    data class PmReadRequest(
        val request: Long,
        val characteristic: Long,
        val offset: Int,
        val central: String,
    ) : MacosEvent

    data class PmWriteRequests(
        val request: Long,
        val central: String,
        val writes: List<PmWrite>,
    ) : MacosEvent

    data class PmSubscribed(
        val characteristic: Long,
        val central: String,
        val maximumUpdateLength: Int,
    ) : MacosEvent

    data class PmUnsubscribed(
        val characteristic: Long,
        val central: String,
    ) : MacosEvent

    data object PmReadyToUpdate : MacosEvent

    data class PmL2capPublished(
        val psm: Int,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    data class PmL2capOpened(
        val psm: Int,
        val channel: Long,
        val status: Int,
        val message: String?,
    ) : MacosEvent

    companion object {
        fun decode(
            kind: Int,
            a: Long,
            b: Long,
            status: Int,
            text: String?,
            data: ByteArray?,
        ): MacosEvent? =
            when (kind) {
                MacosEventKind.CENTRAL_STATE -> CentralState(a.toInt())
                MacosEventKind.DISCOVERED -> {
                    val lines = text.orEmpty().split('\n', limit = 2)
                    Discovered(
                        peripheral = a,
                        identifier = lines[0],
                        peripheralName = lines.getOrNull(1)?.takeIf { it.isNotEmpty() },
                        rssi = status,
                        connectable = if (b < 0) null else b != 0L,
                        advertising = data ?: byteArrayOf(),
                    )
                }
                MacosEventKind.CONNECTED -> Connected(a)
                MacosEventKind.CONNECT_FAILED -> ConnectFailed(a, status, text)
                MacosEventKind.DISCONNECTED -> Disconnected(a, status, text)
                MacosEventKind.SERVICES_DISCOVERED -> ServicesDiscovered(a, status, text, attributes(status, text))
                MacosEventKind.CHARACTERISTICS_DISCOVERED ->
                    CharacteristicsDiscovered(
                        a,
                        b,
                        status,
                        text,
                        attributes(status, text),
                    )
                MacosEventKind.DESCRIPTORS_DISCOVERED ->
                    DescriptorsDiscovered(
                        a,
                        b,
                        status,
                        text,
                        attributes(status, text),
                    )
                MacosEventKind.VALUE_UPDATED -> ValueUpdated(a, b, status, text, data ?: byteArrayOf())
                MacosEventKind.VALUE_WRITTEN -> ValueWritten(a, b, status, text)
                MacosEventKind.DESCRIPTOR_VALUE -> DescriptorValue(a, b, status, text, data ?: byteArrayOf())
                MacosEventKind.DESCRIPTOR_WRITTEN -> DescriptorWritten(a, b, status, text)
                MacosEventKind.NOTIFY_STATE -> NotifyState(a, b, status, text, data?.firstOrNull() == 1.toByte())
                MacosEventKind.RSSI -> Rssi(a, b.toInt(), status, text)
                MacosEventKind.READY_TO_WRITE -> ReadyToWrite(a)
                MacosEventKind.SERVICES_MODIFIED -> ServicesModified(a)
                MacosEventKind.L2CAP_OPENED -> L2capOpened(a, b, status, text)
                MacosEventKind.L2CAP_DATA -> L2capData(a, data ?: byteArrayOf())
                MacosEventKind.L2CAP_CLOSED -> L2capClosed(a, status)
                MacosEventKind.PM_STATE -> PmState(a.toInt())
                MacosEventKind.PM_SERVICE_ADDED -> PmServiceAdded(a, status, text)
                MacosEventKind.PM_ADVERTISING_STARTED -> PmAdvertisingStarted(status, text)
                MacosEventKind.PM_READ_REQUEST -> PmReadRequest(a, b, status, text.orEmpty())
                MacosEventKind.PM_WRITE_REQUESTS -> PmWriteRequests(a, text.orEmpty(), writes(data))
                MacosEventKind.PM_SUBSCRIBED -> PmSubscribed(b, text.orEmpty(), a.toInt())
                MacosEventKind.PM_UNSUBSCRIBED -> PmUnsubscribed(b, text.orEmpty())
                MacosEventKind.PM_READY_TO_UPDATE -> PmReadyToUpdate
                MacosEventKind.PM_L2CAP_PUBLISHED -> PmL2capPublished(a.toInt(), status, text)
                MacosEventKind.PM_L2CAP_OPENED -> PmL2capOpened(a.toInt(), b, status, text)
                else -> null
            }

        internal fun attributes(
            status: Int,
            text: String?,
        ): List<AttributeLine> {
            if (status != 0 || text.isNullOrEmpty()) return emptyList()
            return text.lines().mapNotNull { line ->
                val parts = line.split('|')
                val handle = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val uuid = parts.getOrNull(1) ?: return@mapNotNull null
                AttributeLine(handle, uuid, parts.getOrNull(2)?.toIntOrNull() ?: 0)
            }
        }

        internal fun writes(data: ByteArray?): List<PmWrite> {
            if (data == null) return emptyList()
            val buffer = ByteBuffer.wrap(data)
            val result = mutableListOf<PmWrite>()
            while (buffer.remaining() >= WRITE_HEADER_BYTES) {
                val characteristic = buffer.long
                val offset = buffer.int
                val length = buffer.int
                if (length < 0 || length > buffer.remaining()) break
                val value = ByteArray(length).also(buffer::get)
                result += PmWrite(characteristic, offset, value)
            }
            return result
        }

        private const val WRITE_HEADER_BYTES = 16
    }
}
