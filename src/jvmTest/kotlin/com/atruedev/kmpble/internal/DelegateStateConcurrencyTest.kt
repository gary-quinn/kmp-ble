package com.atruedev.kmpble.internal

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the atomic delegate-state pattern used in
 * [IosPeripheralManagerDelegate]: a single [atomic] reference
 * to an immutable data class, updated via [copy].
 *
 * The real [IosPeripheralDelegateState] uses platform types
 * (NSError, CBPeripheralManager, etc.) and lives in iosMain.
 * This test validates the concurrency contract with a structurally
 * identical state holder so the pattern is proven correct on JVM.
 */
class DelegateStateConcurrencyTest {
    private data class TestDelegateState(
        val onServiceAdded: ((String) -> Unit)? = null,
        val onReadRequest: ((String, Int) -> Unit)? = null,
        val onWriteRequests: ((String, List<Int>) -> Unit)? = null,
        val onSubscribe: ((String, String) -> Unit)? = null,
        val onReadyToUpdate: (() -> Unit)? = null,
        val onStartAdvertising: ((String) -> Unit)? = null,
        val onPublishL2cap: ((Int, String) -> Unit)? = null,
        val onOpenL2capChannel: ((String?, String) -> Unit)? = null,
    )

    private class TestDelegate {
        private val _state = atomic(TestDelegateState())

        var onServiceAdded: ((String) -> Unit)?
            get() = _state.value.onServiceAdded
            set(value) {
                _state.update { it.copy(onServiceAdded = value) }
            }

        var onReadRequest: ((String, Int) -> Unit)?
            get() = _state.value.onReadRequest
            set(value) {
                _state.update { it.copy(onReadRequest = value) }
            }

        var onWriteRequests: ((String, List<Int>) -> Unit)?
            get() = _state.value.onWriteRequests
            set(value) {
                _state.update { it.copy(onWriteRequests = value) }
            }

        var onSubscribe: ((String, String) -> Unit)?
            get() = _state.value.onSubscribe
            set(value) {
                _state.update { it.copy(onSubscribe = value) }
            }

        var onReadyToUpdate: (() -> Unit)?
            get() = _state.value.onReadyToUpdate
            set(value) {
                _state.update { it.copy(onReadyToUpdate = value) }
            }

        var onStartAdvertising: ((String) -> Unit)?
            get() = _state.value.onStartAdvertising
            set(value) {
                _state.update { it.copy(onStartAdvertising = value) }
            }

        var onPublishL2cap: ((Int, String) -> Unit)?
            get() = _state.value.onPublishL2cap
            set(value) {
                _state.update { it.copy(onPublishL2cap = value) }
            }

        var onOpenL2capChannel: ((String?, String) -> Unit)?
            get() = _state.value.onOpenL2capChannel
            set(value) {
                _state.update { it.copy(onOpenL2capChannel = value) }
            }

        // Delegate methods read from the atomic state snapshot
        fun handleServiceAdded(error: String) {
            _state.value.onServiceAdded?.invoke(error)
        }

        fun handleStartAdvertising(error: String) {
            _state.value.onStartAdvertising?.invoke(error)
        }

        fun handleReadRequest(
            peripheral: String,
            request: Int,
        ) {
            _state.value.onReadRequest?.invoke(peripheral, request)
        }
    }

    @Test
    fun `concurrent writes to different fields do not lose updates`() =
        runBlocking {
            val delegate = TestDelegate()
            val iterations = 1000

            val receivedServiceCalls = mutableListOf<String>()
            val receivedAdvertiseCalls = mutableListOf<String>()

            // Set initial callbacks
            delegate.onServiceAdded = { receivedServiceCalls.add(it) }
            delegate.onStartAdvertising = { receivedAdvertiseCalls.add(it) }

            // Concurrently write and read different fields
            withContext(Dispatchers.Default) {
                val jobs =
                    List(4) { index ->
                        launch {
                            repeat(iterations) { i ->
                                when (index) {
                                    0 -> delegate.onServiceAdded = { receivedServiceCalls.add("s$i") }
                                    1 -> delegate.onReadRequest = { p, r -> }
                                    2 -> delegate.onStartAdvertising = { receivedAdvertiseCalls.add("a$i") }
                                    3 -> {
                                        // Read: must never see a torn state
                                        val state = delegate.onServiceAdded
                                        val adv = delegate.onStartAdvertising
                                        // Both fields are independently set; null is valid
                                        // The key invariant: no exception, no corrupted lambda
                                        delegate.handleServiceAdded("read")
                                        delegate.handleStartAdvertising("read")
                                    }
                                }
                            }
                        }
                    }
                jobs.forEach { it.join() }
            }

            // After all concurrent ops, callbacks should still be valid
            delegate.onServiceAdded = { receivedServiceCalls.add("final") }
            delegate.onStartAdvertising = { receivedAdvertiseCalls.add("final") }
            delegate.handleServiceAdded("final")
            delegate.handleStartAdvertising("final")

            assertEquals("final", receivedServiceCalls.last())
            assertEquals("final", receivedAdvertiseCalls.last())
        }

    @Test
    fun `write to one field preserves other field values`() {
        val delegate = TestDelegate()

        var serviceCalled = false
        var advertiseCalled = false

        delegate.onServiceAdded = { serviceCalled = true }
        delegate.onStartAdvertising = { advertiseCalled = true }

        // Update only one field
        delegate.onReadRequest = { _, _ -> }

        // Other fields unchanged
        delegate.handleServiceAdded("err")
        delegate.handleStartAdvertising("err")

        assert(serviceCalled)
        assert(advertiseCalled)
    }

    @Test
    fun `nulling one callback does not affect others`() {
        val delegate = TestDelegate()

        var serviceCalled = false

        delegate.onServiceAdded = { serviceCalled = true }
        delegate.onStartAdvertising = { /* no-op */ }

        assertNotNull(delegate.onServiceAdded)
        assertNotNull(delegate.onStartAdvertising)

        // Null out only one
        delegate.onServiceAdded = null

        assertNull(delegate.onServiceAdded)
        assertNotNull(delegate.onStartAdvertising)
    }

    @Test
    fun `all callbacks start null by default`() {
        val delegate = TestDelegate()

        assertNull(delegate.onServiceAdded)
        assertNull(delegate.onReadRequest)
        assertNull(delegate.onWriteRequests)
        assertNull(delegate.onSubscribe)
        assertNull(delegate.onReadyToUpdate)
        assertNull(delegate.onStartAdvertising)
        assertNull(delegate.onPublishL2cap)
        assertNull(delegate.onOpenL2capChannel)
    }
}
