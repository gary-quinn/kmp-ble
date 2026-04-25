package com.atruedev.kmpble.dfu.firmware

import com.atruedev.kmpble.dfu.DfuError

internal object NordicDfuZipParser {

    fun parse(zipBytes: ByteArray): FirmwarePackage.Nordic {
        val entries = ZipReader.readEntries(zipBytes)
        val entryMap = entries.associateBy { it.name }

        val manifest = entryMap["manifest.json"]
            ?: throw DfuError.FirmwareParseError("manifest.json not found in DFU package")

        val manifestText = manifest.data.decodeToString()
        val fields = parseJsonStringFields(manifestText)
        val datFile = fields["dat_file"]
            ?: throw DfuError.FirmwareParseError("'dat_file' not found in manifest.json")
        val binFile = fields["bin_file"]
            ?: throw DfuError.FirmwareParseError("'bin_file' not found in manifest.json")

        val initPacket = entryMap[datFile]?.data
            ?: throw DfuError.FirmwareParseError("Init packet '$datFile' not found in DFU package")
        val firmware = entryMap[binFile]?.data
            ?: throw DfuError.FirmwareParseError("Firmware binary '$binFile' not found in DFU package")

        return FirmwarePackage.Nordic(initPacket = initPacket, firmware = firmware)
    }

    /**
     * Extract all `"key": "value"` pairs from a JSON string.
     *
     * Handles escaped quotes within values (`\"`). Does not attempt to parse
     * nested objects, arrays, or non-string values - only flat string fields.
     * Sufficient for Nordic DFU manifest.json which contains only string
     * filenames in a shallow structure.
     */
    private fun parseJsonStringFields(json: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        var i = 0

        while (i < json.length) {
            i = json.indexOf('"', i)
            if (i == -1) break

            val key = readJsonString(json, i) ?: break
            i += key.length + 2 // skip key + surrounding quotes

            i = skipWhitespaceAndColon(json, i)

            if (i >= json.length || json[i] != '"') {
                // non-string value - advance past it; next iteration finds the next key
                i++
                continue
            }

            val value = readJsonString(json, i) ?: break
            i += value.length + 2

            fields[key] = value
        }

        return fields
    }

    private fun readJsonString(json: String, startQuote: Int): String? {
        if (startQuote >= json.length || json[startQuote] != '"') return null

        val sb = StringBuilder()
        var i = startQuote + 1
        while (i < json.length) {
            val ch = json[i]
            if (ch == '\\' && i + 1 < json.length) {
                sb.append(json[i + 1])
                i += 2
                continue
            }
            if (ch == '"') return sb.toString()
            sb.append(ch)
            i++
        }
        return null
    }

    private fun skipWhitespaceAndColon(json: String, start: Int): Int {
        var i = start
        while (i < json.length && (json[i].isWhitespace() || json[i] == ':')) i++
        return i
    }

}
