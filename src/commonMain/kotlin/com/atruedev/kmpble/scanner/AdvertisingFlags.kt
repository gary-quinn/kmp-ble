package com.atruedev.kmpble.scanner

import kotlin.uuid.Uuid

/**
 * BLE advertising data flags as defined in Core Spec Supplement, Part A, Section 1.3.
 *
 * Multiple flags can be combined with OR when calling [AdvertisingDataBuilder.flags].
 */
public enum class AdvertisingFlags(public val mask: Int) {
    /** LE Limited Discoverable Mode. */
    LE_LIMITED_DISCOVERABLE(0x01),
    /** LE General Discoverable Mode. */
    LE_GENERAL_DISCOVERABLE(0x02),
    /** BR/EDR Not Supported. For LE-only devices. */
    BR_EDR_NOT_SUPPORTED(0x04),
    /** Simultaneous LE and BR/EDR to Same Device Capable (Controller). */
    LE_BR_EDR_CONTROLLER(0x08),
    /** Simultaneous LE and BR/EDR to Same Device Capable (Host). */
    LE_BR_EDR_HOST(0x10),
}
