package com.atruedev.kmpble.peripheral

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates the HandlerThread/Looper pattern used by [AndroidGattBridge]
 * for dispatching BLE callbacks.
 *
 * The actual [AndroidGattBridge] creates a HandlerThread per connection and
 * dispatches [BluetoothGattCallback] events on it. These tests validate the
 * threading machinery works correctly on a real Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class GattCallbackDispatchTest {
    private var thread: HandlerThread? = null

    @After
    fun teardown() {
        thread?.quitSafely()
    }

    @Test
    fun handlerThread_startsWithValidLooper() {
        thread = HandlerThread("kmp-ble-test").apply { start() }
        val looper = thread!!.looper
        assertNotNull(looper, "HandlerThread looper should be non-null after start()")
    }

    @Test
    fun handler_dispatchesToHandlerThread() {
        thread = HandlerThread("kmp-ble-test").apply { start() }
        val handler = Handler(thread!!.looper)
        val latch = CountDownLatch(1)
        var dispatchThread: Thread? = null

        handler.post {
            dispatchThread = Thread.currentThread()
            latch.countDown()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Handler should dispatch within 2s")
        val t = dispatchThread
        assertNotNull(t)
        assertTrue(
            t.name.contains("kmp-ble-test"),
            "Callback should run on HandlerThread, not ${t.name}",
        )
    }

    @Test
    fun handler_dispatchesNotOnMainThread() {
        thread = HandlerThread("kmp-ble-test").apply { start() }
        val handler = Handler(thread!!.looper)
        val latch = CountDownLatch(1)
        var isMainThread = true

        handler.post {
            isMainThread = Looper.myLooper() == Looper.getMainLooper()
            latch.countDown()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertFalse(isMainThread, "GATT callbacks should NOT dispatch on the main thread")
    }

    @Test
    fun handlerThread_quitSafely_stopsProcessing() {
        thread = HandlerThread("kmp-ble-test").apply { start() }
        val handler = Handler(thread!!.looper)
        val latch = CountDownLatch(1)

        thread!!.quitSafely()

        // After quitSafely, posting should return false
        val posted = handler.post { latch.countDown() }
        // The handler may or may not accept the post depending on timing,
        // but the thread should terminate
        thread!!.join(2000)
        assertFalse(thread!!.isAlive, "HandlerThread should terminate after quitSafely")
    }

    @Test
    fun multipleCallbacks_dispatchInOrder() {
        thread = HandlerThread("kmp-ble-test").apply { start() }
        val handler = Handler(thread!!.looper)
        val results = mutableListOf<Int>()
        val latch = CountDownLatch(3)

        handler.post {
            results.add(1)
            latch.countDown()
        }
        handler.post {
            results.add(2)
            latch.countDown()
        }
        handler.post {
            results.add(3)
            latch.countDown()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2, 3), results)
    }

    @Test
    fun namedThread_matchesKmpBlePattern() {
        val address = "AA:BB:CC:DD:EE:FF"
        thread = HandlerThread("kmp-ble-cb/$address").apply { start() }
        assertTrue(
            thread!!.name == "kmp-ble-cb/$address",
            "Thread name should match the pattern used by AndroidGattBridge",
        )
    }
}
