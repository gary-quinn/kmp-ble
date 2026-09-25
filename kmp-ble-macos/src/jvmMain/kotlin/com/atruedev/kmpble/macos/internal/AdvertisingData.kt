package com.atruedev.kmpble.macos.internal

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Fields of the AD structures the native shim rebuilds from CoreBluetooth's advertisement
 * dictionary (Core Specification Supplement, Part A).
 */
@OptIn(ExperimentalUuidApi::class)
internal class AdvertisingData(
    val localName: String?,
    val txPower: Int?,
    val serviceUuids: List<Uuid>,
    val serviceData: Map<Uuid, ByteArray>,
    val manufacturerData: Map<Int, ByteArray>,
) {
    companion object {
        private const val TYPE_UUID16_INCOMPLETE = 0x02
        private const val TYPE_UUID16 = 0x03
        private const val TYPE_UUID32_INCOMPLETE = 0x04
        private const val TYPE_UUID32 = 0x05
        private const val TYPE_UUID128_INCOMPLETE = 0x06
        private const val TYPE_UUID128 = 0x07
        private const val TYPE_SHORT_NAME = 0x08
        private const val TYPE_COMPLETE_NAME = 0x09
        private const val TYPE_TX_POWER = 0x0A
        private const val TYPE_SERVICE_DATA16 = 0x16
        private const val TYPE_SERVICE_DATA32 = 0x20
        private const val TYPE_SERVICE_DATA128 = 0x21
        private const val TYPE_MANUFACTURER = 0xFF
        private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

        fun parse(bytes: ByteArray): AdvertisingData {
            var localName: String? = null
            var txPower: Int? = null
            val serviceUuids = mutableListOf<Uuid>()
            val serviceData = linkedMapOf<Uuid, ByteArray>()
            val manufacturerData = linkedMapOf<Int, ByteArray>()
            var index = 0
            while (index < bytes.size) {
                val length = bytes[index].toInt() and 0xFF
                if (length == 0 || index + length >= bytes.size) break
                val type = bytes[index + 1].toInt() and 0xFF
                val payload = bytes.copyOfRange(index + 2, index + 1 + length)
                when (type) {
                    TYPE_COMPLETE_NAME, TYPE_SHORT_NAME -> localName = localName ?: payload.decodeToString()
                    TYPE_TX_POWER -> txPower = payload.firstOrNull()?.toInt()
                    TYPE_UUID16, TYPE_UUID16_INCOMPLETE -> serviceUuids += payload.uuids(2)
                    TYPE_UUID32, TYPE_UUID32_INCOMPLETE -> serviceUuids += payload.uuids(4)
                    TYPE_UUID128, TYPE_UUID128_INCOMPLETE -> serviceUuids += payload.uuids(16)
                    TYPE_SERVICE_DATA16 -> payload.serviceData(2)?.let { serviceData += it }
                    TYPE_SERVICE_DATA32 -> payload.serviceData(4)?.let { serviceData += it }
                    TYPE_SERVICE_DATA128 -> payload.serviceData(16)?.let { serviceData += it }
                    TYPE_MANUFACTURER ->
                        if (payload.size >= 2) {
                            val company = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
                            manufacturerData[company] = payload.copyOfRange(2, payload.size)
                        }
                }
                index += length + 1
            }
            return AdvertisingData(localName, txPower, serviceUuids, serviceData, manufacturerData)
        }

        private fun ByteArray.uuids(width: Int): List<Uuid> =
            (0 until size / width).map { i -> copyOfRange(i * width, (i + 1) * width).toUuid() }

        private fun ByteArray.serviceData(width: Int): Pair<Uuid, ByteArray>? {
            if (size < width) return null
            return copyOfRange(0, width).toUuid() to copyOfRange(width, size)
        }

        private fun ByteArray.toUuid(): Uuid {
            val hex = reversedArray().joinToString("") { "%02x".format(it) }
            return when (size) {
                2, 4 -> Uuid.parse(hex.padStart(8, '0') + BASE_UUID_SUFFIX)
                else -> Uuid.parseHex(hex)
            }
        }
    }
}
