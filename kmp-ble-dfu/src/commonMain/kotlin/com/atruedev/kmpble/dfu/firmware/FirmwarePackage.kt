package com.atruedev.kmpble.dfu.firmware

/**
 * Parsed firmware ready for DFU transfer.
 *
 * Contains the init packet (metadata + signature) and the raw firmware binary.
 * Use [fromZipBytes] to parse a Nordic DFU .zip distribution package.
 *
 * ## Usage
 * ```
 * val firmware = FirmwarePackage.fromZipBytes(zipData)
 * controller.performDfu(firmware).collect { ... }
 * ```
 *
 * @property initPacket DFU init packet (.dat) — contains firmware metadata and signature
 * @property firmware raw firmware binary (.bin) to flash onto the peripheral
 * @property totalBytes combined size of init packet and firmware in bytes
 * @see com.atruedev.kmpble.dfu.DfuController.performDfu
 */
public class FirmwarePackage(
    public val initPacket: ByteArray,
    public val firmware: ByteArray,
) {
    public val totalBytes: Long get() = (initPacket.size + firmware.size).toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirmwarePackage) return false
        return initPacket.contentEquals(other.initPacket) && firmware.contentEquals(other.firmware)
    }

    override fun hashCode(): Int = 31 * initPacket.contentHashCode() + firmware.contentHashCode()

    public companion object {
        /**
         * Parse a Nordic DFU .zip package into a [FirmwarePackage].
         *
         * The .zip must contain a `manifest.json` referencing the `.dat`
         * (init packet) and `.bin` (firmware) entries.
         *
         * @param zipData raw bytes of the .zip file
         * @return parsed [FirmwarePackage] ready for
         *   [performDfu][com.atruedev.kmpble.dfu.DfuController.performDfu]
         * @throws com.atruedev.kmpble.dfu.DfuError.FirmwareParseError if the archive is malformed
         *   or missing required entries
         */
        public fun fromZipBytes(zipData: ByteArray): FirmwarePackage =
            NordicDfuZipParser.parse(zipData)
    }
}
