package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionParameterUpdateResult
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capChannel
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

    public suspend fun requestMtu(mtu: Int): Int

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
}
