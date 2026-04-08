package com.atruedev.kmpble.internal

import platform.CoreBluetooth.CBPeripheralManagerStateUnknown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPeripheralManagerDelegateTest {
    @Test
    fun initialManagerStateIsUnknown() {
        val delegate = IosPeripheralManagerDelegate()
        assertEquals(CBPeripheralManagerStateUnknown, delegate.managerState.value)
    }

    @Test
    fun callbacksAreNullByDefault() {
        val delegate = IosPeripheralManagerDelegate()
        assertNull(delegate.onServiceAdded)
        assertNull(delegate.onReadRequest)
        assertNull(delegate.onWriteRequests)
        assertNull(delegate.onSubscribe)
        assertNull(delegate.onReadyToUpdate)
        assertNull(delegate.onStartAdvertising)
    }

    @Test
    fun onServiceAddedCallbackLifecycle() {
        val delegate = IosPeripheralManagerDelegate()
        var invoked = false
        delegate.onServiceAdded = { invoked = true }
        delegate.onServiceAdded?.invoke(null)
        assertTrue(invoked)

        delegate.onServiceAdded = null
        assertNull(delegate.onServiceAdded)
    }

    @Test
    fun onReadyToUpdateCallbackLifecycle() {
        val delegate = IosPeripheralManagerDelegate()
        var invoked = false
        delegate.onReadyToUpdate = { invoked = true }
        delegate.onReadyToUpdate?.invoke()
        assertTrue(invoked)

        delegate.onReadyToUpdate = null
        assertNull(delegate.onReadyToUpdate)
    }

    @Test
    fun onStartAdvertisingCallbackLifecycle() {
        val delegate = IosPeripheralManagerDelegate()
        var invoked = false
        delegate.onStartAdvertising = { invoked = true }
        delegate.onStartAdvertising?.invoke(null)
        assertTrue(invoked)

        delegate.onStartAdvertising = null
        assertNull(delegate.onStartAdvertising)
    }

    @Test
    fun onReadRequestCallbackCanBeSetAndCleared() {
        val delegate = IosPeripheralManagerDelegate()
        delegate.onReadRequest = { _, _ -> }
        assertNotNull(delegate.onReadRequest)

        delegate.onReadRequest = null
        assertNull(delegate.onReadRequest)
    }

    @Test
    fun onWriteRequestsCallbackCanBeSetAndCleared() {
        val delegate = IosPeripheralManagerDelegate()
        delegate.onWriteRequests = { _, _ -> }
        assertNotNull(delegate.onWriteRequests)

        delegate.onWriteRequests = null
        assertNull(delegate.onWriteRequests)
    }

    @Test
    fun onSubscribeCallbackCanBeSetAndCleared() {
        val delegate = IosPeripheralManagerDelegate()
        delegate.onSubscribe = { _, _ -> }
        assertNotNull(delegate.onSubscribe)

        delegate.onSubscribe = null
        assertNull(delegate.onSubscribe)
    }
}
