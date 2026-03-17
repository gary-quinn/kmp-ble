package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
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
    public fun findCharacteristic(serviceUuid: Uuid, characteristicUuid: Uuid): Characteristic?
    public fun findDescriptor(serviceUuid: Uuid, characteristicUuid: Uuid, descriptorUuid: Uuid): Descriptor?

    // --- GATT Operations ---
    public suspend fun read(characteristic: Characteristic): ByteArray
    public suspend fun write(characteristic: Characteristic, data: ByteArray, writeType: WriteType)

    /**
     * Observe notifications/indications from a characteristic.
     *
     * The returned flow survives disconnects and auto-resubscribes on reconnect:
     * - Emits [Observation.Value] when data is received
     * - Emits [Observation.Disconnected] when connection is lost
     * - Resumes emitting [Observation.Value] when reconnected
     * - Completes normally when reconnection exhausts max attempts
     *
     * May be called before connecting — CCCD will be enabled when connection is established.
     *
     * @param characteristic The characteristic to observe (must support notify or indicate)
     * @param backpressure Strategy for handling backpressure when values arrive faster than consumed
     * @return A cold flow that emits [Observation] events
     */
    public fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<Observation>

    /**
     * Observe raw notification/indication values from a characteristic.
     *
     * Similar to [observe], but provides transparent reconnection — the flow suspends during
     * disconnects and resumes when reconnected, without emitting disconnect events.
     *
     * The returned flow survives disconnects and auto-resubscribes on reconnect:
     * - Emits [ByteArray] when data is received
     * - Suspends (no emission) during disconnect
     * - Resumes emitting [ByteArray] when reconnected
     * - Completes normally when reconnection exhausts max attempts
     *
     * Use this when you want to process values without handling connection state changes.
     * Consumers will see a gap in data during disconnects but no error handling is required.
     *
     * May be called before connecting — CCCD will be enabled when connection is established.
     *
     * @param characteristic The characteristic to observe (must support notify or indicate)
     * @param backpressure Strategy for handling backpressure when values arrive faster than consumed
     * @return A cold flow that emits raw [ByteArray] values
     */
    public fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<ByteArray>

    // --- Descriptors ---
    public suspend fun readDescriptor(descriptor: Descriptor): ByteArray
    public suspend fun writeDescriptor(descriptor: Descriptor, data: ByteArray)

    // --- Info ---
    public suspend fun readRssi(): Int
    public suspend fun requestMtu(mtu: Int): Int
    public val maximumWriteValueLength: StateFlow<Int>

    // --- L2CAP ---

    /**
     * Open an L2CAP Connection-Oriented Channel to this peripheral.
     *
     * L2CAP channels provide high-throughput, stream-oriented communication
     * that bypasses GATT. Use for firmware updates, bulk data transfer,
     * or any scenario requiring sustained throughput.
     *
     * ## Prerequisites
     *
     * - Peripheral must be connected (state = [State.Connected.Ready])
     * - Peripheral must support L2CAP and advertise the PSM
     * - PSM is typically discovered via a GATT characteristic
     *
     * ## Example
     *
     * ```kotlin
     * // Read PSM from a GATT characteristic (device-specific)
     * val psmBytes = peripheral.read(psmCharacteristic)
     * val psm = psmBytes.toInt()
     *
     * // Open channel
     * val channel = peripheral.openL2capChannel(psm)
     *
     * // Use channel
     * channel.write(data)
     * channel.incoming.collect { response -> ... }
     *
     * // Close when done
     * channel.close()
     * ```
     *
     * @param psm Protocol/Service Multiplexer identifying the L2CAP service
     * @param secure If true, requires an encrypted connection (default: true).
     *               **Platform behavior varies:**
     *               - **iOS:** This parameter is ignored. CoreBluetooth determines
     *                 encryption at the connection level, not per-channel. All L2CAP
     *                 channels inherit the connection's security level.
     *               - **Android:** When true, uses `createL2capChannel()` (encrypted);
     *                 when false, uses `createInsecureL2capChannel()`.
     * @return Open L2CAP channel ready for communication
     * @throws com.atruedev.kmpble.l2cap.L2capException.NotConnected if peripheral is not connected
     * @throws com.atruedev.kmpble.l2cap.L2capException.OpenFailed if channel cannot be opened
     * @throws com.atruedev.kmpble.l2cap.L2capException.NotSupported if L2CAP is not available
     */
    public suspend fun openL2capChannel(psm: Int, secure: Boolean = true): L2capChannel
}
