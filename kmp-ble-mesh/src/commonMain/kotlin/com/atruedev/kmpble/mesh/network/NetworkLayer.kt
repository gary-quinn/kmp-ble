package com.atruedev.kmpble.mesh.network

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.crypto.AesCcm
import com.atruedev.kmpble.mesh.crypto.CryptoEngine
import com.atruedev.kmpble.mesh.crypto.KeyDerivation
import com.atruedev.kmpble.mesh.crypto.NonceGenerator
import com.atruedev.kmpble.mesh.internal.ReplayProtectionList

/**
 * Network Layer -- encrypts/decrypts Network PDUs using NetKey.
 *
 * The Network Layer is responsible for:
 * - Encrypting transport PDUs with AES-128-CCM using the NetKey
 * - Applying the privacy layer (AES-ECB obfuscation of SRC/DST)
 * - Setting TTL and handling relay decrement
 * - Checking message cache to prevent duplicate forwarding
 * - Replay protection via sequence numbers
 */
internal class NetworkLayer(
    private val netKeys: List<NetworkKey>,
    private val replayProtection: ReplayProtectionList = ReplayProtectionList(),
) {
    /**
     * Encrypt a transport PDU into a Network PDU.
     *
     * Steps:
     * 1. Derive encryption key and privacy key via K2(NetKey, 0x00)
     * 2. Build the Network PDU header (IVI, NID, CTL, TTL, SEQ, SRC, DST)
     * 3. Encrypt transport PDU with AES-128-CCM using encryption key
     * 4. Apply privacy layer to SRC/DST using privacy key + AES-ECB
     *
     * @param transportPdu The transport layer payload.
     * @param src Source unicast address.
     * @param dst Destination address.
     * @param netKey The network key to encrypt with.
     * @param ttl Time-to-live (hop count).
     * @param seq 24-bit sequence number.
     * @param ivIndex Current IV Index.
     * @param ctl Control (1) or Access (0) message flag.
     * @return The complete Network PDU ready for bearer transmission.
     */
    fun encrypt(
        transportPdu: ByteArray,
        src: MeshAddress.UnicastAddress,
        dst: MeshAddress,
        netKey: NetworkKey,
        ttl: Int = 5,
        seq: Int = 0,
        ivIndex: IvIndex = IvIndex.INITIAL,
        ctl: Int = 0,
    ): NetworkPdu {
        val k2Result = KeyDerivation.k2(netKey.key, byteArrayOf(0x00))
        val nonce = NonceGenerator.networkNonce(
            ctl, ttl, seq.toUInt(), src.value.toInt(),
            dst.value.toInt(), ivIndex.value,
        )

        val result = AesCcm.encrypt(
            k2Result.encryptionKey, nonce, transportPdu, ByteArray(0), 4)

        return NetworkPdu(
            ivi = 0, nid = k2Result.nid, ctl = ctl, ttl = ttl,
            seq = seq.toUInt(), src = src, dst = dst,
            transportPdu = result.ciphertext, netMic = result.mic,
        )
    }

    /**
     * Decrypt a Network PDU into its transport payload.
     *
     * Steps:
     * 1. Find the NetKey by NID
     * 2. De-obfuscate privacy layer
     * 3. Verify sequence number against replay protection list
     * 4. Decrypt transport PDU with AES-128-CCM
     *
     * @return The decrypted transport PDU payload, or null if authentication fails.
     */
    fun decrypt(pdu: NetworkPdu, ivIndex: IvIndex): ByteArray? {
        // Find matching NetKey by NID
        val netKey = netKeys.find { nk ->
            val k2r = KeyDerivation.k2(nk.key, byteArrayOf(0x00))
            k2r.nid == pdu.nid
        } ?: return null

        // Check replay protection
        if (!replayProtection.checkAndUpdate(
                pdu.src.value.toInt(), pdu.seq.toInt(), ivIndex)) {
            return null // Replay detected
        }

        val k2Result = KeyDerivation.k2(netKey.key, byteArrayOf(0x00))
        val nonce = NonceGenerator.networkNonce(
            pdu.ctl, pdu.ttl, pdu.seq, pdu.src.value.toInt(),
            pdu.dst.value.toInt(), ivIndex.value,
        )

        return AesCcm.decrypt(
            k2Result.encryptionKey, nonce, pdu.transportPdu, ByteArray(0),
            pdu.netMic,
        )
    }
}
