package com.atruedev.kmpble.mesh

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.mesh.provisioning.MeshProvisioner
import com.atruedev.kmpble.mesh.provisioning.OobAuthentication
import com.atruedev.kmpble.mesh.provisioning.ProvisioningBearerType
import com.atruedev.kmpble.mesh.provisioning.UnprovisionedDevice
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class JvmMeshFactoryTest {
    @Test
    fun meshNetworkIsAvailableOnJvm() {
        val network =
            MeshNetwork {
                networkKey(NetworkKey(KeyIndex(0u), ByteArray(16), "net"))
                element(MeshElement(0, MeshAddress.UnicastAddress(0x0001u), ElementLocation.MAIN))
            }
        assertTrue(network.nodes.value.isEmpty())
        network.close()
    }

    @Test
    fun provisionerMatchesMobilePlatforms() =
        runTest {
            val provisioner = MeshProvisioner()
            assertTrue(provisioner.scanEvents.toList().isEmpty())
            assertFailsWith<MeshNotSupported> {
                provisioner.provision(
                    device =
                        UnprovisionedDevice(
                            uuid = Uuid.random(),
                            bleIdentifier = Identifier("AA:BB:CC:DD:EE:FF"),
                            rssi = -40,
                            bearerType = ProvisioningBearerType.PB_GATT,
                        ),
                    networkKey = NetworkKey(KeyIndex(0u), ByteArray(16), "net"),
                    unicastAddress = MeshAddress.UnicastAddress(0x0002u),
                    oobAuth = OobAuthentication.None,
                )
            }
            provisioner.close()
        }
}
