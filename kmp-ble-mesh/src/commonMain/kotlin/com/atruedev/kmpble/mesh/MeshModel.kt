package com.atruedev.kmpble.mesh

import kotlin.jvm.JvmInline

/**
 * Identifies a mesh model on an element.
 *
 * Models are identified by either:
 * - **SIG model ID** (16-bit): Standard models defined by Bluetooth SIG (0x0000-0xFFFF).
 * - **Vendor model ID** (32-bit): `(vendorId << 16) | modelId`, for vendor-specific models.
 *
 * The model ID is used to route incoming access messages to the correct model handler.
 */
@JvmInline
public value class MeshModelId(public val value: UInt) {
    /** True if this is a standard Bluetooth SIG-defined model. */
    public val isSigModel: Boolean get() = value <= SIG_MAX

    /** The 16-bit SIG model ID (only valid when [isSigModel] is true). */
    public val sigId: UShort get() = value.toUShort()

    /** The 16-bit vendor company ID (only valid when [isSigModel] is false). */
    public val vendorId: UShort get() = ((value shr 16) and 0xFFFFu).toUShort()

    /** The vendor-assigned model ID (only valid when [isSigModel] is false). */
    public val vendorModelId: UShort get() = (value and 0xFFFFu).toUShort()

    public companion object {
        /** Highest value for a SIG model ID. */
        public const val SIG_MAX: UInt = 0xFFFFu

        /** Create a vendor model ID from company ID and vendor-assigned model ID. */
        public fun vendor(companyId: UShort, modelId: UShort): MeshModelId =
            MeshModelId((companyId.toUInt() shl 16) or modelId.toUInt())

        // --- Standard SIG Model IDs ---

        // Foundation models
        public val ConfigurationServer: MeshModelId = MeshModelId(0x0000u)
        public val ConfigurationClient: MeshModelId = MeshModelId(0x0001u)
        public val HealthServer: MeshModelId = MeshModelId(0x0002u)
        public val HealthClient: MeshModelId = MeshModelId(0x0003u)

        // Generic models
        public val GenericOnOffServer: MeshModelId = MeshModelId(0x1000u)
        public val GenericOnOffClient: MeshModelId = MeshModelId(0x1001u)
        public val GenericLevelServer: MeshModelId = MeshModelId(0x1002u)
        public val GenericLevelClient: MeshModelId = MeshModelId(0x1003u)
        public val GenericDefaultTransitionTimeServer: MeshModelId = MeshModelId(0x1004u)
        public val GenericDefaultTransitionTimeClient: MeshModelId = MeshModelId(0x1005u)
        public val GenericPowerOnOffServer: MeshModelId = MeshModelId(0x1006u)
        public val GenericPowerOnOffClient: MeshModelId = MeshModelId(0x1007u)
        public val GenericPowerLevelServer: MeshModelId = MeshModelId(0x1008u)
        public val GenericPowerLevelClient: MeshModelId = MeshModelId(0x1009u)
        public val GenericBatteryServer: MeshModelId = MeshModelId(0x100Au)
        public val GenericBatteryClient: MeshModelId = MeshModelId(0x100Bu)

        // Sensor models
        public val SensorServer: MeshModelId = MeshModelId(0x1100u)
        public val SensorClient: MeshModelId = MeshModelId(0x1101u)

        // Light models
        public val LightLightnessServer: MeshModelId = MeshModelId(0x1300u)
        public val LightLightnessClient: MeshModelId = MeshModelId(0x1301u)
        public val LightCtlServer: MeshModelId = MeshModelId(0x1302u)
        public val LightCtlClient: MeshModelId = MeshModelId(0x1303u)
    }
}
