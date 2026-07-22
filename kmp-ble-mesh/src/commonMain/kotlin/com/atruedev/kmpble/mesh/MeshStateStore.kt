package com.atruedev.kmpble.mesh

/**
 * Persistence SPI for BLE Mesh network state.
 *
 * Mesh network state (sequence numbers, IV Index, keys) MUST survive
 * application restarts. Loss of sequence numbers means permanent exclusion
 * from the mesh network due to replay protection.
 *
 * Implement this interface with your platform's secure storage:
 * - Android: DataStore, SharedPreferences, or EncryptedSharedPreferences
 * - iOS: Keychain or UserDefaults
 * - JVM: File-based storage
 *
 * For testing, use [InMemoryMeshStateStore].
 */
public interface MeshStateStore {
    /**
     * Atomically save the complete network state.
     *
     * The implementation MUST be durable before returning (write to disk,
     * not just cache). Called after every sequence number change and IV
     * Index update.
     *
     * @param state The complete network state to persist.
     * @return Success or failure result.
     */
    public suspend fun saveNetworkState(state: MeshNetworkState): Result<Unit>

    /**
     * Load the last saved network state.
     *
     * @return The persisted state, or null if no state was previously saved
     *   (e.g., first launch or data was cleared).
     */
    public suspend fun loadNetworkState(): Result<MeshNetworkState?>

    /**
     * Clear all persisted mesh state.
     *
     * Called when leaving a mesh network or resetting all data.
     */
    public suspend fun clearAll(): Result<Unit>
}

/**
 * Serializable snapshot of the complete mesh network state.
 *
 * Contains all data needed to restore the network after an app restart:
 * keys, IV Index, unicast address, and per-node state including the
 * critical sequence numbers.
 */
public data class MeshNetworkState(
    /** Current IV Index value. */
    val ivIndex: IvIndex,

    /** Our unicast address on the mesh network. */
    val unicastAddress: MeshAddress.UnicastAddress,

    /** All network keys known to this node. */
    val networkKeys: List<NetworkKey>,

    /** All application keys known to this node. */
    val applicationKeys: List<ApplicationKey>,

    /** Per-node persisted state. */
    val nodes: List<PersistedNodeState>,

    /** All known group addresses and their labels. */
    val groupAddresses: List<MeshAddress.GroupAddress> = emptyList(),

    /** Metadata for debugging. */
    val lastUpdatedTimestamp: Long = 0L,
)

/**
 * Per-node state that must be persisted across restarts.
 */
public data class PersistedNodeState(
    /** The node's primary unicast address. */
    val unicastAddress: MeshAddress.UnicastAddress,

    /** The node's device key (for configuration messages). */
    val deviceKey: DeviceKey,

    /** Last known sequence number for this node's outbound messages. */
    val lastSequenceNumber: UInt,

    /** Node feature flags. */
    val features: NodeFeatures = NodeFeatures(),
)

/**
 * In-memory implementation of [MeshStateStore] for testing.
 *
 * Does not survive process restarts. Use only for tests and development.
 * Production apps should implement [MeshStateStore] with durable storage.
 */
public class InMemoryMeshStateStore : MeshStateStore {
    private var state: MeshNetworkState? = null

    override suspend fun saveNetworkState(state: MeshNetworkState): Result<Unit> {
        this.state = state
        return Result.success(Unit)
    }

    override suspend fun loadNetworkState(): Result<MeshNetworkState?> =
        Result.success(state)

    override suspend fun clearAll(): Result<Unit> {
        state = null
        return Result.success(Unit)
    }
}
