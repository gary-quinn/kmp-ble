package com.atruedev.kmpble.gatt.internal

import android.content.Context
import kotlinx.atomicfu.atomic
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

    companion object {
        /**
         * Application context for SharedPreferences access.
         * Must be set before any persistence operations.
         * Set once during library initialization (e.g., in Application.onCreate).
         *
         * The [AtomicRef] ensures visibility across the initialization path
         * where the context is set on the main thread before any background
         * GATT operations occur.
         */
        val context = atomic<Context?>(null)

        private const val PREFS_NAME = "com.atruedev.kmpble.cccd"
        private const val KEY_PREFIX = "obs"
        private const val KEY_SERVICE = "s"
        private const val KEY_CHAR = "c"
        private const val KEY_BACKPRESSURE = "bp"
    }
}
