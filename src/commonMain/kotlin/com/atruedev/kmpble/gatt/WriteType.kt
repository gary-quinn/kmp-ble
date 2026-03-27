package com.atruedev.kmpble.gatt

/**
 * GATT characteristic write type, determining delivery guarantee and authentication.
 */
public enum class WriteType {
    /** Write with acknowledgement from the peripheral (ATT Write Request). */
    WithResponse,

    /** Fire-and-forget write with no acknowledgement (ATT Write Command). Higher throughput. */
    WithoutResponse,

    /** Authenticated write using a signed ATT Write Command. Requires bonding. */
    Signed,
}
