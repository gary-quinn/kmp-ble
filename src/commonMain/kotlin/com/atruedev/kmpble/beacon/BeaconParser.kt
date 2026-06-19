package com.atruedev.kmpble.beacon

import com.atruedev.kmpble.scanner.Advertisement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Eddystone-URL encoded character lookup table.
 *
 * Codes 0x00-0x13 expand to common URL components. Unrecognized
 * codes are passed through as literal characters.
 */
private val EDDYSTONE_URL_CODES: Map<Int, String> =
    mapOf(
        0x00 to ".com/",
        0x01 to ".org/",
        0x02 to ".edu/",
        0x03 to ".net/",
        0x04 to ".info/",
        0x05 to ".biz/",
        0x06 to ".gov/",
        0x07 to ".com",
        0x08 to ".org",
        0x09 to ".edu",
        0x0A to ".net",
        0x0B to ".info",
        0x0C to ".biz",
        0x0D to ".gov",
    )

internal fun parseIBeacon(advertisement: Advertisement): Beacon.IBeacon? {
    val data = advertisement.manufacturerData[Beacon.APPLE_COMPANY_ID] ?: return null
    val bytes = data.toByteArray()
    if (bytes.size < Beacon.IBEACON_MIN_LENGTH) return null
    if (bytes[0] != Beacon.IBEACON_TYPE.toByte()) return null
    val length = bytes[1].toInt() and 0xFF
    if (length != bytes.size - 2) return null

    @OptIn(ExperimentalUuidApi::class)
    val proximityUuid = Uuid.fromByteArray(bytes.sliceArray(2..17))
    val major = ((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)
    val minor = ((bytes[20].toInt() and 0xFF) shl 8) or (bytes[21].toInt() and 0xFF)
    val measuredPower = bytes[22].toInt()

    return Beacon.IBeacon(
        source = advertisement,
        proximityUuid = proximityUuid,
        major = major,
        minor = minor,
        measuredPower = measuredPower,
    )
}

internal fun parseEddystone(advertisement: Advertisement): Beacon? {
    val data = advertisement.serviceData[Beacon.EDDYSTONE_SERVICE_UUID] ?: return null
    val bytes = data.toByteArray()
    if (bytes.isEmpty()) return null

    return when (bytes[0]) {
        Beacon.EDDYSTONE_FRAME_UID -> parseEddystoneUID(advertisement, bytes)
        Beacon.EDDYSTONE_FRAME_URL -> parseEddystoneURL(advertisement, bytes)
        Beacon.EDDYSTONE_FRAME_TLM -> parseEddystoneTLM(advertisement, bytes)
        else -> null
    }
}

internal fun parseEddystoneUID(
    advertisement: Advertisement,
    bytes: ByteArray,
): Beacon.EddystoneUID? {
    if (bytes.size < Beacon.EDDYSTONE_UID_LENGTH) return null
    val rangingData = bytes[1].toInt()
    val namespace = bytes.sliceArray(2..11)
    val instance = bytes.sliceArray(12..17)
    return Beacon.EddystoneUID(
        source = advertisement,
        namespace = namespace,
        instance = instance,
        rangingData = rangingData,
    )
}

internal fun parseEddystoneURL(
    advertisement: Advertisement,
    bytes: ByteArray,
): Beacon.EddystoneURL? {
    if (bytes.size < 3) return null
    val txPower = bytes[1].toInt()

    val schemePrefix =
        when (bytes[2].toInt() and 0xFF) {
            0x00 -> "http://www."
            0x01 -> "https://www."
            0x02 -> "http://"
            0x03 -> "https://"
            else -> return null
        }

    val encoded =
        buildString {
            append(schemePrefix)
            for (i in 3 until bytes.size) {
                val code = bytes[i].toInt() and 0xFF
                append(EDDYSTONE_URL_CODES[code] ?: code.toChar().toString())
            }
        }

    return Beacon.EddystoneURL(
        source = advertisement,
        url = encoded,
        txPower = txPower,
    )
}

internal fun parseEddystoneTLM(
    advertisement: Advertisement,
    bytes: ByteArray,
): Beacon.EddystoneTLM? {
    if (bytes.size < Beacon.EDDYSTONE_TLM_LENGTH) return null
    val tlmVersion = bytes[1].toInt() and 0xFF

    // Version 0x00: unencrypted TLM
    val batteryMv = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
    val batteryVoltageMv = if (batteryMv == 0) null else batteryMv

    val tempRaw = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
    val temperatureCelsius: Float? =
        when {
            tempRaw == 0x8000 -> null
            tlmVersion == 0x00 -> {
                // Signed 8.8 fixed-point
                val signed = if (tempRaw >= 0x8000) tempRaw - 0x10000 else tempRaw
                signed.toFloat() / 256.0f
            }
            else -> null
        }

    val advertisementCount: Long =
        ((bytes[6].toLong() and 0xFF) shl 24) or
            ((bytes[7].toLong() and 0xFF) shl 16) or
            ((bytes[8].toLong() and 0xFF) shl 8) or
            (bytes[9].toLong() and 0xFF)

    val uptimeTenthSeconds: Long =
        ((bytes[10].toLong() and 0xFF) shl 24) or
            ((bytes[11].toLong() and 0xFF) shl 16) or
            ((bytes[12].toLong() and 0xFF) shl 8) or
            (bytes[13].toLong() and 0xFF)

    return Beacon.EddystoneTLM(
        source = advertisement,
        batteryVoltageMv = batteryVoltageMv,
        temperatureCelsius = temperatureCelsius,
        advertisementCount = advertisementCount,
        uptimeSeconds = uptimeTenthSeconds / 10.0,
    )
}
