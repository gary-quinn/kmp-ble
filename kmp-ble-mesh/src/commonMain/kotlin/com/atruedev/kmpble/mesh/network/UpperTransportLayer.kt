package com.atruedev.kmpble.mesh.network

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.crypto.AesCcm
import com.atruedev.kmpble.mesh.crypto.NonceGenerator

/**
 * Upper Transport Layer -- AppKey/DeviceKey encryption.
 *
 * Encrypts Access Layer PDUs with either an ApplicationKey (for model
 * messages) or a DeviceKey (for configuration/health messages).
 *
 * Also handles transport control messages (SAR ACK, heartbeat, etc.).
 */
internal class UpperTransportLayer {
    /**
     * Encrypt an Access PDU using an ApplicationKey.
     *
     * @param accessPdu Raw access layer payload (opcode + parameters).
     * @param appKey The application key for encryption.
     * @param src Source unicast address.
     * @param dst Destination address.
     * @param seq 24-bit sequence number.
     * @param ivIndex Current IV Index.
     * @param szmic MIC size (0 = 32-bit, 1 = 64-bit).
     * @return Encrypted upper transport PDU.
     */
    fun encryptWithAppKey(
        accessPdu: ByteArray,
        appKey: ApplicationKey,
        src: MeshAddress.UnicastAddress,
        dst: MeshAddress,
        seq: Int,
        ivIndex: IvIndex,
        szmic: Int = 0,
    ): ByteArray {
        val nonce = NonceGenerator.applicationNonce(
            seq.toUInt(), src.value.toInt(), dst.value.toInt(),
            ivIndex.value, szmic,
        )
        val micSize = if (szmic != 0) 8 else 4
        val result = AesCcm.encrypt(appKey.key, nonce, accessPdu,
            ByteArray(0), micSize)
        return result.ciphertext + result.mic
    }

    /**
     * Encrypt an Access PDU using a DeviceKey (for configuration messages).
     */
    fun encryptWithDeviceKey(
        accessPdu: ByteArray,
        deviceKey: DeviceKey,
        src: MeshAddress.UnicastAddress,
        dst: MeshAddress,
        seq: Int,
        ivIndex: IvIndex,
        szmic: Int = 0,
    ): ByteArray {
        val nonce = NonceGenerator.deviceNonce(
            seq.toUInt(), src.value.toInt(), dst.value.toInt(),
            ivIndex.value, szmic,
        )
        val micSize = if (szmic != 0) 8 else 4
        val result = AesCcm.encrypt(deviceKey.key, nonce, accessPdu,
            ByteArray(0), micSize)
        return result.ciphertext + result.mic
    }

    /**
     * Decrypt an Upper Transport PDU with an AppKey.
     *
     * @return Decrypted access PDU, or null if authentication fails.
     */
    fun decryptWithAppKey(
        transportPdu: ByteArray,
        appKey: ApplicationKey,
        src: MeshAddress.UnicastAddress,
        dst: MeshAddress,
        seq: Int,
        ivIndex: IvIndex,
        szmic: Int = 0,
    ): ByteArray? {
        val nonce = NonceGenerator.applicationNonce(
            seq.toUInt(), src.value.toInt(), dst.value.toInt(),
            ivIndex.value, szmic,
        )
        val micSize = if (szmic != 0) 8 else 4
        val micOffset = transportPdu.size - micSize
        val ciphertext = transportPdu.copyOfRange(0, micOffset)
        val mic = transportPdu.copyOfRange(micOffset, transportPdu.size)
        return AesCcm.decrypt(appKey.key, nonce, ciphertext, ByteArray(0), mic)
    }
}
