package com.atruedev.kmpble.backend

import com.atruedev.kmpble.Identifier
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * LE discovery for one backend. Filtering, deduplication, and timeouts are applied by core,
 * so a transport reports every advertisement it sees.
 */
@KmpBleBackendApi
public interface ScanTransport {
    /**
     * Cold flow: collecting starts discovery, cancelling stops it. Fails with
     * [com.atruedev.kmpble.scanner.ScanFailedException] when discovery cannot start or breaks.
     */
    public fun scan(request: ScanRequest): Flow<ScanRecord>
}

/**
 * @property serviceUuids Hint for hardware-side filtering. Non-empty only when every filter
 *   group requires one of these UUIDs, so a transport may drop advertisements that carry none.
 * @property legacyOnly Mirrors [com.atruedev.kmpble.scanner.ScannerConfig.legacyOnly].
 */
@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class ScanRequest(
    public val serviceUuids: List<Uuid>,
    public val legacyOnly: Boolean,
)

/**
 * One advertisement as seen by a backend.
 *
 * @property handle Backend-specific device handle passed back to
 *   [BleBackend.createPeripheralTransport], for example a BlueZ D-Bus object path.
 * @property rawAdvertising AD structures rebuilt from the parsed fields, when available.
 */
@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class ScanRecord(
    public val handle: String,
    public val identifier: Identifier,
    public val name: String?,
    public val rssi: Int,
    public val txPower: Int?,
    public val isConnectable: Boolean,
    public val serviceUuids: List<Uuid>,
    public val manufacturerData: Map<Int, ByteArray>,
    public val serviceData: Map<Uuid, ByteArray>,
    public val rawAdvertising: ByteArray? = null,
    public val timestampNanos: Long = System.nanoTime(),
)
