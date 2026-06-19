package com.atruedev.kmpble.codec

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import com.atruedev.kmpble.testing.simulateBondStateChange
import com.atruedev.kmpble.testing.simulateRediscoverySucceeded
import com.atruedev.kmpble.testing.simulateServiceChangedIndication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class PeripheralCodecExtensionsTest {
    private val svc = uuidFrom("180f")
    private val char = uuidFrom("2a19")

    @Test
    fun readAsReturnsSuccessWithDecodedValue() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf(0x55) }
                }
            }
        }
        peripheral.connect()
        val result = peripheral.readAs(svc, char, Uint8Codec)
        assertEquals(0x55, result.getOrThrow())
    }

    @Test
    fun readAsFailsWithCharacteristicNotFoundWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val result = peripheral.readAs(svc, char, Uint8Codec)
        val ex = result.exceptionOrNull()
        assertIs<CharacteristicNotFoundException>(ex)
        assertEquals(svc, ex.serviceUuid)
        assertEquals(char, ex.characteristicUuid)
    }

    @Test
    fun readAsFailsWithDecodeFailureWhenDecoderThrows() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf() }
                }
            }
        }
        peripheral.connect()
        val result = peripheral.readAs(svc, char, Uint8Codec)
        val ex = result.exceptionOrNull()
        assertIs<DecodeFailureException>(ex)
        assertEquals(0, ex.bytes.size)
    }

    @Test
    fun readAsFailsWithNotReadyWhenPeripheralNotConnected() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { byteArrayOf(0x55) }
                }
            }
        }
        val result = peripheral.readAs(svc, char, Uint8Codec)
        val ex = result.exceptionOrNull()
        assertIs<PeripheralNotReadyException>(ex)
        assertEquals(svc, ex.serviceUuid)
        assertEquals(char, ex.characteristicUuid)
        assertIs<State.Disconnected>(ex.currentState)
    }

    @Test
    fun readAsClassifiesBondingChangeAsNotFoundForMissingChar() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        peripheral.simulateBondStateChange()
        assertEquals(State.Connected.BondingChange, peripheral.state.value)

        val result = peripheral.readAs(svc, char, Uint8Codec)
        assertIs<CharacteristicNotFoundException>(result.exceptionOrNull())
    }

    @Test
    fun readAsClassifiesServiceChangedAsNotReadyForMissingChar() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        peripheral.simulateServiceChangedIndication()
        assertEquals(State.Connected.ServiceChanged, peripheral.state.value)

        val result = peripheral.readAs(svc, char, Uint8Codec)
        val ex = result.exceptionOrNull()
        assertIs<PeripheralNotReadyException>(ex)
        assertEquals(State.Connected.ServiceChanged, ex.currentState)
    }

    @Test
    fun writeAsReturnsSuccessAndEncodesValue() = runTest {
        var received: ByteArray? = null
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(write = true)
                    onWrite { bytes, _ -> received = bytes }
                }
            }
        }
        peripheral.connect()
        val result = peripheral.writeAs(svc, char, 0xAB, Uint8Codec)
        assertTrue(result.isSuccess)
        assertContentEquals(byteArrayOf(0xAB.toByte()), received)
    }

    @Test
    fun writeAsFailsWithCharacteristicNotFoundWhenMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val result = peripheral.writeAs(svc, char, 0xAB, Uint8Codec)
        assertIs<CharacteristicNotFoundException>(result.exceptionOrNull())
    }

    @Test
    fun writeAsFailsWithNotReadyWhenPeripheralNotConnected() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(write = true)
                    onWrite { _, _ -> }
                }
            }
        }
        val result = peripheral.writeAs(svc, char, 0xAB, Uint8Codec)
        val ex = result.exceptionOrNull()
        assertIs<PeripheralNotReadyException>(ex)
        assertEquals(svc, ex.serviceUuid)
        assertEquals(char, ex.characteristicUuid)
        assertIs<State.Disconnected>(ex.currentState)
    }

    @Test
    fun observeAsDecodesNotifications() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(notify = true)
                    onObserve { flowOf(byteArrayOf(0x10), byteArrayOf(0x20)) }
                }
            }
        }
        peripheral.connect()
        val values = peripheral.observeAs(svc, char, Uint8Codec).toList()
        assertEquals(listOf(0x10, 0x20), values)
    }

    @Test
    fun observeAsRoutesDecodeFailuresToCallback() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(notify = true)
                    onObserve {
                        flowOf(byteArrayOf(0x10), byteArrayOf(), byteArrayOf(0x20))
                    }
                }
            }
        }
        peripheral.connect()
        val failures = mutableListOf<ByteArray>()
        val values = peripheral
            .observeAs(
                svc,
                char,
                Uint8Codec,
                backpressure = BackpressureStrategy.Unbounded,
                onDecodeFailure = { failures.add(it) },
            )
            .toList()
        assertEquals(listOf(0x10, 0x20), values)
        assertEquals(1, failures.size)
        assertEquals(0, failures[0].size)
    }

    @Test
    fun observeAsReturnsEmptyWhenCharacteristicMissing() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val values = peripheral.observeAs(svc, char, Uint8Codec).toList()
        assertTrue(values.isEmpty())
    }

    @Test
    fun observeAsDefersResolutionUntilReady() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(notify = true)
                    onObserve { flowOf(byteArrayOf(0x10), byteArrayOf(0x20)) }
                }
            }
        }
        val collected = async { peripheral.observeAs(svc, char, Uint8Codec).toList() }
        yield()
        peripheral.connect()
        assertEquals(listOf(0x10, 0x20), collected.await())
    }

    @Test
    fun observeAsDefersResolutionWhileServiceChanged() = runTest {
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(notify = true)
                    onObserve { flowOf(byteArrayOf(0x10)) }
                }
            }
        }
        peripheral.connect()
        peripheral.simulateServiceChangedIndication()

        val collected = async { peripheral.observeAs(svc, char, Uint8Codec).toList() }
        yield()
        peripheral.simulateRediscoverySucceeded()
        assertEquals(listOf(0x10), collected.await())
    }

    @Test
    fun readAsPropagatesCancellationInsteadOfWrappingInResult() = runTest {
        val unblock = CompletableDeferred<ByteArray>()
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(read = true)
                    onRead { unblock.await() }
                }
            }
        }
        peripheral.connect()

        val pending = async { peripheral.readAs(svc, char, Uint8Codec) }
        yield()
        pending.cancel()
        try {
            pending.await()
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun writeAsPropagatesCancellationInsteadOfWrappingInResult() = runTest {
        val unblock = CompletableDeferred<Unit>()
        val peripheral = FakePeripheral {
            service("180f") {
                characteristic("2a19") {
                    properties(write = true)
                    onWrite { _, _ -> unblock.await() }
                }
            }
        }
        peripheral.connect()

        val pending = async { peripheral.writeAs(svc, char, 0xAB, Uint8Codec) }
        yield()
        pending.cancel()
        try {
            pending.await()
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun dispatchReturnsNotFoundWhenReadyAndCharAbsent() {
        val readyStates = listOf<State>(
            State.Connected.Ready,
            State.Connected.BondingChange,
        )
        readyStates.forEach { state ->
            val ex = dispatchLookupFailure(state, emptyServices, svc, char)
            assertIs<CharacteristicNotFoundException>(ex, "expected NotFound for state $state")
            assertEquals(svc, ex.serviceUuid)
            assertEquals(char, ex.characteristicUuid)
        }
    }

    @Test
    fun dispatchReturnsNotReadyWhenPeripheralNotReadyToResolve() {
        val notReadyStates = listOf<State>(
            State.Connecting.Transport,
            State.Connecting.Authenticating,
            State.Connecting.Discovering,
            State.Connecting.Configuring,
            State.Connected.ServiceChanged,
            State.Disconnecting.Requested,
            State.Disconnecting.Error,
            State.Disconnected.ByRequest,
            State.Disconnected.ByRemote,
            State.Disconnected.ByTimeout,
            State.Disconnected.BySystemEvent,
        )
        notReadyStates.forEach { state ->
            val ex = dispatchLookupFailure(state, emptyServices, svc, char)
            assertIs<PeripheralNotReadyException>(ex, "expected NotReady for state $state")
            assertEquals(state, ex.currentState)
            assertEquals(svc, ex.serviceUuid)
            assertEquals(char, ex.characteristicUuid)
        }
    }

    @Test
    fun dispatchReturnsNotReadyWhenServicesNotYetDiscovered() {
        val ex = dispatchLookupFailure(State.Connected.Ready, null, svc, char)
        assertIs<PeripheralNotReadyException>(ex)
        assertEquals(State.Connected.Ready, ex.currentState)
    }

    private val emptyServices: List<DiscoveredService> = emptyList()
}
