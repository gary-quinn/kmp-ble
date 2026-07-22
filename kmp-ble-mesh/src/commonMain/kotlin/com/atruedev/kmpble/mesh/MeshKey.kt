package com.atruedev.kmpble.mesh

/**
 * Key types used in BLE Mesh networking.
 *
 * BLE Mesh uses three key types forming a hierarchy:
 * - **NetworkKey** (NetKey): Shared by all nodes in a subnet. Secures Network Layer messages.
 * - **ApplicationKey** (AppKey): Bound to a NetKey. Secures Access Layer (model) messages.
 * - **DeviceKey** (DevKey): Unique per node. Used for Configuration and Health model messages.
 *
 * All keys are 128-bit (16 bytes) AES keys. Key indices are 12-bit values.
 *
 * ### Key Refresh
 *
 * [NetworkKey] supports key refresh via [KeyRefreshPhase]. The three phases are:
 * 1. **Normal**: Single key in use.
 * 2. **Phase1**: New key distributed, old key still used for transmission.
 * 3. **Phase2**: New key used for transmission, old key still accepted for reception.
 */

/** 12-bit key index (0x000-0xFFF). */
@JvmInline
public value class KeyIndex(public val value: UShort) {
    init {
        require(value <= MAX_VALUE) {
            "Key index out of range 0x000-0xFFF: $value"
        }
    }

    public companion object {
        public const val MAX_VALUE: UShort = 0xFFFu
    }
}

/** Base type for all mesh keys. */
public sealed interface MeshKey {
    /** 12-bit index identifying this key within the network. */
    public val index: KeyIndex

    /** 16-byte AES-128 key material. */
    public val key: ByteArray

    public companion object {
        /** All mesh keys are 128-bit AES keys. */
        public const val KEY_SIZE: Int = 16

        /** Validate key size. */
        public fun requireValidKey(key: ByteArray) {
            require(key.size == KEY_SIZE) {
                "Mesh key must be $KEY_SIZE bytes, got ${key.size}"
            }
        }
    }
}

/**
 * Network Key shared by all nodes in a (sub)net.
 */
public data class NetworkKey(
    override val index: KeyIndex,
    override val key: ByteArray,
    val name: String = "NetKey ${index.value}",
    val phase: KeyRefreshPhase = KeyRefreshPhase.NORMAL,
) : MeshKey {
    init { MeshKey.requireValidKey(key) }

    override fun equals(other: Any?): Boolean =
        other is NetworkKey && other.index == index && other.key.contentEquals(key)

    override fun hashCode(): Int = 31 * index.hashCode() + key.contentHashCode()
}

/**
 * Application Key bound to a Network Key.
 */
public data class ApplicationKey(
    override val index: KeyIndex,
    override val key: ByteArray,
    val boundNetKeyIndex: KeyIndex,
    val name: String = "AppKey ${index.value}",
) : MeshKey {
    init { MeshKey.requireValidKey(key) }

    override fun equals(other: Any?): Boolean =
        other is ApplicationKey &&
            other.index == index &&
            other.key.contentEquals(key) &&
            other.boundNetKeyIndex == boundNetKeyIndex

    override fun hashCode(): Int =
        31 * (31 * index.hashCode() + key.contentHashCode()) + boundNetKeyIndex.hashCode()
}

/**
 * Device Key unique to a single node.
 */
public data class DeviceKey(
    override val key: ByteArray,
) : MeshKey {
    init { MeshKey.requireValidKey(key) }

    override val index: KeyIndex = KeyIndex(0u)

    override fun equals(other: Any?): Boolean =
        other is DeviceKey && other.key.contentEquals(key)

    override fun hashCode(): Int = key.contentHashCode()
}

/**
 * Key Refresh procedure phases for a NetworkKey.
 */
public enum class KeyRefreshPhase {
    NORMAL,
    PHASE_1,
    PHASE_2,
}
