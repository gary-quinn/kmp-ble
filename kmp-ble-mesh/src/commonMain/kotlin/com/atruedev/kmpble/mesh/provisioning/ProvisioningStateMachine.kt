package com.atruedev.kmpble.mesh.provisioning

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.crypto.AesCcm
import com.atruedev.kmpble.mesh.crypto.AesCmac
import com.atruedev.kmpble.mesh.crypto.CryptoEngine
import com.atruedev.kmpble.mesh.crypto.EcdhKeyPair
import com.atruedev.kmpble.mesh.crypto.KeyDerivation
import kotlinx.coroutines.flow.first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Implements the BLE Mesh provisioning protocol state machine.
 *
 * Runs the 5-phase provisioning protocol:
 * 1. Invitation -- send attention timer
 * 2. Capabilities Exchange -- device reports its features
 * 3. Public Key Exchange -- ECDH P-256 key agreement
 * 4. Authentication -- OOB confirmation and random exchange
 * 5. Data Distribution -- encrypted provisioning data delivery
 */
internal class ProvisioningStateMachine(
    private val bearer: ProvisioningBearer,
    private val timeout: Duration = 60.seconds,
) {
    private var ecdhKeyPair: EcdhKeyPair? = null
    private var devicePublicKey: ByteArray? = null
    private var sharedSecret: ByteArray? = null
    private var authValue: ByteArray? = null

    /**
     * Run the full provisioning protocol.
     *
     * @param networkKey The NetKey to share with the device.
     * @param unicastAddress The unicast address to assign.
     * @param oobAuth The OOB authentication method.
     * @param attentionDuration Duration in seconds for device attention (blink/beep).
     * @return [ProvisioningResult] with the new MeshNode and provisioning data.
     */
    suspend fun run(
        networkKey: NetworkKey,
        unicastAddress: MeshAddress.UnicastAddress,
        oobAuth: OobAuthentication,
        attentionDuration: UByte = 5u,
    ): ProvisioningResult {
        // Phase 1: Send invitation
        bearer.send(byteArrayOf(0x00, attentionDuration.toByte()))

        // Phase 2: Receive capabilities
        val capabilities = receiveCapabilities()

        // Phase 3: Public key exchange
        performPublicKeyExchange(capabilities)

        // Phase 4: Authentication
        performAuthentication(oobAuth)

        // Phase 5: Data distribution
        return distributeProvisioningData(networkKey, unicastAddress)
    }

    private suspend fun receiveCapabilities(): ProvisioningCapabilities {
        val pdu = bearer.incomingPdus.first()
        require(pdu.isNotEmpty() && pdu[0] == 0x01.toByte()) {
            "Expected Capabilities PDU (type 0x01), got ${pdu.firstOrNull()?.let { "0x${it.toInt().and(0xFF).toString(16)}" }}"
        }
        return ProvisioningCapabilities.fromBytes(pdu.copyOfRange(1, pdu.size))
    }

    private suspend fun performPublicKeyExchange(
        capabilities: ProvisioningCapabilities,
    ) {
        require(capabilities.supportsFipsP256) {
            "Device does not support FIPS P-256 ECDH"
        }

        ecdhKeyPair = CryptoEngine.ecdhP256GenerateKeyPair()
        bearer.send(byteArrayOf(0x03.toByte()) + ecdhKeyPair!!.publicKey)

        val response = bearer.incomingPdus.first()
        require(response.isNotEmpty() && response[0] == 0x03.toByte()) {
            "Expected Public Key PDU (type 0x03)"
        }
        devicePublicKey = response.copyOfRange(1, response.size)

        sharedSecret = CryptoEngine.ecdhP256SharedSecret(
            ecdhKeyPair!!.privateKey, devicePublicKey!!,
        )
    }

    private suspend fun performAuthentication(oobAuth: OobAuthentication) {
        val confirmationSalt = KeyDerivation.s1("prck")
        val sessionSalt = KeyDerivation.s1("prsk")

        val confirmationKey = KeyDerivation.k1(sharedSecret!!, confirmationSalt,
            ByteArray(0))
        val randomProvisioner = CryptoEngine.secureRandomBytes(16)

        // Compute and send our confirmation
        val confirmationInputs = ecdhKeyPair!!.publicKey + devicePublicKey!!
        val confirmationValue = AesCmac.compute(confirmationKey,
            confirmationInputs + randomProvisioner +
                oobAuth.getRawAuthValueForCrypto())

        bearer.send(byteArrayOf(0x05.toByte()) + confirmationValue)

        // Receive device confirmation + random
        val devConfirm = bearer.incomingPdus.first()
        val devRandom = bearer.incomingPdus.first()

        // Send our random
        bearer.send(byteArrayOf(0x06.toByte()) + randomProvisioner)

        // Verify device's random by recomputing their confirmation
        val deviceRandomBytes = devRandom.copyOfRange(1, devRandom.size)
        val expectedDeviceConfirm = AesCmac.compute(confirmationKey,
            devicePublicKey!! + ecdhKeyPair!!.publicKey +
                deviceRandomBytes +
                oobAuth.getRawAuthValueForCrypto())

        val receivedDeviceConfirm = devConfirm.copyOfRange(1, devConfirm.size)
        require(expectedDeviceConfirm.contentEquals(receivedDeviceConfirm)) {
            "Device confirmation mismatch -- OOB authentication failed"
        }

        authValue = oobAuth.getRawAuthValueForCrypto()
    }

    private suspend fun distributeProvisioningData(
        networkKey: NetworkKey,
        unicastAddress: MeshAddress.UnicastAddress,
    ): ProvisioningResult {
        val deviceKeyBytes = CryptoEngine.secureRandomBytes(16)
        val deviceKey = DeviceKey(deviceKeyBytes)

        val sessionKey = KeyDerivation.k1(
            sharedSecret!!, KeyDerivation.s1("prsk"), ByteArray(0))
        val sessionNonce = KeyDerivation.s1("prsn").copyOf(13)

        val dataBytes = buildProvisioningDataBytes(networkKey, unicastAddress,
            deviceKey)
        val result = AesCcm.encrypt(sessionKey, sessionNonce, dataBytes,
            ByteArray(0), 8)

        bearer.send(byteArrayOf(0x07.toByte()) + result.ciphertext + result.mic)

        val completePdu = bearer.incomingPdus.first()
        require(completePdu.isNotEmpty() && completePdu[0] == 0x08.toByte()) {
            "Expected Provisioning Complete PDU (type 0x08)"
        }

        val provisioningData = ProvisioningData(
            networkKey = networkKey, keyIndex = networkKey.index,
            ivIndex = IvIndex.INITIAL, unicastAddress = unicastAddress,
            deviceKey = deviceKey,
        )

        val node = MeshNode(
            unicastAddress = unicastAddress, deviceKey = deviceKey,
            elements = listOf(MeshElement(
                index = 0, unicastAddress = unicastAddress,
                location = ElementLocation.MAIN,
            )), networkKeys = listOf(networkKey),
        )

        return ProvisioningResult(node, provisioningData)
    }

    private fun buildProvisioningDataBytes(
        networkKey: NetworkKey,
        unicastAddress: MeshAddress.UnicastAddress,
        deviceKey: DeviceKey,
    ): ByteArray = networkKey.key +
        byteArrayOf(
            (networkKey.index.value.toInt() and 0xFF).toByte(),
            ((networkKey.index.value.toInt() shr 8) and 0xFF).toByte(),
        ) +
        byteArrayOf(0x00, 0x00, 0x00, 0x00) +
        byteArrayOf(
            (unicastAddress.value.toInt() and 0xFF).toByte(),
            ((unicastAddress.value.toInt() shr 8) and 0xFF).toByte(),
        ) +
        deviceKey.key
}

/** Extension to get raw bytes for crypto from OobAuthentication. */
private fun OobAuthentication.getRawAuthValueForCrypto(): ByteArray = when (this) {
    is OobAuthentication.None -> ByteArray(16)
    is OobAuthentication.StaticOob -> key
    is OobAuthentication.OutputOob -> ByteArray(size) { 0 }
    is OobAuthentication.InputOob -> ByteArray(size) { 0 }
}
