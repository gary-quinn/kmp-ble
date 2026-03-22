package com.atruedev.kmpble.profiles.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Ieee11073Test {

    @Test
    fun sfloatZero() {
        assertEquals(0.0f, sfloatToFloat(0x0000))
    }

    @Test
    fun sfloatPositiveInteger() {
        // mantissa=100, exponent=0 → 100.0
        assertEquals(100.0f, sfloatToFloat(0x0064))
    }

    @Test
    fun sfloatNegativeExponent() {
        // mantissa=365, exponent=0xF (-1) → 36.5
        assertEquals(36.5f, sfloatToFloat(0xF16D))
    }

    @Test
    fun sfloatNegativeMantissa() {
        // mantissa=0xFFF (-1), exponent=0 → -1.0
        assertEquals(-1.0f, sfloatToFloat(0x0FFF))
    }

    @Test
    fun sfloatNanReturnsNull() {
        assertNull(sfloatToFloat(0x07FF))
    }

    @Test
    fun sfloatNresReturnsNull() {
        assertNull(sfloatToFloat(0x0800))
    }

    @Test
    fun sfloatPosInfinityReturnsNull() {
        assertNull(sfloatToFloat(0x07FE))
    }

    @Test
    fun sfloatNegInfinityReturnsNull() {
        assertNull(sfloatToFloat(0x0802))
    }

    @Test
    fun floatZero() {
        assertEquals(0.0f, floatToFloat(0x00000000L))
    }

    @Test
    fun floatPositiveInteger() {
        // mantissa=1000, exponent=0 → 1000.0
        assertEquals(1000.0f, floatToFloat(0x000003E8L))
    }

    @Test
    fun floatNegativeExponent() {
        // mantissa=3650, exponent=0xFF (-1) → 365.0
        assertEquals(365.0f, floatToFloat(0xFF000E42L))
    }

    @Test
    fun floatNanReturnsNull() {
        assertNull(floatToFloat(0x007FFFFFL))
    }

    @Test
    fun floatNresReturnsNull() {
        assertNull(floatToFloat(0x00800000L))
    }
}
