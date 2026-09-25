package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.backend.GattCharacteristicRecord
import com.atruedev.kmpble.backend.GattDescriptorRecord
import com.atruedev.kmpble.backend.GattServiceRecord
import com.atruedev.kmpble.backend.L2capStream
import com.atruedev.kmpble.backend.PeripheralEvent
import com.atruedev.kmpble.backend.PeripheralEventListener
import com.atruedev.kmpble.backend.PeripheralFeatures
import com.atruedev.kmpble.backend.PeripheralTransport
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.macos.Macos
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

/**
 * [PeripheralTransport] over one `CBPeripheral`. Core serializes GATT calls, so one pending
 * request slot is enough; like iOS, a value update for a characteristic with a read in flight
 * completes the read and every other update is a notification.
 */
@OptIn(ExperimentalUuidApi::class)
internal class MacosPeripheralTransport(
    private val stack: MacosStack,
    private val identifier: String,
    private val settleTimeout: Duration = SETTLE_TIMEOUT,
    private val writeReadyTimeout: Duration = WRITE_READY_TIMEOUT,
) : PeripheralTransport {
    override val features: PeripheralFeatures =
        PeripheralFeatures(supportsReliableWrite = false, supportsBonding = false, supportsL2cap = true)

    private class Pending(
        val matches: (MacosEvent) -> Boolean,
        val result: CompletableDeferred<MacosEvent> = CompletableDeferred(),
    )

    private val api get() = stack.api

    @Volatile private var listener: PeripheralEventListener? = null

    @Volatile private var handle: Long = 0

    private val gattPending = AtomicReference<Pending?>(null)
    private val l2capPending = AtomicReference<Pending?>(null)
    private val connectWaiter = AtomicReference<CompletableDeferred<Unit>?>(null)
    private val disconnectWaiter = AtomicReference<CompletableDeferred<Unit>?>(null)
    private val writeReady = Channel<Unit>(Channel.CONFLATED)
    private val linkUp = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val errorFreeLossPending = AtomicBoolean(false)
    private val cccdOwners = ConcurrentHashMap<Long, Long>()

    override fun setEventListener(listener: PeripheralEventListener?) {
        this.listener = listener
    }

    override suspend fun connect(options: ConnectionOptions) {
        if (closed.get()) throw BleException(closedError())
        val state =
            try {
                stack.awaitCentralSettled(settleTimeout)
            } catch (e: MissingUsageDescriptionException) {
                throw BleException(
                    ConnectionFailed(
                        e.message ?: "",
                        ConnectionFailureReason.UNKNOWN,
                        Macos.ERROR_USAGE_DESCRIPTION_MISSING,
                    ),
                )
            } catch (e: UnsatisfiedLinkError) {
                throw BleException(
                    ConnectionFailed(e.message ?: "", ConnectionFailureReason.UNKNOWN, Macos.ERROR_NATIVE_UNAVAILABLE),
                )
            }
        if (state != CbManagerState.POWERED_ON) {
            throw BleException(
                ConnectionFailed(
                    "Bluetooth adapter is not powered on",
                    ConnectionFailureReason.UNKNOWN,
                    Macos.ERROR_ADAPTER_OFF,
                ),
            )
        }
        val peripheral = resolve()
        val waiter = CompletableDeferred<Unit>()
        connectWaiter.set(waiter)
        try {
            if (closed.get()) throw BleException(closedError())
            if (!api.connect(peripheral)) {
                throw BleException(ConnectionFailed("CoreBluetooth rejected connect", ConnectionFailureReason.UNKNOWN))
            }
            waiter.await()
        } catch (e: Throwable) {
            if (e is CancellationException || closed.get()) api.cancelConnect(peripheral)
            throw e
        } finally {
            connectWaiter.compareAndSet(waiter, null)
        }
    }

    override suspend fun disconnect() {
        val peripheral = handle.takeIf { it != 0L } ?: return
        val waiter = CompletableDeferred<Unit>()
        disconnectWaiter.set(waiter)
        try {
            val connected = linkUp.get() || api.peripheralState(peripheral) != PERIPHERAL_DISCONNECTED
            api.cancelConnect(peripheral)
            if (connected) withTimeoutOrNull(DISCONNECT_WAIT) { waiter.await() }
        } finally {
            disconnectWaiter.compareAndSet(waiter, null)
        }
    }

    override suspend fun discoverServices(): List<GattServiceRecord> {
        val peripheral = requireHandle()
        cccdOwners.clear()
        val services =
            request<MacosEvent.ServicesDiscovered>("discoverServices", {
                it is MacosEvent.ServicesDiscovered &&
                    it.peripheral == peripheral
            }) {
                api.discoverServices(peripheral)
            }
        requireSuccess("discoverServices", services.status, services.message)
        return services.services.map { service ->
            val characteristics =
                request<MacosEvent.CharacteristicsDiscovered>(
                    "discoverCharacteristics",
                    { it is MacosEvent.CharacteristicsDiscovered && it.service == service.handle },
                ) { api.discoverCharacteristics(peripheral, service.handle) }
            requireSuccess("discoverCharacteristics", characteristics.status, characteristics.message)
            GattServiceRecord(
                handle = service.handle,
                uuid = uuidFrom(service.uuid),
                characteristics =
                    characteristics.characteristics.map { characteristic ->
                        characteristicRecord(peripheral, characteristic)
                    },
            )
        }
    }

    override suspend fun read(characteristic: Long): ByteArray {
        val peripheral = requireHandle()
        val event =
            request<MacosEvent.ValueUpdated>("read", {
                it is MacosEvent.ValueUpdated &&
                    it.characteristic == characteristic
            }) {
                api.readCharacteristic(peripheral, characteristic)
            }
        requireSuccess("read", event.status, event.message)
        return event.value
    }

    override suspend fun write(
        characteristic: Long,
        data: ByteArray,
        type: WriteType,
    ) {
        val peripheral = requireHandle()
        if (type == WriteType.WithoutResponse) {
            awaitWriteReady(peripheral)
            if (!api.writeCharacteristic(
                    peripheral,
                    characteristic,
                    data,
                    withResponse = false,
                )
            ) {
                throw BleException(notConnected("write"))
            }
            return
        }
        val event =
            request<MacosEvent.ValueWritten>("write", {
                it is MacosEvent.ValueWritten &&
                    it.characteristic == characteristic
            }) {
                api.writeCharacteristic(peripheral, characteristic, data, withResponse = true)
            }
        requireSuccess("write", event.status, event.message)
    }

    override suspend fun readDescriptor(descriptor: Long): ByteArray {
        val peripheral = requireHandle()
        val event =
            request<MacosEvent.DescriptorValue>("readDescriptor", {
                it is MacosEvent.DescriptorValue &&
                    it.descriptor == descriptor
            }) {
                api.readDescriptor(peripheral, descriptor)
            }
        requireSuccess("readDescriptor", event.status, event.message)
        return event.value
    }

    override suspend fun writeDescriptor(
        descriptor: Long,
        data: ByteArray,
    ) {
        val peripheral = requireHandle()
        val owner = cccdOwners[descriptor]
        if (owner != null) {
            setNotify(owner, enabled = data.any { it != 0.toByte() })
            return
        }
        val event =
            request<MacosEvent.DescriptorWritten>("writeDescriptor", {
                it is MacosEvent.DescriptorWritten &&
                    it.descriptor == descriptor
            }) {
                api.writeDescriptor(peripheral, descriptor, data)
            }
        requireSuccess("writeDescriptor", event.status, event.message)
    }

    override suspend fun setNotify(
        characteristic: Long,
        enabled: Boolean,
    ) {
        val peripheral = requireHandle()
        val event =
            request<MacosEvent.NotifyState>("setNotify", {
                it is MacosEvent.NotifyState &&
                    it.characteristic == characteristic
            }) {
                api.setNotify(peripheral, characteristic, enabled)
            }
        requireSuccess("setNotify", event.status, event.message)
    }

    override suspend fun readRssi(): Int {
        val peripheral = requireHandle()
        val event =
            request<MacosEvent.Rssi>("readRssi", {
                it is MacosEvent.Rssi && it.peripheral == peripheral
            }) { api.readRssi(peripheral) }
        requireSuccess("readRssi", event.status, event.message)
        return event.rssi
    }

    override suspend fun mtu(): Int {
        val length = api.maximumWriteLength(requireHandle(), withResponse = false)
        return if (length <= 0) DEFAULT_ATT_MTU else length + ATT_HEADER_SIZE
    }

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
    ): L2capStream {
        val peripheral = requireHandle()
        val event =
            request<MacosEvent.L2capOpened>(
                "openL2capChannel",
                { it is MacosEvent.L2capOpened && it.peripheral == peripheral },
                slot = l2capPending,
                overlap = { L2capException.OpenFailed(psm, "another L2CAP channel open is in progress") },
            ) { api.openL2cap(peripheral, psm) }
        if (event.status != 0 || event.channel == 0L) {
            throw L2capException.OpenFailed(psm, event.message ?: "CoreBluetooth status ${event.status}")
        }
        return MacosL2capStream(stack, event.channel, psm)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        errorFreeLossPending.set(false)
        listener = null
        val connecting = connectWaiter.getAndSet(null)
        val peripheral = handle
        if (peripheral != 0L) {
            stack.unregisterPeripheral(peripheral)
            if (linkUp.getAndSet(false) || connecting != null) api.cancelConnect(peripheral)
            api.releasePeripheral(peripheral)
        }
        connecting?.completeExceptionally(BleException(closedError()))
        disconnectWaiter.getAndSet(null)?.complete(Unit)
        failPending(closedError())
    }

    internal fun onEvent(event: MacosEvent) {
        when (event) {
            is MacosEvent.Connected -> {
                linkUp.set(true)
                connectWaiter.get()?.complete(Unit)
            }
            is MacosEvent.ConnectFailed ->
                connectWaiter.get()?.completeExceptionally(
                    BleException(MacosStatus.connectError(event.status, event.message)),
                )
            is MacosEvent.Disconnected -> {
                val error = MacosStatus.disconnectError(event.status, event.message)
                when {
                    error != null || disconnectWaiter.get() != null || !linkUp.get() ->
                        onLinkDown(error, adapterOff = false)
                    adapterWentDown() -> onLinkDown(null, adapterOff = true)
                    else -> deferErrorFreeLoss()
                }
            }
            is MacosEvent.CentralState -> {
                if (event.state != CbManagerState.POWERED_ON) onLinkDown(null, adapterOff = true)
            }
            is MacosEvent.ReadyToWrite -> writeReady.trySend(Unit)
            is MacosEvent.ServicesModified -> listener?.onEvent(PeripheralEvent.ServicesChanged)
            is MacosEvent.ValueUpdated -> {
                if (!completePending(event) && event.status == 0) {
                    listener?.onEvent(PeripheralEvent.ValueChanged(event.characteristic, event.value))
                }
            }
            else -> completePending(event)
        }
    }

    private fun onLinkDown(
        error: BleError?,
        adapterOff: Boolean,
    ) {
        val wasUp = linkUp.getAndSet(false) or errorFreeLossPending.getAndSet(false)
        val loss = error ?: ConnectionLost("Peripheral disconnected", ConnectionFailureReason.LINK_LOSS)
        failPending(loss)
        connectWaiter.get()?.completeExceptionally(
            BleException(
                error ?: ConnectionFailed("Disconnected while connecting", ConnectionFailureReason.LINK_LOSS),
            ),
        )
        disconnectWaiter.get()?.complete(Unit)
        when {
            adapterOff -> if (wasUp) listener?.onEvent(PeripheralEvent.AdapterOff)
            else -> listener?.onEvent(PeripheralEvent.Disconnected(error))
        }
    }

    private fun adapterWentDown(): Boolean =
        api.centralState().let { it != CbManagerState.UNKNOWN && it != CbManagerState.POWERED_ON }

    /**
     * Turning Bluetooth off makes CoreBluetooth report an error-free `didDisconnectPeripheral`
     * while the manager still reads powered on, and `centralManagerDidUpdateState` follows. The
     * loss is reported after [ERROR_FREE_LOSS_GRACE_MS] so a power-off in that window becomes
     * [PeripheralEvent.AdapterOff]; pending requests fail at once.
     */
    private fun deferErrorFreeLoss() {
        linkUp.set(false)
        failPending(ConnectionLost("Peripheral disconnected", ConnectionFailureReason.LINK_LOSS))
        errorFreeLossPending.set(true)
        CompletableFuture.delayedExecutor(ERROR_FREE_LOSS_GRACE_MS, TimeUnit.MILLISECONDS).execute {
            if (errorFreeLossPending.compareAndSet(true, false)) {
                listener?.onEvent(
                    if (adapterWentDown()) PeripheralEvent.AdapterOff else PeripheralEvent.Disconnected(null),
                )
            }
        }
    }

    private fun completePending(event: MacosEvent): Boolean {
        for (slot in listOf(gattPending, l2capPending)) {
            val pending = slot.get() ?: continue
            if (pending.matches(event)) return pending.result.complete(event)
        }
        return false
    }

    private fun failPending(error: BleError) {
        gattPending.getAndSet(null)?.result?.completeExceptionally(BleException(error))
        l2capPending.getAndSet(null)?.result?.completeExceptionally(BleException(error))
    }

    private suspend inline fun <reified T : MacosEvent> request(
        label: String,
        noinline matches: (MacosEvent) -> Boolean,
        slot: AtomicReference<Pending?> = gattPending,
        noinline overlap: () -> Throwable = {
            IllegalStateException("CoreBluetooth $label overlaps another request on $identifier")
        },
        submit: () -> Boolean,
    ): T {
        val pending = Pending(matches)
        if (!slot.compareAndSet(null, pending)) throw overlap()
        try {
            if (!submit()) throw BleException(notConnected(label))
            return pending.result.await() as T
        } finally {
            slot.compareAndSet(pending, null)
        }
    }

    private suspend fun characteristicRecord(
        peripheral: Long,
        characteristic: AttributeLine,
    ): GattCharacteristicRecord {
        val descriptors =
            request<MacosEvent.DescriptorsDiscovered>(
                "discoverDescriptors",
                { it is MacosEvent.DescriptorsDiscovered && it.characteristic == characteristic.handle },
            ) { api.discoverDescriptors(peripheral, characteristic.handle) }
        requireSuccess("discoverDescriptors", descriptors.status, descriptors.message)
        val records = descriptors.descriptors.map { GattDescriptorRecord(it.handle, uuidFrom(it.uuid)) }
        records.firstOrNull { it.uuid == CCCD }?.let { cccdOwners[it.handle] = characteristic.handle }
        return GattCharacteristicRecord(
            handle = characteristic.handle,
            uuid = uuidFrom(characteristic.uuid),
            properties = characteristic.properties.toCharacteristicProperties(),
            descriptors = records,
        )
    }

    private suspend fun awaitWriteReady(peripheral: Long) {
        try {
            withTimeout(writeReadyTimeout) {
                while (!api.canSendWriteWithoutResponse(peripheral)) writeReady.receive()
            }
        } catch (e: TimeoutCancellationException) {
            throw BleException(GattError("write", GattStatus.ConnectionCongested))
        }
    }

    private fun resolve(): Long {
        if (handle != 0L) return handle
        val peripheral = api.peripheralForIdentifier(identifier)
        if (peripheral == 0L) {
            throw BleException(
                ConnectionFailed(
                    "CoreBluetooth does not know peripheral $identifier. Scan for it first.",
                    ConnectionFailureReason.UNKNOWN_DEVICE,
                ),
            )
        }
        handle = peripheral
        stack.registerPeripheral(peripheral, ::onEvent)
        return peripheral
    }

    private fun requireHandle(): Long = handle.takeIf { it != 0L } ?: throw BleException(notConnected("request"))

    private fun requireSuccess(
        operation: String,
        status: Int,
        message: String?,
    ) {
        if (!MacosStatus.isSuccess(status)) throw BleException(MacosStatus.gattError(operation, status, message))
    }

    private fun closedError(): BleError = ConnectionLost("Peripheral closed", ConnectionFailureReason.LINK_LOSS)

    private fun notConnected(operation: String): BleError =
        ConnectionLost(
            "CoreBluetooth rejected $operation: peripheral not connected or Bluetooth off",
            ConnectionFailureReason.LINK_LOSS,
        )

    private companion object {
        const val DEFAULT_ATT_MTU = 23
        const val ATT_HEADER_SIZE = 3
        const val PERIPHERAL_DISCONNECTED = 0
        val SETTLE_TIMEOUT = 10.seconds
        val WRITE_READY_TIMEOUT = 5.seconds
        val DISCONNECT_WAIT = 3.seconds
        const val ERROR_FREE_LOSS_GRACE_MS = 500L
        val CCCD = uuidFrom("2902")
    }
}

/** `CBCharacteristicProperties` bits to common properties. */
internal fun Int.toCharacteristicProperties(): Characteristic.Properties =
    Characteristic.Properties(
        read = this and 0x02 != 0,
        write = this and 0x08 != 0,
        writeWithoutResponse = this and 0x04 != 0,
        signedWrite = this and 0x40 != 0,
        notify = this and 0x10 != 0,
        indicate = this and 0x20 != 0,
    )
