package com.atruedev.kmpble.dfu.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class DfuOpcodeTest {

    @Test
    fun opcodeValues() {
        assertEquals(0x01, DfuOpcode.CREATE)
        assertEquals(0x02, DfuOpcode.SET_PRN)
        assertEquals(0x03, DfuOpcode.CALCULATE_CHECKSUM)
        assertEquals(0x04, DfuOpcode.EXECUTE)
        assertEquals(0x06, DfuOpcode.SELECT)
        assertEquals(0x60, DfuOpcode.RESPONSE)
    }

    @Test
    fun objectTypeValues() {
        assertEquals(0x01, DfuObjectType.COMMAND)
        assertEquals(0x02, DfuObjectType.DATA)
    }

    @Test
    fun resultCodeDescription() {
        assertEquals("Success", DfuResultCode.describe(DfuResultCode.SUCCESS))
        assertEquals("Operation failed", DfuResultCode.describe(DfuResultCode.OPERATION_FAILED))
        assertEquals("Unknown result code: 0xff", DfuResultCode.describe(0xFF))
    }
}
