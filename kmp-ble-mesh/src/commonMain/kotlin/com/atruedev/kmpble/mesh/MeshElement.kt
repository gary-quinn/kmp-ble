package com.atruedev.kmpble.mesh

/**
 * Element location descriptor as defined by the Bluetooth SIG.
 *
 * Each element in a mesh node has a location that describes its physical
 * or logical position. This is reported in the Composition Data.
 */
public enum class ElementLocation(public val code: UShort) {
    /** Primary element (always the first element of a node). */
    MAIN(0x0000u),

    // Physical locations
    TOP(0x0001u),
    BOTTOM(0x0002u),
    LEFT(0x0003u),
    RIGHT(0x0004u),
    FRONT(0x0005u),
    BACK(0x0006u),
    CENTER(0x0007u),
    INTERNAL(0x0008u),
    EXTERNAL(0x0009u),

    // Lighting-specific
    CEILING(0x000Au),
    WALL(0x000Bu),
    FLOOR(0x000Cu),

    // Appliance-specific
    DOOR(0x000Du),
    DRAWER(0x000Eu),

    // Unknown/other
    UNKNOWN(0xFFFFu),
    ;

    public companion object {
        /** Look up a location by its SIG-assigned code. */
        public fun fromCode(code: UShort): ElementLocation =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
