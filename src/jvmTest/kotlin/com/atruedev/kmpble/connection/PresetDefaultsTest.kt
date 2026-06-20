package com.atruedev.kmpble.connection

import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.ScanPhy
import com.atruedev.kmpble.scanner.ScannerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PresetDefaultsTest {
    @Test
    fun `ConnectionOptions Balanced preset uses 2M PHY and LE transport`() {
        val opts = ConnectionOptions.Balanced
        assertEquals(TransportType.LE, opts.transportType)
        assertEquals(PhyMask.LE_2M, opts.phyMask)
        assertEquals(30.seconds, opts.timeout)
        assertFalse(opts.autoConnect)
    }

    @Test
    fun `ConnectionOptions LongRange preset uses Coded PHY with 60s timeout`() {
        val opts = ConnectionOptions.LongRange
        assertEquals(TransportType.LE, opts.transportType)
        assertEquals(PhyMask.LE_CODED, opts.phyMask)
        assertEquals(60.seconds, opts.timeout)
        assertFalse(opts.autoConnect)
    }

    @Test
    fun `ConnectionOptions LowLatency preset uses 2M PHY with 10s timeout and MTU 512`() {
        val opts = ConnectionOptions.LowLatency
        assertEquals(TransportType.LE, opts.transportType)
        assertEquals(PhyMask.LE_2M, opts.phyMask)
        assertEquals(10.seconds, opts.timeout)
        assertEquals(512, opts.mtuRequest)
        assertFalse(opts.autoConnect)
    }

    @Test
    fun `ScannerConfig default sets all PHYs and disables legacy only`() {
        // ScannerConfig has internal constructor, test via DSL pattern
        var captured: ScannerConfig? = null
        // Simulate what platform factories do
        val config =
            object {
                val scannerConfig = ScannerConfig().also { captured = it }
            }
        ScannerConfig.default(config.scannerConfig)

        val cfg = captured!!
        assertEquals(ScanPhy.All, cfg.phy)
        assertFalse(cfg.legacyOnly)
        assertEquals(30.seconds, cfg.timeout)
        assertTrue(cfg.emission is EmissionPolicy.FirstThenChanges)
    }

    @Test
    fun `ConnectionOptions presets produce valid objects`() {
        // Verify init block doesn't throw for any preset
        ConnectionOptions.Balanced // should not throw
        ConnectionOptions.LongRange // should not throw
        ConnectionOptions.LowLatency // should not throw
    }
}
