package com.atruedev.kmpble.testing

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public typealias ReadHandler = suspend () -> ByteArray
public typealias WriteHandler = suspend (data: ByteArray, writeType: WriteType) -> Unit
public typealias ObserveHandler = () -> Flow<ByteArray>
public typealias L2capHandler = suspend (psm: Int) -> L2capChannel

@OptIn(ExperimentalUuidApi::class)
internal data class FakeCharacteristicConfig(
    val characteristic: Characteristic,
    val readHandler: ReadHandler?,
    val writeHandler: WriteHandler?,
    val observeHandler: ObserveHandler?,
    val respondAfterDuration: Duration?,
    val failWithError: BleError?,
)

@OptIn(ExperimentalUuidApi::class)
public class FakePeripheralBuilder {
    private val services = mutableListOf<DiscoveredService>()
    internal val characteristicConfigs: MutableList<FakeCharacteristicConfig> = mutableListOf()
    private var connectHandler: suspend () -> Result<Unit> = { Result.success(Unit) }
    private var disconnectHandler: suspend () -> Result<Unit> = { Result.success(Unit) }
    internal var l2capHandler: L2capHandler? = null
    public var identifier: Identifier = Identifier("fake-peripheral")

    public fun service(
        uuid: String,
        block: FakeServiceBuilder.() -> Unit = {},
    ) {
        service(uuidFrom(uuid), block)
    }

    public fun service(
        uuid: Uuid,
        block: FakeServiceBuilder.() -> Unit = {},
    ) {
        val builder = FakeServiceBuilder(uuid).apply(block)
        val result = builder.build()
        services += result.first
        characteristicConfigs += result.second
    }

    public fun onConnect(handler: suspend () -> Result<Unit>) {
        connectHandler = handler
    }

    public fun onDisconnect(handler: suspend () -> Result<Unit>) {
        disconnectHandler = handler
    }

    /**
     * Configure L2CAP channel opening behavior.
     * The handler receives the PSM and returns an [L2capChannel] (typically [FakeL2capChannel]).
     */
    public fun onOpenL2capChannel(handler: L2capHandler) {
        l2capHandler = handler
    }

    internal fun build(): FakePeripheral =
        FakePeripheral(
            identifier = identifier,
            fakeServices = services.toList(),
            characteristicConfigs = characteristicConfigs.toList(),
            onConnectHandler = connectHandler,
            onDisconnectHandler = disconnectHandler,
            onL2capHandler = l2capHandler,
        )
}

@OptIn(ExperimentalUuidApi::class)
public class FakeServiceBuilder(private val serviceUuid: Uuid) {
    private val characteristics = mutableListOf<Characteristic>()
    private val configs = mutableListOf<FakeCharacteristicConfig>()

    public fun characteristic(
        uuid: String,
        block: FakeCharacteristicBuilder.() -> Unit = {},
    ) {
        characteristic(uuidFrom(uuid), block)
    }

    public fun characteristic(
        uuid: Uuid,
        block: FakeCharacteristicBuilder.() -> Unit = {},
    ) {
        val builder = FakeCharacteristicBuilder(serviceUuid, uuid).apply(block)
        val (char, config) = builder.build()
        characteristics += char
        configs += config
    }

    internal fun build(): Pair<DiscoveredService, List<FakeCharacteristicConfig>> {
        val service = DiscoveredService(uuid = serviceUuid, characteristics = characteristics.toList())
        return service to configs.toList()
    }
}

@OptIn(ExperimentalUuidApi::class)
public class FakeCharacteristicBuilder(
    private val serviceUuid: Uuid,
    private val uuid: Uuid,
) {
    private var props = Characteristic.Properties()
    private var readHandler: ReadHandler? = null
    private var writeHandler: WriteHandler? = null
    private var observeHandler: ObserveHandler? = null
    private var respondAfterDuration: Duration? = null
    private var failWithError: BleError? = null

    public fun properties(
        read: Boolean = false,
        write: Boolean = false,
        writeWithoutResponse: Boolean = false,
        signedWrite: Boolean = false,
        notify: Boolean = false,
        indicate: Boolean = false,
    ) {
        props =
            Characteristic.Properties(
                read = read, write = write, writeWithoutResponse = writeWithoutResponse,
                signedWrite = signedWrite, notify = notify, indicate = indicate,
            )
    }

    public fun onRead(handler: ReadHandler) {
        readHandler = handler
    }

    public fun onWrite(handler: WriteHandler) {
        writeHandler = handler
    }

    public fun onObserve(handler: ObserveHandler) {
        observeHandler = handler
    }

    /**
     * Add a delay before executing read/write handlers on this characteristic.
     * The delay is cancellable — if the coroutine is cancelled during the delay,
     * the operation cancels cleanly without invoking the handler.
     * Does not affect [onObserve] (notifications have their own timing via Flow).
     */
    public fun respondAfter(duration: Duration) {
        respondAfterDuration = duration
    }

    /**
     * Cause all operations (read, write, observe) on this characteristic to throw
     * the specified [BleError]. Takes precedence over [onRead]/[onWrite]/[onObserve] —
     * if both are set, the error is thrown and handlers are never invoked.
     * Can be combined with [respondAfter] to delay before failing.
     */
    public fun failWith(error: BleError) {
        failWithError = error
    }

    internal fun build(): Pair<Characteristic, FakeCharacteristicConfig> {
        val char = Characteristic(serviceUuid, uuid, props)
        val config =
            FakeCharacteristicConfig(
                char,
                readHandler,
                writeHandler,
                observeHandler,
                respondAfterDuration,
                failWithError,
            )
        return char to config
    }
}

@OptIn(ExperimentalUuidApi::class)
public fun FakePeripheral(block: FakePeripheralBuilder.() -> Unit): FakePeripheral {
    return FakePeripheralBuilder().apply(block).build()
}
