package com.atruedev.kmpble.roles

import com.atruedev.kmpble.testing.FakeAdvertiser
import com.atruedev.kmpble.testing.FakeGattServer
import com.atruedev.kmpble.testing.FakeScanner
import com.atruedev.kmpble.testing.FakeScannerBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimultaneousRoleManagerTest {

    @Test
    fun `manager exposes scanner advertiser and optional gatt server`() {
        // Verify we can construct with all three roles via the builder.
        // Use FakeScanner/FakeAdvertiser/FakeGattServer explicitly since
        // the commonMain factory delegates to expect fun Scanner/Advertiser/GattServer
        // which resolve to platform-specific implementations at link time.
        // In jvmTest, Scanner() and Advertiser() are stubs — we verify
        // the manager's structure and composition, not platform behavior.
        val scanner = FakeScanner {}
        val advertiser = FakeAdvertiser()
        val server = FakeGattServer()
        val roles = SimultaneousRoleManager(
            scanner = scanner,
            advertiser = advertiser,
            gattServer = server,
        )

        assertEquals(scanner, roles.scanner)
        assertEquals(advertiser, roles.advertiser)
        assertEquals(server, roles.gattServer)
    }

    @Test
    fun `gattServer is null when not provided`() {
        val scanner = FakeScanner {}
        val advertiser = FakeAdvertiser()
        val roles = SimultaneousRoleManager(
            scanner = scanner,
            advertiser = advertiser,
        )

        assertNull(roles.gattServer)
    }

    @Test
    fun `close cleans up all roles`() {
        val scanner = FakeScanner {}
        val advertiser = FakeAdvertiser()
        val server = FakeGattServer()
        val roles = SimultaneousRoleManager(
            scanner = scanner,
            advertiser = advertiser,
            gattServer = server,
        )

        roles.close()

        // After close, the advertiser should report not advertising
        // and the server should be closed (isOpen = false)
        assertTrue(!advertiser.isAdvertising.value)
        assertTrue(!server.isOpen)
    }

    @Test
    fun `close is safe to call multiple times`() {
        val scanner = FakeScanner {}
        val advertiser = FakeAdvertiser()
        val roles = SimultaneousRoleManager(
            scanner = scanner,
            advertiser = advertiser,
        )

        // Should not throw
        roles.close()
        roles.close()
        roles.close()
    }

    @Test
    fun `close with null gattServer does not throw`() {
        val scanner = FakeScanner {}
        val advertiser = FakeAdvertiser()
        val roles = SimultaneousRoleManager(
            scanner = scanner,
            advertiser = advertiser,
            gattServer = null,
        )

        roles.close()
        assertTrue(!advertiser.isAdvertising.value)
    }

    @Test
    fun `builder sets scan config and server block`() {
        val builder = SimultaneousRolesBuilder()
        builder.scan { timeout = null }
        builder.server { /* empty server */ }

        assertNotNull(builder.scanConfig)
        assertNotNull(builder.serverBlock)
    }
}
