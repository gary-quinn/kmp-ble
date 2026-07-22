package com.atruedev.kmpble.mesh.provisioning

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.mesh.*
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Discovers and provisions unprovisioned BLE Mesh devices.
 *
 * [MeshProvisioner] handles the complete provisioning lifecycle:
 * 1. Scan for unprovisioned devices via PB-ADV or PB-GATT
 * 2. Run the provisioning protocol (ECDH + OOB authentication + data distribution)
 * 3. Return a provisioned [MeshNode] ready for configuration
 *
 * ## Usage
 *
 * ```kotlin
 * val provisioner = MeshProvisioner()
 * provisioner.scanEvents.collect { device ->
 *     val result = provisioner.provision(
 *         device = device,
 *         networkKey = myNetKey,
 *         unicastAddress = allocateAddress(),
 *         oobAuth = OobAuthentication.None,
 *     )
 *     network.addNode(result.node)
 * }
 * provisioner.close()
 * ```
 */
public interface MeshProvisioner : AutoCloseable {
    /** Flow of discovered unprovisioned devices. Emits when a new device beacon is seen. */
    public val scanEvents: Flow<UnprovisionedDevice>

    /**
     * Provision a discovered unprovisioned device.
     *
     * Runs the full provisioning protocol and returns a [ProvisioningResult]
     * containing the new [MeshNode] and provisioning data.
     *
     * The node is NOT automatically added to any [MeshNetwork]. The caller
     * must call `network.addNode(result.node)` to add it.
     *
     * @param device The unprovisioned device to provision.
     * @param networkKey The NetKey to share with the device.
     * @param unicastAddress The unicast address to assign to the primary element.
     * @param oobAuth The OOB authentication method (default: None).
     * @return The provisioning result with the new node.
     * @throws ProvisioningException if provisioning fails at any phase.
     */
    public suspend fun provision(
        device: UnprovisionedDevice,
        networkKey: NetworkKey,
        unicastAddress: MeshAddress.UnicastAddress,
        oobAuth: OobAuthentication = OobAuthentication.None,
    ): ProvisioningResult

    override fun close()
}

/**
 * An unprovisioned BLE Mesh device discovered during scanning.
 */
public data class UnprovisionedDevice(
    /** Device UUID (128-bit, from unprovisioned device beacon). */
    val uuid: Uuid,

    /** Platform BLE identifier for establishing a connection. */
    val bleIdentifier: Identifier,

    /** Signal strength at discovery time (dBm). */
    val rssi: Int,

    /** Which provisioning bearer the device is advertising on. */
    val bearerType: ProvisioningBearerType,

    /** OOB capabilities reported in the beacon. */
    val oobInfo: OobInfo = OobInfo(),
)

/** Create a platform-specific MeshProvisioner. */
public expect fun MeshProvisioner(): MeshProvisioner
