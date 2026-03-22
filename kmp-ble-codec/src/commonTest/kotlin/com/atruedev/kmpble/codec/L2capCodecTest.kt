@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.atruedev.kmpble.codec

import com.atruedev.kmpble.testing.FakeL2capChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class L2capCodecTest {

    @Test
    fun incomingDecodesPackets() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val received = mutableListOf<String>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            channel.incoming(TestStringDecoder).collect { received.add(it) }
        }

        channel.emitIncoming("alpha".encodeToByteArray())
        channel.emitIncoming("beta".encodeToByteArray())

        assertEquals(listOf("alpha", "beta"), received)
        job.cancel()
    }

    @Test
    fun writeEncodesBeforeSending() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)

        channel.write("hello", TestStringEncoder)

        val written = channel.getWrittenData()
        assertEquals(1, written.size)
        assertContentEquals("hello".encodeToByteArray(), written[0])
    }

    @Test
    fun writeMultipleEncodedValues() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)

        channel.write("one", TestStringEncoder)
        channel.write("two", TestStringEncoder)
        channel.write("three", TestStringEncoder)

        val written = channel.getWrittenData()
        assertEquals(3, written.size)
        assertContentEquals("one".encodeToByteArray(), written[0])
        assertContentEquals("two".encodeToByteArray(), written[1])
        assertContentEquals("three".encodeToByteArray(), written[2])
    }
}
