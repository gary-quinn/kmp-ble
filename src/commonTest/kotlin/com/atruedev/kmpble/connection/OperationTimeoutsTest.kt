package com.atruedev.kmpble.connection

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.PeripheralTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class OperationTimeoutsTest {
    @Test
    fun `default values match spec`() {
        val t = OperationTimeouts()
        assertEquals(30.seconds, t.connect)
        assertEquals(15.seconds, t.serviceDiscovery)
        assertEquals(5.seconds, t.read)
        assertEquals(5.seconds, t.write)
        assertEquals(10.seconds, t.mtuNegotiation)
        assertEquals(10.seconds, t.l2capOpen)
    }

    @Test
    fun `all timeouts can be overridden`() {
        val t =
            OperationTimeouts(
                connect = 60.seconds,
                serviceDiscovery = 20.seconds,
                read = 3.seconds,
                write = 3.seconds,
                mtuNegotiation = 5.seconds,
                l2capOpen = 15.seconds,
            )
        assertEquals(60.seconds, t.connect)
        assertEquals(20.seconds, t.serviceDiscovery)
        assertEquals(3.seconds, t.read)
        assertEquals(3.seconds, t.write)
        assertEquals(5.seconds, t.mtuNegotiation)
        assertEquals(15.seconds, t.l2capOpen)
    }

    @Test
    fun `zero duration disables timeout`() {
        val t = OperationTimeouts(connect = Duration.ZERO)
        assertEquals(Duration.ZERO, t.connect)
    }

    @Test
    fun `infinite duration disables timeout`() {
        val t = OperationTimeouts(connect = Duration.INFINITE)
        assertEquals(Duration.INFINITE, t.connect)
    }

    @Test
    fun `negative duration rejected`() {
        assertFailsWith<IllegalArgumentException> {
            OperationTimeouts(connect = (-1).seconds)
        }
    }

    @Test
    fun `ConnectionOptions defaults include OperationTimeouts`() {
        val opts = ConnectionOptions()
        assertEquals(30.seconds, opts.timeouts.connect)
        assertEquals(15.seconds, opts.timeouts.serviceDiscovery)
    }

    @Test
    fun `ConnectionOptions accepts custom timeouts`() {
        val opts =
            ConnectionOptions(
                timeouts = OperationTimeouts(connect = 45.seconds),
            )
        assertEquals(45.seconds, opts.timeouts.connect)
        // Other timeouts remain at defaults
        assertEquals(15.seconds, opts.timeouts.serviceDiscovery)
    }
}

class PeripheralTimeoutTest {
    @Test
    fun `PeripheralTimeout stores operation and timeout`() {
        val err = PeripheralTimeout("connect", 30.seconds)
        assertEquals("connect", err.operation)
        assertEquals(30.seconds, err.timeout)
    }

    @Test
    fun `PeripheralTimeout recovery hint is populated`() {
        val err = PeripheralTimeout("read", 5.seconds)
        assertTrue(err.recoveryHint.isNotEmpty())
    }

    @Test
    fun `PeripheralTimeout wrapped in BleException`() {
        val timeout = PeripheralTimeout("write", 5.seconds)
        val ex = BleException(timeout)
        assertIs<PeripheralTimeout>(ex.error)
        assertEquals("write", (ex.error as PeripheralTimeout).operation)
    }
}
