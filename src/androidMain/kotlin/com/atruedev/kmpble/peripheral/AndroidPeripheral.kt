@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionParameterUpdateResult
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.ConnectionSubratingParameters
import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.connection.DataLengthParameters
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.connection.internal.ReconnectionHandler
import com.atruedev.kmpble.direction.DirectionFindingParameters
import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.isochronous.IsochronousChannel
import com.atruedev.kmpble.isochronous.IsochronousException
import com.atruedev.kmpble.l2cap.AndroidL2capChannel
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.periodic.PastException
import com.atruedev.kmpble.periodic.PeriodicAdvertisingSync
import com.atruedev.kmpble.peripheral.internal.LifecycleSlots
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.internal.findDescriptor
import com.atruedev.kmpble.quirks.QuirkRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val observationPersistence = ObservationPersistence()
    internal val slots = LifecycleSlots()

    internal val nativeCharMap = mutableMapOf<Characteristic, BluetoothGattCharacteristic>()
    internal val nativeDescMap = mutableMapOf<Descriptor, BluetoothGattDescriptor>()

    internal val _phyUpdate = MutableSharedFlow<PhyUpdate>(extraBufferCapacity = 16)

    internal val bondManager = AndroidBondManager(device, context, peripheralContext)

    @OptIn(ExperimentalBleApi::class)
    internal val pairingRequestHandler =
        AndroidPairingRequestHandler(device, context, peripheralContext.scope, peripheralContext.dispatcher)

    override val state: StateFlow<ConnectionState> get() = peripheralContext.state
    override val bondState: StateFlow<BondState> get() = bondManager.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength
    override val mtu: StateFlow<Int> get() = peripheralContext.mtu
    override val dataLengthParameters: StateFlow<DataLengthParameters?> get() = peripheralContext.dataLengthParameters

    @Volatile
    internal var closed = false

    /**
     * Confined to [peripheralContext.dispatcher]. Read by [handleConnectionStateChanged]
     * to decide whether bonding is required for the freshly-established link.
     */
    internal var currentConnectionOptions: ConnectionOptions? = null

    /** Stored during [connect] for GATT ops to reference per-operation timeouts. */
    internal var currentTimeouts: OperationTimeouts = OperationTimeouts()

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
        observationManager.onObservationsChanged = { observations ->
            observationPersistence.save(identifier.value, observations)
        }
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

    /**
     * Restore observations that were persisted from a previous session.
     *
     * Re-subscribes to characteristics with their original backpressure
     * strategies. Call after [connect] and service discovery when the
     * peripheral was previously observed in a prior app session.
     *
     * This is a no-op if no observations were persisted. Stale entries
     * (characteristics that no longer exist) are silently skipped.
     *
     * Safe to call multiple times; already-subscribed observations are
     * not duplicated (the ObservationManager tracks by UUID key).
     */
    internal suspend fun restorePersistedObservations() {
        val saved = observationPersistence.restore(identifier.value)
        for (obs in saved) {
            observationManager.subscribe(obs.key.serviceUuid, obs.key.charUuid, obs.backpressure)
        }
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
        observationPersistence.clear(identifier.value)
        peripheralContext.close()
        PeripheralRegistry.remove(identifier)
    }

    @ExperimentalBleApi
    override fun removeBond(): BondRemovalResult {
        checkNotClosed()
        return bondManager.removeBond()
    }

    override suspend fun refreshServices(): List<DiscoveredService> = refreshServicesGatt()

    override fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic? = services.value.findCharacteristic(serviceUuid, characteristicUuid)

    override fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor? = services.value.findDescriptor(serviceUuid, characteristicUuid, descriptorUuid)

    override suspend fun read(characteristic: Characteristic): ByteArray = readCharacteristicGatt(characteristic)

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        writeCharacteristicGatt(characteristic, data, writeType)
    }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> = observeGatt(characteristic, backpressure)

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> = observeValuesGatt(characteristic, backpressure)

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray = readDescriptorGatt(descriptor)

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        writeDescriptorGatt(descriptor, data)
    }

    override suspend fun readRssi(): Int = readRssiGatt()

    override suspend fun requestMtu(mtu: Int): Int = requestMtuGatt(mtu)

    @ExperimentalBleApi
    override suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean =
        requestConnectionPriorityGatt(priority)

    @ExperimentalBleApi
    override suspend fun requestConnectionParameterUpdate(
        params: ConnectionParameters,
    ): ConnectionParameterUpdateResult? = requestConnectionParameterUpdateGatt(params)

    @ExperimentalBleApi
    override suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? = setPreferredPhyGatt(tx, rx)

    @ExperimentalBleApi
    override suspend fun readPhy(): PhyResult? = readPhyGatt()

    @ExperimentalBleApi
    override val phyUpdate: Flow<PhyUpdate> = _phyUpdate

    @ExperimentalBleApi
    override suspend fun requestConnectionSubrating(
        parameters: ConnectionSubratingParameters,
    ): ConnectionSubratingResult = requestConnectionSubratingGatt(parameters)

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel = openL2capChannelInternal(psm, secure, mtu)

    override suspend fun openIsochronousChannel(): IsochronousChannel =
        throw IsochronousException.NotSupported(
            "LE Audio isochronous channels are not publicly available on Android",
        )

    override suspend fun receivePastSync(): PeriodicAdvertisingSync =
        throw PastException.NotSupported(
            "PAST is not available on Android (requires API 31+)",
        )

    @ExperimentalBleApi
    override suspend fun requestDirectionFinding(parameters: DirectionFindingParameters): DirectionFindingResult =
        DirectionFindingResult.NotSupported
}
