package com.atruedev.kmpble.codec

import com.atruedev.kmpble.peripheral.state.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * Sealed root of failures raised by [readAs] / [writeAs] / [observeAs] that
 * originate inside the codec layer (as opposed to the underlying BLE op).
 *
 * Match exhaustively when distinguishing codec failures from arbitrary BLE
 * errors that may also surface via [Result.failure]:
 *
 * ```
 * when (val cause = result.exceptionOrNull()) {
 *     is PeripheralCodecException -> when (cause) {
 *         is CharacteristicNotFoundException -> ...
 *         is PeripheralNotReadyException -> ...
 *         is DecodeFailureException -> ...
 *     }
 *     else -> ... // BLE error, cancellation, etc.
 * }
 * ```
 *
 * Adding a subclass is a source-breaking change for callers using `when` as
 * an expression that exhaustively matches the sealed subclasses without an
 * `else` branch. Callers matching on the sealed root, or with an `else`
 * branch, are unaffected at compile time.
 */
public sealed class PeripheralCodecException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Raised when the peripheral is ready to resolve the characteristic and the
 * characteristic is absent from the device's GATT database. Ready to resolve
 * means [Peripheral.services] is populated and [Peripheral.state] is a stable
 * [State.Connected] substate other than [State.Connected.ServiceChanged].
 */
public class CharacteristicNotFoundException(
    public val serviceUuid: Uuid,
    public val characteristicUuid: Uuid,
) : PeripheralCodecException("Characteristic $characteristicUuid not found in service $serviceUuid")

/**
 * Raised when the peripheral is not ready to resolve the characteristic:
 * discovery has not completed, the cached service list is pending
 * invalidation ([State.Connected.ServiceChanged]), or the peripheral is
 * disconnecting/disconnected. Retry after [Peripheral.state] reaches
 * [State.Connected.Ready], reconnecting first if necessary. The
 * [currentState] is captured at dispatch time for diagnostics.
 */
public class PeripheralNotReadyException(
    public val serviceUuid: Uuid,
    public val characteristicUuid: Uuid,
    public val currentState: State,
) : PeripheralCodecException(
        "Peripheral not ready (state: $currentState) to resolve " +
            "characteristic $characteristicUuid in service $serviceUuid",
    )

/**
 * Raised when a [BleDecoder] rejects a value read from a characteristic. The
 * raw [bytes] are preserved for logging or inspection of the malformed
 * payload, and the underlying decoder exception is preserved as the [cause].
 */
public class DecodeFailureException(
    public val bytes: ByteArray,
    cause: Throwable,
) : PeripheralCodecException("Decoder rejected ${bytes.size} bytes", cause)

/**
 * Reads a characteristic by service+characteristic UUID and decodes the value.
 *
 * Failure cases:
 * - [PeripheralNotReadyException]: the peripheral is not ready to resolve
 *   the characteristic (discovery incomplete, service change pending, or
 *   peripheral disconnecting/disconnected). Retry after
 *   [State.Connected.Ready], reconnecting first if necessary.
 * - [CharacteristicNotFoundException]: the peripheral was ready to resolve
 *   and the characteristic is absent from the device's GATT database.
 * - [DecodeFailureException]: read succeeded but [decoder] threw on the
 *   raw bytes.
 * - any other exception thrown by the underlying read.
 *
 * [kotlin.coroutines.cancellation.CancellationException] is always propagated
 * to the caller, never wrapped into [Result.failure].
 *
 * Use the [Peripheral.read] overload that takes a
 * [com.atruedev.kmpble.gatt.Characteristic] directly when you already hold a
 * reference and prefer raw exception propagation.
 */
public suspend fun <T> Peripheral.readAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    decoder: BleDecoder<T>,
): Result<T> {
    val char = findCharacteristic(serviceUuid, characteristicUuid)
        ?: return Result.failure(lookupFailure(serviceUuid, characteristicUuid))
    return try {
        val bytes = read(char)
        val decoded = try {
            decoder.decode(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DecodeFailureException(bytes, e)
        }
        Result.success(decoded)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/**
 * Encodes [value] with [encoder] and writes it to a characteristic addressed
 * by service+characteristic UUID. Failure cases mirror [readAs]:
 * - [PeripheralNotReadyException]: peripheral not ready to resolve.
 * - [CharacteristicNotFoundException]: ready to resolve, char absent.
 * - any exception thrown by the underlying write.
 *
 * [kotlin.coroutines.cancellation.CancellationException] is always propagated
 * to the caller, never wrapped into [Result.failure].
 */
public suspend fun <T> Peripheral.writeAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    value: T,
    encoder: BleEncoder<T>,
    writeType: WriteType = WriteType.WithResponse,
): Result<Unit> {
    val char = findCharacteristic(serviceUuid, characteristicUuid)
        ?: return Result.failure(lookupFailure(serviceUuid, characteristicUuid))
    return try {
        write(char, encoder.encode(value), writeType)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/**
 * Observes notifications/indications from a characteristic addressed by
 * service+characteristic UUID, decoding each payload with [decoder].
 *
 * Resolution is deferred until the peripheral is ready to resolve (services
 * populated and state is a stable [State.Connected] substate other than
 * [State.Connected.ServiceChanged]), matching the readiness predicate used
 * by [readAs] and [writeAs]. When the characteristic is absent from the
 * resolved service list, the flow completes empty, matching the lenient
 * SIG-profile convention. Payloads that fail to decode are routed to
 * [onDecodeFailure] and dropped from the output stream.
 *
 * The flow may be collected before [Peripheral.connect]; it will suspend
 * until the readiness predicate first becomes true.
 */
public fun <T> Peripheral.observeAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    decoder: BleDecoder<T>,
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    onDecodeFailure: (ByteArray) -> Unit = {},
): Flow<T> = flow {
    awaitReadyToResolve()
    val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return@flow
    observeValues(char, backpressure).collect { bytes ->
        val decoded = try {
            decoder.decode(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onDecodeFailure(bytes)
            return@collect
        }
        emit(decoded)
    }
}

private suspend fun Peripheral.awaitReadyToResolve() {
    combine(state, services, ::Pair).first { (s, sv) -> isReadyToResolve(s, sv) }
}

private fun Peripheral.lookupFailure(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
): PeripheralCodecException =
    dispatchLookupFailure(state.value, services.value, serviceUuid, characteristicUuid)

internal fun dispatchLookupFailure(
    state: State,
    services: List<DiscoveredService>?,
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
): PeripheralCodecException =
    if (isReadyToResolve(state, services)) {
        CharacteristicNotFoundException(serviceUuid, characteristicUuid)
    } else {
        PeripheralNotReadyException(serviceUuid, characteristicUuid, state)
    }

private fun isReadyToResolve(
    state: State,
    services: List<DiscoveredService>?,
): Boolean =
    services != null &&
        state is State.Connected &&
        state !is State.Connected.ServiceChanged
