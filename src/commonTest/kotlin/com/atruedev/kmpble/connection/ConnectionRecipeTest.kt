package com.atruedev.kmpble.connection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class ConnectionRecipeTest {

    @Test
    fun medicalRecipeHasCorrectDefaults() {
        val opts = ConnectionRecipe.MEDICAL
        assertEquals(247, opts.mtuRequest)
        assertEquals(60.seconds, opts.timeout)
        val strategy = assertIs<ReconnectionStrategy.ExponentialBackoff>(opts.reconnectionStrategy)
        assertEquals(1.seconds, strategy.initialDelay)
        assertEquals(30.seconds, strategy.maxDelay)
        assertEquals(10, strategy.maxAttempts)
    }

    @Test
    fun fitnessRecipeHasCorrectDefaults() {
        val opts = ConnectionRecipe.FITNESS
        assertEquals(247, opts.mtuRequest)
        assertEquals(30.seconds, opts.timeout)
        val strategy = assertIs<ReconnectionStrategy.ExponentialBackoff>(opts.reconnectionStrategy)
        assertEquals(0.5.seconds, strategy.initialDelay)
        assertEquals(15.seconds, strategy.maxDelay)
        assertEquals(5, strategy.maxAttempts)
    }

    @Test
    fun iotRecipeHasCorrectDefaults() {
        val opts = ConnectionRecipe.IOT
        assertNull(opts.mtuRequest)
        assertEquals(15.seconds, opts.timeout)
        val strategy = assertIs<ReconnectionStrategy.LinearBackoff>(opts.reconnectionStrategy)
        assertEquals(2.seconds, strategy.delay)
        assertEquals(3, strategy.maxAttempts)
    }

    @Test
    fun consumerRecipeHasCorrectDefaults() {
        val opts = ConnectionRecipe.CONSUMER
        assertEquals(247, opts.mtuRequest)
        assertEquals(20.seconds, opts.timeout)
        val strategy = assertIs<ReconnectionStrategy.ExponentialBackoff>(opts.reconnectionStrategy)
        assertEquals(1.seconds, strategy.initialDelay)
        assertEquals(10.seconds, strategy.maxDelay)
        assertEquals(3, strategy.maxAttempts)
    }

    @Test
    fun recipesAreCopyable() {
        val custom = ConnectionRecipe.MEDICAL.copy(mtuRequest = 512)
        assertEquals(512, custom.mtuRequest)
        assertEquals(60.seconds, custom.timeout) // other fields preserved
    }
}
