package com.atruedev.kmpble.backend

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.permissions.PermissionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(KmpBleBackendApi::class, ExperimentalUuidApi::class)
internal class FakeBackend(
    override val id: String = "fake",
    override val priority: Int = 0,
    private val supported: Boolean = true,
    val peripheralFactory: (Identifier, String?) -> FakePeripheralTransport = { _, _ -> FakePeripheralTransport() },
) : BleBackend {
    val scan = FakeScanTransport()
    val adapter = FakeAdapterTransport()
    val createdPeripherals = CopyOnWriteArrayList<Pair<Identifier, String?>>()
    var permissions: PermissionResult = PermissionResult.Granted
    var gattServer: FakeGattServerTransport = FakeGattServerTransport()
    var advertiser: FakeAdvertiserTransport = FakeAdvertiserTransport()
    var l2capListener: FakeL2capListenerTransport = FakeL2capListenerTransport()

    override fun isSupported(): Boolean = supported

    override fun checkPermissions(): PermissionResult = permissions

    override fun createAdapterTransport(): AdapterTransport = adapter

    override fun createScanTransport(): ScanTransport = scan

    override fun createPeripheralTransport(
        identifier: Identifier,
        handle: String?,
    ): PeripheralTransport {
        createdPeripherals += identifier to handle
        return peripheralFactory(identifier, handle)
    }

    override fun createGattServerTransport(): GattServerTransport = gattServer

    override fun createAdvertiserTransport(): AdvertiserTransport = advertiser

    override fun createL2capListenerTransport(): L2capListenerTransport = l2capListener
}

@OptIn(KmpBleBackendApi::class)
internal class FakeScanTransport : ScanTransport {
    val records = Channel<ScanRecord>(Channel.UNLIMITED)
    val requests = CopyOnWriteArrayList<ScanRequest>()

    override fun scan(request: ScanRequest): Flow<ScanRecord> {
        requests += request
        return records.receiveAsFlow()
    }
}

@OptIn(KmpBleBackendApi::class)
internal class FakeAdapterTransport : AdapterTransport {
    val mutableState = MutableStateFlow<BluetoothAdapterState>(BluetoothAdapterState.On)
    var closed = false
    override val state: StateFlow<BluetoothAdapterState> = mutableState
    override val capabilities: AdapterCapabilities =
        AdapterCapabilities(supportsExtendedAdvertising = true, supportsLe2mPhy = true)

    override fun bondedDevices(): List<Identifier> = listOf(Identifier("AA:BB:CC:DD:EE:FF"))

    override fun close() {
        closed = true
    }
}

@OptIn(KmpBleBackendApi::class, ExperimentalUuidApi::class)
internal class FakePeripheralTransport(
    override val features: PeripheralFeatures =
        PeripheralFeatures(supportsReliableWrite = true, supportsBonding = true, supportsL2cap = true),
    var services: List<GattServiceRecord> = defaultServices(),
) : PeripheralTransport {
    @Volatile var listener: PeripheralEventListener? = null
    var connectCalls = 0
    var disconnectCalls = 0
    var discoverCalls = 0
    var closeCalls = 0
    var bondCalls = 0
    var connectFailure: Throwable? = null
    var connectGate: CompletableDeferred<Unit>? = null
    var discoverFailure: Throwable? = null
    var onDiscover: (suspend () -> Unit)? = null
    var setNotifyLatency: Duration = Duration.ZERO
    var bond: BondState = BondState.NotBonded
    var bondFailure: Throwable? = null
    var mtuValue = 185
    var rssiValue = -42
    var lastPairingHandler: PairingHandler? = null
    var connected = false
    val values = mutableMapOf<Long, ByteArray>()
    val writes = CopyOnWriteArrayList<Triple<Long, ByteArray, WriteType>>()
    val reliableWrites = CopyOnWriteArrayList<Pair<Long, ByteArray>>()
    val notifyCalls = CopyOnWriteArrayList<Pair<Long, Boolean>>()
    val l2capStreams = CopyOnWriteArrayList<FakeL2capStream>()

    override fun setEventListener(listener: PeripheralEventListener?) {
        this.listener = listener
    }

    override suspend fun connect(options: ConnectionOptions) {
        connectCalls++
        connectGate?.await()
        connectFailure?.let { throw it }
        connected = true
    }

    override suspend fun disconnect() {
        disconnectCalls++
        val wasConnected = connected
        connected = false
        if (wasConnected) listener?.onEvent(PeripheralEvent.Disconnected())
    }

    override suspend fun discoverServices(): List<GattServiceRecord> {
        discoverCalls++
        discoverFailure?.let { throw it }
        onDiscover?.invoke()
        return services
    }

    override suspend fun read(characteristic: Long): ByteArray {
        requireLink()
        return values[characteristic] ?: byteArrayOf(0x4B)
    }

    override suspend fun write(
        characteristic: Long,
        data: ByteArray,
        type: WriteType,
    ) {
        requireLink()
        writes += Triple(characteristic, data, type)
    }

    override suspend fun writeReliable(
        characteristic: Long,
        data: ByteArray,
    ) {
        requireLink()
        reliableWrites += characteristic to data
    }

    override suspend fun readDescriptor(descriptor: Long): ByteArray {
        requireLink()
        return byteArrayOf(0x01, 0x00)
    }

    override suspend fun writeDescriptor(
        descriptor: Long,
        data: ByteArray,
    ) {
        requireLink()
        writes += Triple(descriptor, data, WriteType.WithResponse)
    }

    override suspend fun setNotify(
        characteristic: Long,
        enabled: Boolean,
    ) {
        delay(setNotifyLatency)
        requireLink()
        notifyCalls += characteristic to enabled
    }

    override suspend fun readRssi(): Int = rssiValue

    override suspend fun mtu(): Int = mtuValue

    override suspend fun bondState(): BondState = bond

    override suspend fun createBond() {
        bondCalls++
        bondFailure?.let { throw it }
        bond = BondState.Bonded
    }

    override fun removeBond(): BondRemovalResult {
        bond = BondState.NotBonded
        return BondRemovalResult.Success
    }

    override fun setPairingHandler(handler: PairingHandler?) {
        lastPairingHandler = handler
    }

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
    ): L2capStream {
        if (psm == REJECTED_PSM) throw L2capException.OpenFailed(psm, "rejected")
        return FakeL2capStream(psm).also { l2capStreams += it }
    }

    override fun close() {
        closeCalls++
    }

    fun emit(event: PeripheralEvent) {
        listener?.onEvent(event)
    }

    fun dropLink() {
        connected = false
        emit(PeripheralEvent.Disconnected())
    }

    private fun requireLink() {
        if (!connected) throw BleException(ConnectionLost("fake link down"))
    }

    companion object {
        const val SERVICE_HANDLE = 1L
        const val HEART_RATE_HANDLE = 2L
        const val CCCD_HANDLE = 3L
        const val CONTROL_HANDLE = 4L
        const val REJECTED_PSM = 0x99
        val SERVICE_UUID: Uuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_UUID: Uuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
        val CONTROL_UUID: Uuid = Uuid.parse("00002a39-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: Uuid = Uuid.parse("00002902-0000-1000-8000-00805f9b34fb")

        fun defaultServices(): List<GattServiceRecord> =
            listOf(
                GattServiceRecord(
                    handle = SERVICE_HANDLE,
                    uuid = SERVICE_UUID,
                    characteristics =
                        listOf(
                            GattCharacteristicRecord(
                                handle = HEART_RATE_HANDLE,
                                uuid = HEART_RATE_UUID,
                                properties = Characteristic.Properties(read = true, notify = true),
                                descriptors = listOf(GattDescriptorRecord(CCCD_HANDLE, CCCD_UUID)),
                            ),
                            GattCharacteristicRecord(
                                handle = CONTROL_HANDLE,
                                uuid = CONTROL_UUID,
                                properties = Characteristic.Properties(write = true, writeWithoutResponse = true),
                                descriptors = emptyList(),
                            ),
                        ),
                ),
            )
    }
}

@OptIn(KmpBleBackendApi::class)
internal class FakeL2capStream(
    override val psm: Int,
    override val mtu: Int = 247,
) : L2capStream {
    @Volatile var streamListener: L2capStreamListener? = null
    val written = CopyOnWriteArrayList<ByteArray>()
    var closed = false
    var writeFailure: Throwable? = null

    override fun setListener(listener: L2capStreamListener?) {
        streamListener = listener
    }

    override suspend fun write(data: ByteArray) {
        writeFailure?.let { throw it }
        written += data
    }

    override fun close() {
        closed = true
    }
}

@OptIn(KmpBleBackendApi::class)
internal class FakeL2capListenerTransport : L2capListenerTransport {
    @Volatile var onAccept: ((L2capStream) -> Unit)? = null
    var assignedPsm = 0x80
    var publishFailure: Throwable? = null
    var hang = false
    var closed = false

    override fun setAcceptListener(listener: ((L2capStream) -> Unit)?) {
        onAccept = listener
    }

    override suspend fun publish(secure: Boolean): Int {
        publishFailure?.let { throw it }
        if (hang) awaitCancellation()
        return assignedPsm
    }

    override fun close() {
        closed = true
    }
}

@OptIn(KmpBleBackendApi::class, ExperimentalUuidApi::class)
internal class FakeGattServerTransport(
    override val reportsConnections: Boolean = true,
) : GattServerTransport {
    @Volatile var listener: GattServerEventListener? = null
    var openedServices: List<ServerServiceSpec>? = null
    var openFailure: Throwable? = null
    var closed = false
    val responses = Channel<Triple<Long, GattStatus, ByteArray?>>(Channel.UNLIMITED)
    val notifications = CopyOnWriteArrayList<Triple<Uuid, Identifier?, Boolean>>()

    override fun setEventListener(listener: GattServerEventListener?) {
        this.listener = listener
    }

    override suspend fun open(services: List<ServerServiceSpec>) {
        openFailure?.let { throw it }
        openedServices = services
    }

    override fun respond(
        requestId: Long,
        status: GattStatus,
        value: ByteArray?,
    ) {
        responses.trySend(Triple(requestId, status, value))
    }

    override suspend fun notify(
        characteristic: Uuid,
        device: Identifier?,
        data: ByteArray,
        indicate: Boolean,
    ) {
        notifications += Triple(characteristic, device, indicate)
    }

    override fun close() {
        closed = true
    }

    fun emit(event: GattServerEvent) {
        listener?.onEvent(event)
    }
}

@OptIn(KmpBleBackendApi::class)
internal class FakeAdvertiserTransport(
    override val maxAdvertisingSets: Int = 1,
) : AdvertiserTransport {
    val started = CopyOnWriteArrayList<Pair<Int, AdvertisingSetSpec>>()
    val stopped = CopyOnWriteArrayList<Int>()
    var startFailure: Throwable? = null
    var closed = false

    override suspend fun start(
        setId: Int,
        spec: AdvertisingSetSpec,
    ) {
        startFailure?.let { throw it }
        started += setId to spec
    }

    override suspend fun stop(setId: Int) {
        stopped += setId
    }

    override fun close() {
        closed = true
    }
}

internal fun gattFailure(operation: String): BleException = BleException(GattError(operation, GattStatus.Failure))
