package com.atruedev.kmpble.backend

import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Connection and GATT client operations for one remote device.
 *
 * Core serializes every call through the peripheral's GATT queue and state machine, so a
 * transport never sees two GATT operations at once. Suspending calls complete when the stack
 * confirms the operation and fail with [com.atruedev.kmpble.error.BleException] carrying a
 * common [BleError]. Attribute handles are the [GattServiceRecord] / [GattCharacteristicRecord] /
 * [GattDescriptorRecord] handles from the most recent [discoverServices].
 *
 * Unsolicited callbacks (link loss, notifications, service changes) go to the listener set with
 * [setEventListener]; they may arrive on any thread.
 */
@KmpBleBackendApi
public interface PeripheralTransport : AutoCloseable {
    public val features: PeripheralFeatures

    public fun setEventListener(listener: PeripheralEventListener?)

    /** Returns once the link is up. Core applies [ConnectionOptions.timeouts] around this call. */
    public suspend fun connect(options: ConnectionOptions)

    /** Returns once the link is down or the stack gave up. Must not throw when already disconnected. */
    public suspend fun disconnect()

    /** Full GATT table, including descriptors. */
    public suspend fun discoverServices(): List<GattServiceRecord>

    public suspend fun read(characteristic: Long): ByteArray

    /** Writes one PDU-sized chunk; core splits payloads by `maximumWriteValueLength`. */
    public suspend fun write(
        characteristic: Long,
        data: ByteArray,
        type: WriteType,
    )

    public suspend fun writeReliable(
        characteristic: Long,
        data: ByteArray,
    ): Unit = throw UnsupportedOperationException("Reliable write is not supported by this backend")

    public suspend fun readDescriptor(descriptor: Long): ByteArray

    public suspend fun writeDescriptor(
        descriptor: Long,
        data: ByteArray,
    )

    public suspend fun setNotify(
        characteristic: Long,
        enabled: Boolean,
    )

    public suspend fun readRssi(): Int

    /** Negotiated ATT MTU. Core derives `maximumWriteValueLength` as `mtu - 3`. */
    public suspend fun mtu(): Int

    public suspend fun bondState(): BondState = BondState.Unknown

    public suspend fun createBond(): Unit =
        throw UnsupportedOperationException("Bonding is not supported by this backend")

    public fun removeBond(): BondRemovalResult =
        BondRemovalResult.NotSupported("Bond removal is not supported by this backend")

    public fun setPairingHandler(handler: PairingHandler?) {}

    public suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
    ): L2capStream = throw L2capException.NotSupported("L2CAP channels are not supported by this backend")

    /** Releases the device. Disconnects best-effort; must be idempotent and non-blocking. */
    override fun close()
}

@KmpBleBackendApi
public class PeripheralFeatures(
    public val supportsReliableWrite: Boolean = false,
    public val supportsBonding: Boolean = false,
    public val supportsL2cap: Boolean = false,
)

@KmpBleBackendApi
public fun interface PeripheralEventListener {
    public fun onEvent(event: PeripheralEvent)
}

@KmpBleBackendApi
public sealed interface PeripheralEvent {
    /** The link dropped. [error] is `null` for a clean or requested disconnect. */
    public class Disconnected(
        public val error: BleError? = null,
    ) : PeripheralEvent

    /** The adapter was powered off or became unavailable while the device was known. */
    public data object AdapterOff : PeripheralEvent

    /** Notification or indication payload. */
    public class ValueChanged(
        public val characteristic: Long,
        public val value: ByteArray,
    ) : PeripheralEvent

    /** The remote GATT table changed; handles from the last discovery are stale. */
    public data object ServicesChanged : PeripheralEvent

    public class MtuChanged(
        public val mtu: Int,
    ) : PeripheralEvent

    public class BondStateChanged(
        public val state: BondState,
    ) : PeripheralEvent
}

@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class GattServiceRecord(
    public val handle: Long,
    public val uuid: Uuid,
    public val characteristics: List<GattCharacteristicRecord>,
)

@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class GattCharacteristicRecord(
    public val handle: Long,
    public val uuid: Uuid,
    public val properties: Characteristic.Properties,
    public val descriptors: List<GattDescriptorRecord>,
)

@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class GattDescriptorRecord(
    public val handle: Long,
    public val uuid: Uuid,
)
