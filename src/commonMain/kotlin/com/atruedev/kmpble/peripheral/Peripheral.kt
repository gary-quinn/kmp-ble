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
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.direction.DirectionFindingParameters
import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.isochronous.IsochronousChannel
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.periodic.PeriodicAdvertisingSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public interface Peripheral : AutoCloseable {
    public val identifier: Identifier

    // --- Connection ---
    public suspend fun connect(options: ConnectionOptions = ConnectionOptions())

    public suspend fun disconnect()

    override fun close()

    public val state: StateFlow<State>
    public val bondState: StateFlow<BondState>

    @ExperimentalBleApi
    public fun removeBond(): BondRemovalResult

    // --- Discovery ---
    public val services: StateFlow<List<DiscoveredService>?>

    public suspend fun refreshServices(): List<DiscoveredService>

    public fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic?

    public fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor?

    // --- GATT Operations ---
    public suspend fun read(characteristic: Characteristic): ByteArray

    public suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    )

    /**
     * Observe notifications/indications from a characteristic. The returned
     * flow survives disconnects and auto-resubscribes on reconnect -- emits
     * [Observation.Value] with data, [Observation.Disconnected] on connection
     * loss, resumes after reconnect, and completes when retries exhaust.
     *
     * May be called before connecting; CCCD is enabled on connect.
     *
     * @param backpressure Controls delivery when values arrive faster than
     *   consumed: [BackpressureStrategy.Latest] drops intermediate values,
     *   [BackpressureStrategy.Buffer] retains a fixed number,
     *   [BackpressureStrategy.Unbounded] for lossless delivery.
     */
    public fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<Observation>

    /**
     * Observe raw notification/indication values from a characteristic.
     *
     * Same lifecycle as [observe], but provides transparent reconnection --
     * suspends during disconnects and resumes on reconnect without emitting
     * disconnect events. Consumers see a gap in data during disconnects but
     * no error handling is required.
     *
     * May be called before connecting; CCCD is enabled on connect.
     *
     * @param backpressure Controls delivery when values arrive faster than
     *   consumed (see [observe] for strategy details).
     */
    public fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<ByteArray>

    // --- Descriptors ---
    public suspend fun readDescriptor(descriptor: Descriptor): ByteArray

    public suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    )

    // --- Info ---
    public suspend fun readRssi(): Int

    /**
     * Negotiate the ATT Maximum Transmission Unit for this connection.
     *
     * The ATT MTU determines the maximum payload per GATT operation.
     * A larger MTU reduces packet overhead and improves throughput --
     * critical for firmware updates, large data transfers, and L2CAP
     * co-channel usage. The default ATT MTU is 23 bytes (per the BLE
     * specification), leaving 20 bytes of application payload after the
     * 3-byte ATT header.
     *
     * The platform may negotiate a value lower than requested. Always
     * read the returned value -- do not assume the request was granted
     * in full. The negotiated MTU is also exposed as [mtu] for reactive
     * consumers.
     *
     * After negotiation, [maximumWriteValueLength] reflects the usable
     * payload (MTU minus the 3-byte ATT header) so callers can chunk
     * writes appropriately.
     *
     * **Android**: Maps to `BluetoothGatt.requestMtu()`. The result is
     * delivered asynchronously via `onMtuChanged`; this method suspends
     * until the callback or [ConnectionOptions.timeouts] mtuNegotiation
     * timeout fires.
     *
     * **iOS**: CoreBluetooth negotiates MTU automatically and does not
     * expose a public request API. This method returns the current maximum
     * write-value length from CoreBluetooth plus the 3-byte ATT header,
     * clamped to at least the BLE default (23 bytes). The [mtu] parameter
     * is ignored on iOS.
     *
     * @param mtu The desired ATT MTU in bytes. Must be >= 23 (BLE
     *   minimum). Typical values: 185 (LE Data Length Extension),
     *   517 (BLE 5.0 max).
     * @return The actual negotiated ATT MTU (>= 23). May differ from
     *   the requested value.
     * @throws com.atruedev.kmpble.error.BleException wrapping
     *   [com.atruedev.kmpble.error.GattError] if negotiation fails.
     * @throws com.atruedev.kmpble.error.BleException wrapping
     *   [com.atruedev.kmpble.error.PeripheralTimeout] if the platform
     *   callback does not arrive within [com.atruedev.kmpble.connection.OperationTimeouts.mtuNegotiation].
     */
    public suspend fun requestMtu(mtu: Int): Int

    /**
     * The current ATT Maximum Transmission Unit for this connection.
     *
     * Updated after [requestMtu] completes and when the remote device
     * initiates MTU renegotiation. Starts at the BLE default (23 bytes)
     * and reflects the most recently negotiated value.
     *
     * Use [maximumWriteValueLength] for write-payload capacity (MTU - 3
     * byte ATT header). Read and descriptor operations may have different
     * size limits depending on the platform GATT implementation.
     */
    public val mtu: StateFlow<Int>

    /**
     * Request a new connection priority (interval/latency/timeout).
     *
     * Android maps to `BluetoothGatt.requestConnectionPriority`. iOS is a
     * no-op (CoreBluetooth does not expose this API).
     *
     * Use [ConnectionPriority.High] before high-throughput operations
     * (L2CAP, firmware updates) to drop interval to ~11-15 ms. Reset to
     * [ConnectionPriority.Balanced] when done.
     *
     * @return `true` if the request was dispatched; `false` if unsupported
     *   or the GATT layer is not ready.
     */
    @ExperimentalBleApi
    public suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean

    /**
     * Request updated LE connection parameters from the central.
     *
     * The central may accept, reject, or negotiate alternative values. Returns
     * the actual negotiated parameters reported by the platform.
     *
     * Android maps to `BluetoothGatt.requestConnectionPriority()` with the
     * closest priority level; returns `null` on API <29 where the callback is
     * unavailable. iOS returns `null` (CoreBluetooth has no public API).
     *
     * Use instead of [requestConnectionPriority] when you need specific
     * interval/latency/timeout control.
     *
     * @return [ConnectionParameterUpdateResult] with negotiated values,
     *   or `null` if unsupported.
     */
    @ExperimentalBleApi
    public suspend fun requestConnectionParameterUpdate(params: ConnectionParameters): ConnectionParameterUpdateResult?

    /**
     * Request preferred PHYs for the LE connection (BLE 5.0).
     *
     * Android maps to `BluetoothGatt.setPreferredPhy` (API 26+); returns the
     * controller's choice which may differ from the request. iOS returns
     * `null` (CoreBluetooth has no public PHY API).
     *
     * Use [Phy.Le2M] for both directions to double throughput on BLE 5.0
     * devices. Falls back to 1M on older devices.
     *
     * @return [PhyResult] with negotiated TX/RX PHYs, or `null` if unsupported.
     */
    @ExperimentalBleApi
    public suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult?

    /**
     * Read the current PHY for this connection.
     *
     * Android maps to `BluetoothGatt.readPhy()` (API 26+); suspends until
     * callback or timeout. iOS returns `null` (no public API).
     *
     * @return [PhyResult] with current TX/RX PHYs, or `null` if unsupported.
     */
    @ExperimentalBleApi
    public suspend fun readPhy(): PhyResult?

    /**
     * Hot flow of spontaneous PHY change events. Emits [PhyUpdate] when the
     * controller negotiates a new PHY (triggered by [setPreferredPhy] or
     * autonomously). Only emits while connected. iOS never emits.
     */
    @ExperimentalBleApi
    public val phyUpdate: Flow<PhyUpdate>

    /**
     * Request LE Connection Subrating parameters for this connection
     * (Bluetooth 5.3+).
     *
     * Connection Subrating allows a peripheral to switch to a lower subrated
     * connection interval during idle periods, then snap back to the full
     * connection interval for data transfer. This provides better power
     * efficiency while maintaining fast response times.
     *
     * Android maps to `BluetoothGatt.requestConnectionSubrating()` (API 33+).
     * iOS returns [ConnectionSubratingResult.NotSupported] -- CoreBluetooth
     * handles subrating internally and does not expose a public API.
     *
     * @return [ConnectionSubratingResult.Accepted] with the negotiated
     *   parameters, [ConnectionSubratingResult.NotSupported] if the platform
     *   lacks this API, or [ConnectionSubratingResult.Rejected] if the
     *   request was denied.
     */
    @ExperimentalBleApi
    public suspend fun requestConnectionSubrating(parameters: ConnectionSubratingParameters): ConnectionSubratingResult

    public val maximumWriteValueLength: StateFlow<Int>

    // --- L2CAP ---

    /**
     * Open an L2CAP Connection-Oriented Channel for high-throughput,
     * stream-oriented communication bypassing GATT.
     *
     * Peripheral must be connected and support L2CAP. PSM is typically
     * discovered via a GATT characteristic.
     *
     * @param psm Protocol/Service Multiplexer identifying the L2CAP service.
     * @param secure Requires encrypted connection (default true). Ignored on
     *   iOS (CoreBluetooth determines encryption at connection level).
     * @param mtu Optional MTU hint (positive). iOS uses this value instead of
     *   the 2048-byte default (CoreBluetooth hides negotiated MTU). Ignored
     *   on Android (queried from socket).
     * @throws IllegalArgumentException if [mtu] is not positive.
     * @throws com.atruedev.kmpble.l2cap.L2capException.NotConnected if not connected.
     * @throws com.atruedev.kmpble.l2cap.L2capException.OpenFailed if channel open fails.
     * @throws com.atruedev.kmpble.l2cap.L2capException.NotSupported if unavailable.
     */
    public suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean = true,
        mtu: Int? = null,
    ): L2capChannel

    // --- Isochronous Channels ---

    /**
     * Open a LE Audio Isochronous Channel for time-bounded streaming.
     *
     * Isochronous channels (Bluetooth 5.2+) provide connection-oriented (CIS)
     * or broadcast (BIS) streaming with guaranteed latency bounds. Used by
     * LE Audio profiles for hearing aids, broadcast audio, and low-latency
     * audio devices.
     *
     * @throws IsochronousException.NotSupported if isochronous channels are
     *         not available on this platform
     * @throws IsochronousException.NotConnected if the peripheral is not connected
     * @throws IsochronousException.OpenFailed if channel setup fails
     */
    public suspend fun openIsochronousChannel(): IsochronousChannel

    // --- Periodic Advertising Sync Transfer (PAST, BLE 5.1) ---

    /**
     * Receive a periodic advertising sync transferred from a connected peer via PAST.
     *
     * After a successful PAST transfer, the returned [PeriodicAdvertisingSync] can
     * receive periodic advertising reports from the original advertiser without
     * performing its own scan and sync procedure.
     *
     * Callers must be connected to the peer that initiated the transfer. The peer
     * uses [PeriodicAdvertisingSync.transferTo] to send the sync.
     *
     * @throws com.atruedev.kmpble.periodic.PastException.NotSupported if PAST
     *   is not available on this platform or OS version.
     * @throws com.atruedev.kmpble.periodic.PastException.NotConnected if this
     *   peripheral is not connected.
     * @throws com.atruedev.kmpble.periodic.PastException.TransferFailed if the
     *   transfer protocol fails.
     */
    @ExperimentalBleApi
    public suspend fun receivePastSync(): PeriodicAdvertisingSync

    // --- Direction Finding (BLE 5.1+) ---

    /**
     * Request direction finding (AoA/AoD) on this connection.
     *
     * Bluetooth 5.1+ Direction Finding uses the Constant Tone Extension (CTE)
     * and multi-antenna arrays to estimate the angle of arriving or departing
     * signals. This enables high-accuracy indoor positioning, asset tracking,
     * and wayfinding applications.
     *
     * Android maps to `BluetoothDevice#REQUEST_TYPE_DIRECTION_FINDING` (API 34+).
     * iOS returns [DirectionFindingResult.NotSupported] -- CoreBluetooth does
     * not expose a public direction finding API.
     *
     * @return [DirectionFindingResult.Angle] with azimuth/elevation,
     *   [DirectionFindingResult.NotSupported] if the platform lacks this API,
     *   or [DirectionFindingResult.Failed] if the request was denied.
     */
    @ExperimentalBleApi
    public suspend fun requestDirectionFinding(parameters: DirectionFindingParameters): DirectionFindingResult

    // --- LE Data Length Extension (BLE 4.2+) ---

    /**
     * Negotiated LE Data Length Extension parameters for this connection.
     *
     * BLE 4.2+ controllers negotiate larger link-layer payloads (up to 251 bytes
     * vs the default 27) and longer transmission times for improved throughput.
     * When DLE is supported and negotiated, this reflects the current parameters.
     *
     * Returns `null` when DLE is unsupported or parameters are not yet available.
     * Both Android and iOS controllers handle DLE internally; this property
     * exposes platform-reported values when the OS makes them available.
     */
    public val dataLengthParameters: StateFlow<DataLengthParameters?>
}
