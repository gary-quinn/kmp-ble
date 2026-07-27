package com.atruedev.kmpble.mesh.testing

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.provisioning.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fake [MeshProvisioner] for testing provisioning without hardware.
 *
 * Allows tests to:
 * - Emit unprovisioned devices via a SharedFlow
 * - Pre-configure provisioning results
 * - Inject provisioning errors
 * - Inspect provisioning requests
 */
public class FakeProvisioner : MeshProvisioner {
    private val _scanEvents = MutableSharedFlow<UnprovisionedDevice>(
        replay = 0, extraBufferCapacity = 16)
    private val mutex = Mutex()
    private var provisionResult: ProvisioningResult? = null
    private var provisionError: MeshError? = null
    private val provisionRequests = mutableListOf<ProvisionRequest>()

    override val scanEvents: Flow<UnprovisionedDevice> = _scanEvents.asSharedFlow()

    override suspend fun provision(
        device: UnprovisionedDevice,
        networkKey: NetworkKey,
        unicastAddress: MeshAddress.UnicastAddress,
        oobAuth: OobAuthentication,
    ): ProvisioningResult {
        mutex.withLock {
            provisionRequests.add(ProvisionRequest(device, networkKey, unicastAddress, oobAuth))
        }
        provisionError?.let { throw MeshException(it) }
        return provisionResult ?: throw MeshException(
            ProvisioningFailed(ProvisioningPhase.DATA_DISTRIBUTION, "No result configured"))
    }

    override fun close() {}

    // --- Test helpers ---

    /** Emit a discovered unprovisioned device. */
    public suspend fun simulateDevice(device: UnprovisionedDevice) {
        _scanEvents.emit(device)
    }

    /** Configure the next provisioning call to succeed. */
    public suspend fun configureSuccess(result: ProvisioningResult) {
        mutex.withLock { provisionResult = result; provisionError = null }
    }

    /** Configure the next provisioning call to fail. */
    public suspend fun configureError(error: MeshError) {
        mutex.withLock { provisionError = error; provisionResult = null }
    }

    /** Get all provisioning requests made so far. */
    public fun getProvisionRequests(): List<ProvisionRequest> = provisionRequests.toList()

    public data class ProvisionRequest(
        val device: UnprovisionedDevice,
        val networkKey: NetworkKey,
        val unicastAddress: MeshAddress.UnicastAddress,
        val oobAuth: OobAuthentication,
    )
}
