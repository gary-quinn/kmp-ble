package com.atruedev.kmpble.beacon

import com.atruedev.kmpble.scanner.Advertisement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Parsed beacon advertisement data.
 *
 * [Beacon] is a sealed hierarchy representing common beacon protocols.
 * Parse an [Advertisement] to a typed beacon via [parse] or the
 * [com.atruedev.kmpble.scanner.Advertisement.parseBeacon] extension.
 *
 * ## Supported protocols
 * - [IBeacon] -- Apple iBeacon (manufacturer data 0x004C, type 0x02)
 * - [EddystoneUID] -- Google Eddystone-UID (service UUID 0xFEAA, frame type 0x00)
 * - [EddystoneURL] -- Google Eddystone-URL (service UUID 0xFEAA, frame type 0x10)
 * - [EddystoneTLM] -- Google Eddystone-TLM (service UUID 0xFEAA, frame type 0x20)
 *
 * ## Thread safety
 * All implementations are immutable data classes. Safe for concurrent access.
 */
public sealed interface Beacon {
    /** The original advertisement that produced this beacon. */
    public val source: Advertisement

    /**
     * Apple iBeacon proximity beacon.
     *
     * Uses Apple manufacturer data (company ID 0x004C) with subtype 0x02.
     * The [measuredPower] is the calibrated RSSI at 1 meter, used for
     * distance estimation.
     */
    @OptIn(ExperimentalUuidApi::class)
    public data class IBeacon(
        override val source: Advertisement,
        /** Proximity UUID identifying the beacon region. */
        public val proximityUuid: Uuid,
        /** Major value (grouping). */
        public val major: Int,
        /** Minor value (individual beacon within group). */
        public val minor: Int,
        /** Calibrated RSSI at 1 meter (signed int8). */
        public val measuredPower: Int,
    ) : Beacon

    /**
     * Google Eddystone-UID beacon.
     *
     * Broadcasts a 16-byte unique identifier (10-byte namespace + 6-byte instance).
     * The [rangingData] is the calibrated txPower at 0 meters.
     */
    public data class EddystoneUID(
        override val source: Advertisement,
        /** 10-byte namespace identifier. */
        public val namespace: ByteArray,
        /** 6-byte instance identifier. */
        public val instance: ByteArray,
        /** Calibrated txPower at 0 meters (signed int8). */
        public val rangingData: Int,
    ) : Beacon {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EddystoneUID) return false
            return source == other.source &&
                namespace.contentEquals(other.namespace) &&
                instance.contentEquals(other.instance) &&
                rangingData == other.rangingData
        }

        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + namespace.contentHashCode()
            result = 31 * result + instance.contentHashCode()
            result = 31 * result + rangingData
            return result
        }
    }

    /**
     * Google Eddystone-URL beacon.
     *
     * Broadcasts a compressed URL. The [txPower] is the calibrated txPower at 0 meters.
     */
    public data class EddystoneURL(
        override val source: Advertisement,
        /** Decompressed URL string. */
        public val url: String,
        /** Calibrated txPower at 0 meters (signed int8). */
        public val txPower: Int,
    ) : Beacon

    /**
     * Google Eddystone-TLM telemetry beacon.
     *
     * Broadcasts device health metrics alongside UID or URL frames.
     * Usually interleaved with Eddystone-UID or Eddystone-URL advertisements.
     */
    public data class EddystoneTLM(
        override val source: Advertisement,
        /** Battery voltage in millivolts, or null if not supported. */
        public val batteryVoltageMv: Int?,
        /** Temperature in Celsius (signed fixed-point 8.8), or null if not supported. */
        public val temperatureCelsius: Float?,
        /** Advertisement count since power-on or reboot. */
        public val advertisementCount: Long,
        /** Uptime in seconds (0.1 second resolution). */
        public val uptimeSeconds: Double,
    ) : Beacon

    public companion object {
        /**
         * Parse an [Advertisement] into a typed [Beacon], or null if the
         * advertisement does not contain a recognized beacon format.
         */
        public fun parse(advertisement: Advertisement): Beacon? {
            parseIBeacon(advertisement)?.let { return it }
            parseEddystone(advertisement)?.let { return it }
            return null
        }
    }
}

/**
 * Returns `true` if this advertisement contains a recognized beacon format
 * that can be parsed via [parseBeacon].
 */
public fun Advertisement.isBeacon(): Boolean = Beacon.parse(this) != null

/**
 * Parse this advertisement into a typed [Beacon], or null if the
 * advertisement does not contain a recognized beacon format.
 */
public fun Advertisement.parseBeacon(): Beacon? = Beacon.parse(this)
