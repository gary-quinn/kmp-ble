@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ReconnectionHandler
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.l2cap.AndroidL2capChannel
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.internal.LifecycleSlots
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.ObservationToObservation
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.internal.awaitGatt
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.internal.findDescriptor
import com.atruedev.kmpble.quirks.QuirkRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class AndroidPeripheral internal constructor(
    internal val device: BluetoothDevice,
    context: Context,
    internal val quirkRegistry: QuirkRegistry,
) : Peripheral {
    public constructor(device: BluetoothDevice, context: Context) :
        this(device, context, QuirkRegistry.getInstance())

    override val identifier: Identifier = Identifier(device.address)
    internal val peripheralContext = PeripheralContext(identifier)
    internal val bridge = AndroidGattBridge(device, context)

    internal val pendingOps = PendingOperations()
    internal val observationManager = ObservationManager(peripheralContext.dispatcher)
    internal val slots = LifecycleSlots()

    internal val nativeCharMap = mutableMapOf<Characteristic, BluetoothGattCharacteristic>()
    internal val nativeDescMap = mutableMapOf<Descriptor, BluetoothGattDescriptor>()

    internal val bondManager = AndroidBondManager(device, context, peripheralContext)

    @OptIn(ExperimentalBleApi::class)
    internal val pairingRequestHandler =
        AndroidPairingRequestHandler(device, context, peripheralContext.scope, peripheralContext.dispatcher)

    override val state: StateFlow<State> get() = peripheralContext.state
    override val bondState: StateFlow<BondState> get() = bondManager.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength

    @Volatile
    internal var closed = false

    /**
     * Confined to [peripheralContext.dispatcher]. Read by [handleConnectionStateChanged]
     * to decide whether bonding is required for the freshly-established link.
     */
    internal var currentConnectionOptions: ConnectionOptions? = null

    internal val reconnectionHandler =
        ReconnectionHandler(
            scope = peripheralContext.scope,
            stateFlow = peripheralContext.state,
            connectAction = { opts ->
                connect(opts.copy(reconnectionStrategy = ReconnectionStrategy.None))
            },
            onMaxAttemptsExhausted = { observationManager.onPermanentDisconnect() },
        )

    internal val activeL2capChannels =
        MutableStateFlow<List<AndroidL2capChannel>>(emptyList())

    init {
        bridge.onEvent = { event -> handleGattEvent(event) }
        logEvent(
            BleLogEvent.GattOperation(
                identifier,
                "DeviceQuirks: ${quirkRegistry.describe()}",
                uuid = null,
                status = null,
            ),
        )
    }

    @OptIn(ExperimentalBleApi::class)
    override suspend fun connect(options: ConnectionOptions) {
        connectInternal(options)
    }

    @OptIn(ExperimentalBleApi::class)
    override suspend fun disconnect() {
        disconnectInternal()
    }

    @OptIn(ExperimentalBleApi::class)
    override fun close() {
        if (closed) return
        closed = true
        reconnectionHandler.stop()
        pairingRequestHandler.closeSync()
        bondManager.stop()
        closeL2capChannels()
        bridge.close()
        observationManager.clear()
        peripheralContext.close()
        PeripheralRegistry.remove(identifier)
    }

    @ExperimentalBleApi
    override fun removeBond(): BondRemovalResult {
        checkNotClosed()
        return bondManager.removeBond()
    }

    override suspend fun refreshServices(): List<DiscoveredService> {
        checkNotClosed()
        return withContext(peripheralContext.dispatcher) {
            // Invalidate the Android GATT cache before discovery so the platform
            // performs a fresh radio-level service discovery instead of returning
            // cached data instantly. Best-effort: refreshDeviceCache() uses
            // reflection on the hidden BluetoothGatt.refresh() method and may
            // fail silently on some OEMs.
            bridge.refreshDeviceCache()
            val deferred = slots.armDiscovery()
            if (!bridge.discoverServices()) {
                slots.clearDiscovery()
                throw BleException(OperationFailed("discoverServices initiation failed"))
            }
            try {
                withTimeout(SERVICE_DISCOVERY_TIMEOUT) { deferred.await() }
            } finally {
                slots.clearDiscovery()
            }
        }
    }

    override fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic? = services.value.findCharacteristic(serviceUuid, characteristicUuid)

    override fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor? = services.value.findDescriptor(serviceUuid, characteristicUuid, descriptorUuid)

    override suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeChar(characteristic)
            val result =
                pendingOps.awaitGatt(PendingOp.CharacteristicRead, "read") {
                    bridge.readCharacteristic(native)
                }
            if (!result.status.isSuccess()) throw BleException(GattError("read", result.status))
            result.value
        }
    }

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        checkNotClosed()
        LargeWriteHandler.validateForWriteType(data, maximumWriteValueLength.value, writeType)

        val native = requireNativeChar(characteristic)
        val androidWriteType = writeType.toAndroidWriteType()
        val chunks = LargeWriteHandler.chunk(data, maximumWriteValueLength.value)

        peripheralContext.gattQueue.enqueue {
            for (chunk in chunks) {
                val status =
                    pendingOps.awaitGatt(PendingOp.CharacteristicWrite, "write") {
                        bridge.writeCharacteristic(native, chunk, androidWriteType)
                    }
                if (!status.isSuccess()) throw BleException(GattError("write", status))
            }
        }
    }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> {
        checkNotClosed()
        return buildObservationFlow(
            characteristic = characteristic,
            backpressure = backpressure,
            observationManager = observationManager,
            isReady = { peripheralContext.state.value is State.Connected.Ready },
            enable = ::enableNotifications,
            disable = ::disableNotificationsBestEffort,
            mapper = ObservationToObservation,
        )
    }

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> {
        checkNotClosed()
        return buildObservationFlow(
            characteristic = characteristic,
            backpressure = backpressure,
            observationManager = observationManager,
            isReady = { peripheralContext.state.value is State.Connected.Ready },
            enable = ::enableNotifications,
            disable = ::disableNotificationsBestEffort,
            mapper = ObservationToBytes,
        )
    }

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeDesc(descriptor)
            val result =
                pendingOps.awaitGatt(PendingOp.DescriptorRead, "readDescriptor") {
                    bridge.readDescriptor(native)
                }
            if (!result.status.isSuccess()) throw BleException(GattError("descriptorRead", result.status))
            result.value
        }
    }

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        checkNotClosed()
        peripheralContext.gattQueue.enqueue {
            val native = requireNativeDesc(descriptor)
            val status =
                pendingOps.awaitGatt(PendingOp.DescriptorWrite, "writeDescriptor") {
                    bridge.writeDescriptor(native, data)
                }
            if (!status.isSuccess()) throw BleException(GattError("descriptorWrite", status))
        }
    }

    override suspend fun readRssi(): Int {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            pendingOps.awaitGatt(PendingOp.RssiRead, "readRssi") { bridge.readRemoteRssi() }
        }
    }

    override suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            pendingOps.awaitGatt(PendingOp.MtuRequest, "requestMtu") { bridge.requestMtu(mtu) }
        }
    }

    @ExperimentalBleApi
    override suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean {
        checkNotClosed()
        val androidPriority =
            when (priority) {
                ConnectionPriority.Balanced -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
                ConnectionPriority.High -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
                ConnectionPriority.LowPower -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
            }
        return peripheralContext.gattQueue.enqueue {
            bridge.requestConnectionPriority(androidPriority)
        }
    }

    @ExperimentalBleApi
    override suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? {
        checkNotClosed()
        val txMask = phyToMask(tx)
        val rxMask = phyToMask(rx)
        return peripheralContext.gattQueue.enqueue {
            val result =
                pendingOps.awaitGatt(PendingOp.PhyUpdate, "setPreferredPhy") {
                    bridge.setPreferredPhy(
                        txMask,
                        rxMask,
                        BluetoothDevice.PHY_OPTION_NO_PREFERRED,
                    )
                }
            if (!result.status.isSuccess()) return@enqueue null
            PhyResult(
                tx = phyConstantToPhy(result.txPhyConstant),
                rx = phyConstantToPhy(result.rxPhyConstant),
            )
        }
    }

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel = openL2capChannelInternal(psm, secure, mtu)
}
