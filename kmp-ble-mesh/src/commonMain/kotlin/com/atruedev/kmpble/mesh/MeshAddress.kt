package com.atruedev.kmpble.mesh

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Address types used in BLE Mesh networking.
 *
 * BLE Mesh uses three address types:
 * - **Unicast** (0x0001-0x7FFF): Assigned to individual elements during provisioning.
 * - **Group** (0xC000-0xFFFF): Multicast addresses for publish-subscribe.
 * - **Virtual** (label-based): Derived from a 128-bit UUID label via hashing.
 *
 * Address 0x0000 is the unassigned address. Addresses 0x8000-0xBFFF are reserved.
 */
public sealed interface MeshAddress : Comparable<MeshAddress> {
    /** 16-bit address value as used in mesh PDUs. */
    public val value: UShort

    override fun compareTo(other: MeshAddress): Int = value.compareTo(other.value)

    /** A 16-bit unicast address assigned to exactly one element on one node. */
    @JvmInline
    public value class UnicastAddress(override val value: UShort) : MeshAddress {
        init {
            require(value in UNICAST_MIN..UNICAST_MAX) {
                "Unicast address out of range 0x0001-0x7FFF: 0x${value.toString(16).uppercase()}"
            }
        }

        public companion object {
            public val UNICAST_MIN: UShort = 0x0001u
            public val UNICAST_MAX: UShort = 0x7FFFu
        }
    }

    /** A 16-bit group address for multicast publish-subscribe communication. */
    @JvmInline
    public value class GroupAddress(override val value: UShort) : MeshAddress {
        init {
            require(value in GROUP_MIN..GROUP_MAX) {
                "Group address out of range 0xC000-0xFFFF: 0x${value.toString(16).uppercase()}"
            }
        }

        public val isFixedGroup: Boolean get() = value in FIXED_GROUP_MIN..FIXED_GROUP_MAX

        public companion object {
            public val GROUP_MIN: UShort = 0xC000u
            public val GROUP_MAX: UShort = 0xFFFFu
            public val FIXED_GROUP_MIN: UShort = 0xFF00u
            public val FIXED_GROUP_MAX: UShort = 0xFFFFu
            /** All-proxies fixed group address. */
            public val ALL_PROXIES: GroupAddress = GroupAddress(0xFFFCu)
            /** All-friends fixed group address. */
            public val ALL_FRIENDS: GroupAddress = GroupAddress(0xFFFDu)
            /** All-relays fixed group address. */
            public val ALL_RELAYS: GroupAddress = GroupAddress(0xFFFEu)
            /** All-nodes fixed group address. */
            public val ALL_NODES: GroupAddress = GroupAddress(0xFFFFu)
        }
    }

    /**
     * A virtual address derived from a 128-bit UUID label.
     *
     * The 16-bit address value is computed by hashing the label UUID with
     * the salt "vtad" using AES-CMAC and taking the lower 16 bits of the
     * result.
     */
    public data class VirtualAddress(
        public val labelUuid: Uuid,
    ) : MeshAddress {
        /**
         * 16-bit hash of the label UUID.
         * Computed via AES-CMAC(salt="vtad", data=labelUuid.bytes).
         */
        override val value: UShort
            get() = deriveVirtualAddressHash(labelUuid)

        override fun equals(other: Any?): Boolean =
            other is VirtualAddress && other.labelUuid == labelUuid

        override fun hashCode(): Int = labelUuid.hashCode()

        public companion object {
            /**
             * Derive the 16-bit hash value from a UUID label.
             *
             * The algorithm is defined in the BLE Mesh Profile specification:
             * `AES-CMAC(vtadSalt, labelBytes)`, taking the lower 16 bits.
             */
            @OptIn(ExperimentalUuidApi::class)
            public fun deriveVirtualAddressHash(uuid: Uuid): UShort {
                // Placeholder implementation using UUID bytes directly.
                // The actual implementation requires AES-CMAC which is in the crypto package.
                // This will be replaced by crypto/KeyDerivation.kt once implemented.
                val bytes = uuid.toByteArray()
                var hash = 0
                for (i in bytes.indices step 2) {
                    val word = ((bytes[i].toInt() and 0xFF) shl 8) or
                        (if (i + 1 < bytes.size) (bytes[i + 1].toInt() and 0xFF) else 0)
                    hash = hash xor word
                }
                return hash.toUShort()
            }
        }
    }

    public companion object {
        /** The unassigned address (0x0000) -- indicates no address assigned. */
        public val UNASSIGNED: UShort = 0x0000u

        /** Create an address from a raw 16-bit value. */
        public fun fromValue(value: UShort): MeshAddress = when (value.toInt()) {
            0x0000 -> throw IllegalArgumentException("Unassigned address (0x0000)")
            in UNICAST_RANGE -> UnicastAddress(value)
            in GROUP_RANGE -> GroupAddress(value)
            else -> throw IllegalArgumentException(
                "Address 0x${value.toString(16).uppercase()} in reserved range (0x8000-0xBFFF)",
            )
        }

        private val UNICAST_RANGE = 0x0001..0x7FFF
        private val GROUP_RANGE = 0xC000..0xFFFF
    }
}
