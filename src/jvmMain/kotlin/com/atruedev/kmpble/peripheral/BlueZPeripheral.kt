package com.atruedev.kmpble.peripheral

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
import com.atruedev.kmpble.connection.EncryptionLevel
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.direction.DirectionFindingParameters
import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.isochronous.IsochronousChannel
import com.atruedev.kmpble.isochronous.IsochronousException
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.periodic.PastException
import com.atruedev.kmpble.periodic.PeriodicAdvertisingSync
import com.atruedev.kmpble.peripheral.internal.LifecycleSlots
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.internal.findDescriptor
import com.atruedev.kmpble.scanner.Advertisement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Linux BlueZ-backed [Peripheral] using D-Bus ([org.bluez.Device1], [org.bluez.GattCharacteristic1]).
 *
 * Construct explicitly from a [BlueZScanner] [Advertisement] via [Advertisement.toBlueZPeripheral],
 * or opt in through [com.atruedev.kmpble.scanner.BlueZ.ENABLE_PROPERTY].
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalBleApi::class)
public class BlueZPeripheral internal constructor(
    internal val devicePath: String,
    internal val address: String,
    internal val sessionFactory: BlueZDeviceSessionFactory = DefaultBlueZDeviceSessionFactory,
) : Peripheral {
    public constructor(
        devicePath: String,
        address: String,
    ) : this(devicePath, address, DefaultBlueZDeviceSessionFactory)

    override val identifier: Identifier = Identifier(address)

    internal val peripheralContext = PeripheralContext(identifier)
    internal val pendingOps = PendingOperations()
    internal val observationManager = ObservationManager(peripheralContext.dispatcher)
    internal val slots = LifecycleSlots()

    internal val nativeCharPathMap = mutableMapOf<Characteristic, String>()
    internal val nativeDescPathMap = mutableMapOf<Descriptor, String>()

    internal var deviceSession: BlueZDeviceSession? = null
    internal var currentTimeouts: OperationTimeouts = OperationTimeouts()

    private var _lastConnectionOptions: ConnectionOptions? = null
    override val lastConnectionOptions: ConnectionOptions? get() = _lastConnectionOptions

    private val _closed = AtomicBoolean(false)
    internal val closed: Boolean get() = _closed.get()

    override val state: StateFlow<com.atruedev.kmpble.peripheral.state.State> get() = peripheralContext.state
    override val bondState: StateFlow<BondState> get() = peripheralContext.bondState
    override val encryptionLevel: StateFlow<EncryptionLevel> get() = peripheralContext.encryptionLevel
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength
    override val supportsReliableWrite: Boolean get() = false
    override val mtu: StateFlow<Int> get() = peripheralContext.mtu
    override val dataLengthParameters: StateFlow<DataLengthParameters?> get() = peripheralContext.dataLengthParameters
    override val phyUpdate: Flow<PhyUpdate> = emptyFlow()

    override suspend fun connect(options: ConnectionOptions) {
        _lastConnectionOptions = options
        connectInternal(options)
    }

    override suspend fun disconnect() {
        disconnectInternal()
    }

    override fun close() {
        if (_closed.getAndSet(true)) return
        runCatching { deviceSession?.disconnect() }
        deviceSession?.unregisterHandlers()
        deviceSession?.closeConnection()
        deviceSession = null
        observationManager.clear()
        peripheralContext.close()
        PeripheralRegistry.remove(identifier)
    }

    override fun removeBond(): BondRemovalResult =
        BondRemovalResult.NotSupported("Bond removal is not supported on BlueZ JVM (M2)")

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

    override suspend fun writeReliable(
        characteristic: Characteristic,
        data: ByteArray,
    ): Unit = throw UnsupportedOperationException("Reliable write is not supported on BlueZ JVM")

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

    override suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        require(mtu >= 23) { "MTU must be >= 23" }
        return this.mtu.value
    }

    override suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean = false

    override suspend fun requestConnectionParameterUpdate(
        params: ConnectionParameters,
    ): ConnectionParameterUpdateResult? = null

    override suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? = null

    override suspend fun readPhy(): PhyResult? = null

    override suspend fun requestConnectionSubrating(
        parameters: ConnectionSubratingParameters,
    ): ConnectionSubratingResult = ConnectionSubratingResult.NotSupported

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel = throw L2capException.NotSupported("L2CAP is not supported on BlueZ JVM (M2)")

    override suspend fun openIsochronousChannel(): IsochronousChannel =
        throw IsochronousException.NotSupported("Isochronous channels are not supported on BlueZ JVM")

    override suspend fun receivePastSync(): PeriodicAdvertisingSync =
        throw PastException.NotSupported("PAST is not supported on BlueZ JVM")

    override suspend fun requestDirectionFinding(parameters: DirectionFindingParameters): DirectionFindingResult =
        DirectionFindingResult.NotSupported

    internal fun sessionOrNull(): BlueZDeviceSession? = deviceSession

    internal fun requireSession(): BlueZDeviceSession =
        checkNotNull(deviceSession) { "BlueZ device session is not open" }

    public companion object {
        public const val ERROR_DBUS_UNAVAILABLE: Int = -20
        public const val ERROR_NO_ADAPTER: Int = -21
        public const val ERROR_DEVICE_NOT_FOUND: Int = -22
        public const val ERROR_CONNECT_FAILED: Int = -23
    }
}

/**
 * Creates a [BlueZPeripheral] from a [BlueZScanner] [Advertisement].
 */
public fun Advertisement.toBlueZPeripheral(): Peripheral {
    val dbusPath =
        platformContext as? String
            ?: throw IllegalStateException(
                "Cannot create BlueZPeripheral: Advertisement was not produced by BlueZScanner",
            )
    return PeripheralRegistry.getOrCreate(identifier) {
        BlueZPeripheral(devicePath = dbusPath, address = identifier.value)
    }
}
