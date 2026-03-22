package com.atruedev.kmpble.profiles.parsing

import kotlin.math.pow

// SFLOAT special values are full 16-bit raw values (exponent + mantissa combined)
private const val SFLOAT_NAN = 0x07FF
private const val SFLOAT_NRES = 0x0800
private const val SFLOAT_POS_INFINITY = 0x07FE
private const val SFLOAT_NEG_INFINITY = 0x0802
private const val SFLOAT_RESERVED = 0x0801

// FLOAT special values are full 32-bit raw values
private const val FLOAT_NAN = 0x007FFFFFL
private const val FLOAT_NRES = 0x00800000L
private const val FLOAT_POS_INFINITY = 0x007FFFFEL
private const val FLOAT_NEG_INFINITY = 0x00800002L
private const val FLOAT_RESERVED = 0x00800001L

/**
 * Converts a 16-bit IEEE 11073-20601 SFLOAT to a [Float].
 *
 * Returns `null` for special values (NaN, NRes, +/-Infinity, Reserved).
 *
 * @param raw unsigned 16-bit value read from BLE characteristic
 */
public fun sfloatToFloat(raw: Int): Float? {
    if (raw == SFLOAT_NAN || raw == SFLOAT_NRES ||
        raw == SFLOAT_POS_INFINITY || raw == SFLOAT_NEG_INFINITY ||
        raw == SFLOAT_RESERVED
    ) return null

    val mantissa = raw and 0x0FFF
    val exponent = (raw shr 12) and 0x0F
    val signedMantissa = if (mantissa >= 0x0800) mantissa - 0x1000 else mantissa
    val signedExponent = if (exponent >= 0x08) exponent - 0x10 else exponent

    return (signedMantissa * 10.0.pow(signedExponent)).toFloat()
}

/**
 * Converts a 32-bit IEEE 11073-20601 FLOAT to a [Float].
 *
 * Returns `null` for special values (NaN, NRes, +/-Infinity, Reserved).
 *
 * @param raw unsigned 32-bit value read from BLE characteristic
 */
public fun floatToFloat(raw: Long): Float? {
    if (raw == FLOAT_NAN || raw == FLOAT_NRES ||
        raw == FLOAT_POS_INFINITY || raw == FLOAT_NEG_INFINITY ||
        raw == FLOAT_RESERVED
    ) return null

    val mantissa = (raw and 0x00FFFFFF).toInt()
    val exponent = ((raw shr 24) and 0xFF).toInt()
    val signedMantissa = if (mantissa >= 0x800000) mantissa - 0x1000000 else mantissa
    val signedExponent = if (exponent >= 0x80) exponent - 0x100 else exponent

    return (signedMantissa * 10.0.pow(signedExponent)).toFloat()
}
