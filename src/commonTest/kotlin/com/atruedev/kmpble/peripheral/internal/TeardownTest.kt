package com.atruedev.kmpble.peripheral.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TeardownTest {
    @Test
    fun runsEveryStepAndRethrowsTheFirstFailure() {
        val ran = mutableListOf<Int>()
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")

        val thrown =
            assertFailsWith<IllegalStateException> {
                runTeardown(
                    {
                        ran += 1
                        throw first
                    },
                    { ran += 2 },
                    {
                        ran += 3
                        throw second
                    },
                    { ran += 4 },
                )
            }

        assertSame(first, thrown)
        assertEquals(listOf(1, 2, 3, 4), ran)
        assertEquals(listOf<Throwable>(second), thrown.suppressedExceptions)
    }

    @Test
    fun completesNormallyWhenNoStepThrows() {
        val ran = mutableListOf<Int>()

        runTeardown({ ran += 1 }, { ran += 2 })

        assertEquals(listOf(1, 2), ran)
    }
}
