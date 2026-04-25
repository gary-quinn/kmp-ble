package com.atruedev.kmpble.dfu.firmware

/**
 * Parsed firmware ready for DFU transfer.
 *
 * Each DFU protocol has its own firmware layout. Use the appropriate subtype:
 * - [Nordic] for Nordic Semiconductor Secure DFU v2 (.zip packages)
 * - [McuBoot] for MCUboot/Zephyr SMP-based DFU (.bin images)
 * - [EspOta] for Espressif ESP-IDF OTA updates (.bin images)
 *
 * @property totalBytes combined firmware payload size in bytes
 * @see com.atruedev.kmpble.dfu.DfuController.performDfu
 */
public sealed interface FirmwarePackage {
    public val totalBytes: Long

    /**
     * Nordic Semiconductor Secure DFU v2 firmware package.
     *
     * Contains the init packet (metadata + signature) and the raw firmware binary,
     * parsed from a Nordic DFU .zip distribution package.
     *
     * @property initPacket DFU init packet (.dat) - firmware metadata and signature
     * @property firmware raw firmware binary (.bin) to flash onto the peripheral
     */
    public class Nordic(
        public val initPacket: ByteArray,
        public val firmware: ByteArray,
    ) : FirmwarePackage {
        override val totalBytes: Long get() = (initPacket.size + firmware.size).toLong()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Nordic) return false
            return initPacket.contentEquals(other.initPacket) && firmware.contentEquals(other.firmware)
        }

        override fun hashCode(): Int = 31 * initPacket.contentHashCode() + firmware.contentHashCode()

        public companion object {
            /**
             * Parse a Nordic DFU .zip package into a [Nordic] firmware package.
             *
             * @param zipData raw bytes of the .zip file
             * @return parsed firmware package
             * @throws com.atruedev.kmpble.dfu.DfuError.FirmwareParseError if the archive
             *   is malformed or missing required entries
             */
            public fun fromZipBytes(zipData: ByteArray): Nordic =
                NordicDfuZipParser.parse(zipData)
        }
    }

    /**
     * MCUboot firmware image for SMP-based DFU.
     *
     * @property image raw MCUboot image bytes (header + payload + TLV trailer)
     * @property imageIndex target image slot index (0 for primary, 1+ for multi-image)
     */
    public class McuBoot(
        public val image: ByteArray,
        public val imageIndex: Int = 0,
    ) : FirmwarePackage {
        override val totalBytes: Long get() = image.size.toLong()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is McuBoot) return false
            return imageIndex == other.imageIndex && image.contentEquals(other.image)
        }

        override fun hashCode(): Int = 31 * image.contentHashCode() + imageIndex

        public companion object {
            /**
             * Parse and validate a MCUboot firmware image.
             *
             * @param binData raw bytes of the MCUboot image file
             * @param imageIndex target image slot (default 0)
             * @return validated firmware package
             * @throws com.atruedev.kmpble.dfu.DfuError.FirmwareParseError if the image header is invalid
             */
            public fun fromBinBytes(binData: ByteArray, imageIndex: Int = 0): McuBoot =
                McuBootImageParser.parse(binData, imageIndex)
        }
    }

    /**
     * Espressif ESP-IDF OTA firmware image.
     *
     * @property firmware raw ESP-IDF application binary
     */
    public class EspOta(
        public val firmware: ByteArray,
    ) : FirmwarePackage {
        override val totalBytes: Long get() = firmware.size.toLong()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EspOta) return false
            return firmware.contentEquals(other.firmware)
        }

        override fun hashCode(): Int = firmware.contentHashCode()

        public companion object {
            /**
             * Parse and validate an ESP-IDF OTA firmware image.
             *
             * @param binData raw bytes of the ESP-IDF application binary
             * @return validated firmware package
             * @throws com.atruedev.kmpble.dfu.DfuError.FirmwareParseError if the image header is invalid
             */
            public fun fromBinBytes(binData: ByteArray): EspOta =
                EspImageParser.parse(binData)
        }
    }
}

@Deprecated(
    message = "Use FirmwarePackage.Nordic constructor directly",
    replaceWith = ReplaceWith("FirmwarePackage.Nordic(initPacket, firmware)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("FunctionName")
public fun FirmwarePackage(initPacket: ByteArray, firmware: ByteArray): FirmwarePackage.Nordic =
    FirmwarePackage.Nordic(initPacket, firmware)
