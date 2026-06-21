package com.atruedev.kmpble.connection

import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.bonding.PairingResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionOptionsValidationTest {
    @Test
    fun `default options produce no warnings`() {
        val options = ConnectionOptions()
        val warnings = options.validate()
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `mtu below minimum warns`() {
        val options = ConnectionOptions(mtuRequest = 20)
        val warnings = options.validate()
        assertEquals(1, warnings.size)
        assertTrue(warnings.single() is ValidationWarning.MtuTooLow)
        assertEquals(20, (warnings.single() as ValidationWarning.MtuTooLow).requested)
    }

    @Test
    fun `mtu at exactly 23 does not warn`() {
        val options = ConnectionOptions(mtuRequest = 23)
        val warnings = options.validate()
        assertTrue(warnings.none { it is ValidationWarning.MtuTooLow })
    }

    @Test
    fun `mtu above maximum warns`() {
        val options = ConnectionOptions(mtuRequest = 600)
        val warnings = options.validate()
        assertEquals(1, warnings.size)
        assertTrue(warnings.single() is ValidationWarning.MtuTooHigh)
        assertEquals(600, (warnings.single() as ValidationWarning.MtuTooHigh).requested)
    }

    @Test
    fun `mtu at exactly 517 does not warn`() {
        val options = ConnectionOptions(mtuRequest = 517)
        val warnings = options.validate()
        assertTrue(warnings.none { it is ValidationWarning.MtuTooHigh })
    }

    @Test
    fun `null mtu produces no mtu warnings`() {
        val options = ConnectionOptions(mtuRequest = null)
        val warnings = options.validate()
        assertTrue(warnings.none { it is ValidationWarning.MtuTooLow || it is ValidationWarning.MtuTooHigh })
    }

    @Test
    fun `autoConnect warns about battery`() {
        val options = ConnectionOptions(autoConnect = true)
        val warnings = options.validate()
        assertEquals(1, warnings.size)
        assertTrue(warnings.single() is ValidationWarning.AutoConnectBattery)
    }

    @Test
    fun `autoConnect false produces no battery warning`() {
        val options = ConnectionOptions(autoConnect = false)
        val warnings = options.validate()
        assertTrue(warnings.none { it is ValidationWarning.AutoConnectBattery })
    }

    @Test
    fun `BrEdr transport with non-default phy warns`() {
        val options = ConnectionOptions(transportType = TransportType.BrEdr, phyMask = PhyMask.LE_2M)
        val warnings = options.validate()
        assertEquals(1, warnings.size)
        val warning = warnings.single() as ValidationWarning.PhyBrEdrMismatch
        assertEquals(TransportType.BrEdr, warning.transportType)
        assertEquals(PhyMask.LE_2M, warning.phyMask)
    }

    @Test
    fun `BrEdr transport with LE_1M phy does not warn`() {
        val options = ConnectionOptions(transportType = TransportType.BrEdr, phyMask = PhyMask.LE_1M)
        val warnings = options.validate()
        assertTrue(warnings.none { it is ValidationWarning.PhyBrEdrMismatch })
    }

    @Test
    fun `Coded PHY with high MTU warns`() {
        val options = ConnectionOptions(phyMask = PhyMask.LE_CODED, mtuRequest = 200)
        val warnings = options.validate()
        assertTrue(warnings.any { it is ValidationWarning.CodedPhyHighMtu })
        val mtuWarning = warnings.filterIsInstance<ValidationWarning.CodedPhyHighMtu>().single()
        assertEquals(200, mtuWarning.mtuRequest)
    }

    @Test
    fun `Coded PHY with low MTU does not warn`() {
        val options = ConnectionOptions(phyMask = PhyMask.LE_CODED, mtuRequest = 100)
        val warnings = options.validate()
        assertTrue(warnings.none { it is ValidationWarning.CodedPhyHighMtu })
    }

    @Test
    fun `Coded PHY with null MTU does not warn about MTU`() {
        val options = ConnectionOptions(phyMask = PhyMask.LE_CODED, mtuRequest = null)
        val warnings = options.validate()
        assertTrue(warnings.none { it is ValidationWarning.CodedPhyHighMtu })
    }

    @Test
    fun `Required bonding without pairing handler warns`() {
        val options = ConnectionOptions(bondingPreference = BondingPreference.Required, pairingHandler = null)
        val warnings = options.validate()
        assertTrue(warnings.any { it is ValidationWarning.RequiredBondingNoHandler })
    }

    @Test
    fun `Required bonding with pairing handler does not warn`() {
        val handler =
            PairingHandler { _ ->
                PairingResponse.Confirm(true)
            }
        val options = ConnectionOptions(bondingPreference = BondingPreference.Required, pairingHandler = handler)
        val warnings = options.validate()
        assertTrue(warnings.none { it is ValidationWarning.RequiredBondingNoHandler })
    }

    @Test
    fun `multiple warnings aggregate`() {
        val options = ConnectionOptions(autoConnect = true, mtuRequest = 10)
        val warnings = options.validate()
        assertEquals(2, warnings.size)
        assertTrue(warnings.any { it is ValidationWarning.AutoConnectBattery })
        assertTrue(warnings.any { it is ValidationWarning.MtuTooLow })
    }

    @Test
    fun `balanced preset has no warnings`() {
        val warnings = ConnectionOptions.Balanced.validate()
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `longRange preset has no warnings`() {
        val warnings = ConnectionOptions.LongRange.validate()
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `lowLatency preset has no warnings`() {
        val warnings = ConnectionOptions.LowLatency.validate()
        assertTrue(warnings.isEmpty())
    }
}
