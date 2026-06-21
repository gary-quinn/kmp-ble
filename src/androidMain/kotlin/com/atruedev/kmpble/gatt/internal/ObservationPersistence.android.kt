package com.atruedev.kmpble.gatt.internal

import android.content.Context
import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Android implementation of ObservationPersistence using SharedPreferences.
 *
 * Store and restore observation entries (UUID pairs + backpressure strategy)
 * for seamless GATT reconnection across app restarts. While in-memory
 * observation state persists across disconnects within a session,
 * SharedPreferences ensures observations survive process death.
 *
 * The companion [context] must be set before any save/restore operations,
 * typically during library initialization or first Peripheral construction.
 * After close(), call [clear] to remove the peripheral's persisted state.
 * Entries are stored in the pref "com.atruedev.kmpble.cccd" as
 * "obs.<peripheralId>" keys with JSON-encoded maps.
 *
 * Design decision: SharedPreferences instead of DataStore because:
 * 1. Synchronous reads are required during reconnection (no suspend in callbacks)
 * 2. The data volume is tiny (a few UUID pairs per peripheral)
 * 3. No migration complexity for a simple key-value mapping
 */
@OptIn(ExperimentalUuidApi::class)
internal actual class ObservationPersistence actual constructor() {
    /**
     * SharedPreferences instance, lazily resolved from the companion [context].
     * Throws [IllegalStateException] if [context] was never set.
     */
    private val prefs by lazy {
        val ctx =
            context
                ?: throw IllegalStateException(
                    "ObservationPersistence.context must be set before use. " +
                        "Call ObservationPersistence.context = applicationContext during initialization.",
                )
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun save(
        peripheralId: String,
        observations: Set<PersistedObservation>,
    ) {
        if (observations.isEmpty()) {
            clear(peripheralId)
            return
        }

        val entries =
            observations.map { obs ->
                mapOf(
                    KEY_SERVICE to obs.key.serviceUuid.toString(),
                    KEY_CHAR to obs.key.charUuid.toString(),
                    KEY_BACKPRESSURE to serializeBackpressure(obs.backpressure),
                )
            }

        val json = JsonArrayEncoder.encode(entries)
        prefs
            .edit()
            .putString(keyFor(peripheralId), json)
            .apply()
    }

    actual fun restore(peripheralId: String): Set<PersistedObservation> {
        val json = prefs.getString(keyFor(peripheralId), null) ?: return emptySet()

        val entries =
            try {
                JsonArrayEncoder.decode(json)
            } catch (_: Exception) {
                return emptySet()
            }

        val result = mutableSetOf<PersistedObservation>()
        for (entry in entries) {
            val serviceStr = entry[KEY_SERVICE] as? String ?: continue
            val charStr = entry[KEY_CHAR] as? String ?: continue
            val bpStr = entry[KEY_BACKPRESSURE] as? String
            try {
                result.add(
                    PersistedObservation(
                        key =
                            ObservationKey(
                                serviceUuid = Uuid.parse(serviceStr),
                                charUuid = Uuid.parse(charStr),
                            ),
                        backpressure = deserializeBackpressure(bpStr),
                    ),
                )
            } catch (_: Exception) {
                // Skip malformed entries - lenient restore
            }
        }
        return result
    }

    actual fun clear(peripheralId: String) {
        prefs
            .edit()
            .remove(keyFor(peripheralId))
            .apply()
    }

    private fun keyFor(peripheralId: String) = "$KEY_PREFIX.$peripheralId"

    private fun serializeBackpressure(strategy: BackpressureStrategy): String =
        when (strategy) {
            is BackpressureStrategy.Latest -> "latest"
            is BackpressureStrategy.Buffer -> "buffer:${strategy.capacity}"
            is BackpressureStrategy.Unbounded -> "unbounded"
        }

    private fun deserializeBackpressure(value: String?): BackpressureStrategy =
        when {
            value == null -> BackpressureStrategy.Latest
            value == "latest" -> BackpressureStrategy.Latest
            value.startsWith("buffer:") -> {
                val capacity = value.removePrefix("buffer:").toIntOrNull() ?: 64
                BackpressureStrategy.Buffer(capacity)
            }
            value == "unbounded" -> BackpressureStrategy.Unbounded
            else -> BackpressureStrategy.Latest
        }

    companion object {
        /**
         * Application context for SharedPreferences access.
         * Must be set before any persistence operations.
         * Set once during library initialization (e.g., in Application.onCreate).
         */
        @Volatile
        var context: Context? = null

        private const val PREFS_NAME = "com.atruedev.kmpble.cccd"
        private const val KEY_PREFIX = "obs"
        private const val KEY_SERVICE = "s"
        private const val KEY_CHAR = "c"
        private const val KEY_BACKPRESSURE = "bp"
    }
}

/**
 * Minimal JSON array-of-maps encoder/decoder for SharedPreferences storage.
 *
 * Avoids pulling in a full JSON library. The schema is simple enough
 * for a custom encoder:
 *   [{"s":"uuid1","c":"uuid2","bp":"latest"}, ...]
 *
 * Handles escaping of backslash, double-quote, and control characters
 * in UUID strings (though standard UUIDs contain none of these).
 */
@OptIn(ExperimentalUuidApi::class)
internal object JsonArrayEncoder {
    fun encode(entries: List<Map<String, String>>): String {
        if (entries.isEmpty()) return "[]"
        val sb = StringBuilder("[")
        for ((i, entry) in entries.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append('{')
            var first = true
            for ((key, value) in entry) {
                if (!first) sb.append(',')
                first = false
                sb.append('"')
                sb.append(escape(key))
                sb.append("\":\"")
                sb.append(escape(value))
                sb.append('"')
            }
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    fun decode(json: String): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        val trimmed = json.trim()
        if (trimmed.length < 2 || trimmed[0] != '[' || trimmed[trimmed.lastIndex] != ']') {
            return result
        }

        var pos = 1
        while (pos < trimmed.lastIndex) {
            val objStart = trimmed.indexOf('{', pos)
            if (objStart < 0) break
            val objEnd = findMatchingBrace(trimmed, objStart)
            if (objEnd < 0) break

            val obj = trimmed.substring(objStart + 1, objEnd)
            val map = mutableMapOf<String, String>()
            var keyPos = 0
            while (keyPos < obj.length) {
                val keyStart = obj.indexOf('"', keyPos)
                if (keyStart < 0) break
                val keyEnd = obj.indexOf('"', keyStart + 1)
                if (keyEnd < 0) break
                val key = unescape(obj.substring(keyStart + 1, keyEnd))

                val colon = obj.indexOf(':', keyEnd + 1)
                if (colon < 0) break
                val valStart = obj.indexOf('"', colon + 1)
                if (valStart < 0) break
                val valEnd = obj.indexOf('"', valStart + 1)
                if (valEnd < 0) break
                val value = unescape(obj.substring(valStart + 1, valEnd))

                map[key] = value
                keyPos = valEnd + 1
            }
            if (map.isNotEmpty()) result.add(map)
            pos = objEnd + 2 // skip '}' and ','
        }
        return result
    }

    private fun findMatchingBrace(
        s: String,
        start: Int,
    ): Int {
        var depth = 0
        for (i in start until s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
                '"' -> {
                    // Skip over string contents
                    var j = i + 1
                    while (j < s.length) {
                        when (s[j]) {
                            '\\' -> j += 2
                            '"' -> {
                                i = j
                                break
                            }
                            else -> j++
                        }
                    }
                }
            }
        }
        return -1
    }

    private fun escape(s: String): String =
        s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun unescape(s: String): String =
        s
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
}
