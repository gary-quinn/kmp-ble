package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import platform.Foundation.NSUserDefaults
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val DEFAULTS_KEY_PREFIX = "com.atruedev.kmpble.observation-keys"
private const val INDEX_KEY = "com.atruedev.kmpble.observation-index"

/**
 * iOS implementation of ObservationPersistence using NSUserDefaults.
 *
 * Stores observation entries (UUID pairs + backpressure strategy) as JSON.
 * NSUserDefaults is backed by a plist file that survives app termination and
 * is available during background launch (state restoration happens before
 * user interaction).
 *
 * Peripheral IDs with persisted observations are tracked in a separate index key
 * to avoid loading the entire NSUserDefaults dictionary during pruning.
 *
 * Design decision: NSUserDefaults was chosen over Keychain because:
 * 1. Keychain K/N interop requires complex CFTypeRef bridging that varies by Kotlin version
 * 2. NSUserDefaults is immediately available during state restoration (no unlock required)
 * 3. The data (UUID pairs) is low-sensitivity metadata, not user credentials
 */
@OptIn(ExperimentalUuidApi::class)
internal actual class ObservationPersistence actual constructor() {
    private fun keyFor(peripheralId: String) = "$DEFAULTS_KEY_PREFIX.$peripheralId"

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
                    "s" to obs.key.serviceUuid.toString(),
                    "c" to obs.key.charUuid.toString(),
                    "bp" to serializeBackpressure(obs.backpressure),
                )
            }

        val defaults = NSUserDefaults.standardUserDefaults
        defaults.setObject(entries, forKey = keyFor(peripheralId))
        addToIndex(peripheralId)
    }

    actual fun restore(peripheralId: String): Set<PersistedObservation> {
        val array =
            NSUserDefaults.standardUserDefaults.arrayForKey(keyFor(peripheralId))
                ?: return emptySet()

        val result = mutableSetOf<PersistedObservation>()
        for (item in array) {
            val dict = item as? Map<*, *> ?: continue
            val serviceStr = dict["s"] as? String ?: continue
            val charStr = dict["c"] as? String ?: continue
            val bpStr = dict["bp"] as? String
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
                // Skip malformed entries — lenient restore
            }
        }
        return result
    }

    actual fun clear(peripheralId: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(keyFor(peripheralId))
        removeFromIndex(peripheralId)
    }

    /**
     * Remove all persisted observation keys that don't belong to any of the given
     * peripheral IDs. Called during initialization to prevent unbounded key accumulation
     * from peripherals that were connected but never explicitly closed.
     *
     * Uses a separate index key rather than dictionaryRepresentation() to avoid
     * loading the entire NSUserDefaults into memory.
     */
    fun pruneStaleEntries(activePeripheralIds: Set<String>) {
        val defaults = NSUserDefaults.standardUserDefaults
        val indexed = getIndex()
        val stale = indexed - activePeripheralIds

        for (peripheralId in stale) {
            defaults.removeObjectForKey(keyFor(peripheralId))
        }
        if (stale.isNotEmpty()) {
            setIndex(indexed - stale)
        }
    }

    // --- Index management ---

    private fun getIndex(): Set<String> {
        val array =
            NSUserDefaults.standardUserDefaults.arrayForKey(INDEX_KEY)
                ?: return emptySet()
        return array.filterIsInstance<String>().toSet()
    }

    private fun setIndex(ids: Set<String>) {
        if (ids.isEmpty()) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(INDEX_KEY)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(ids.toList(), forKey = INDEX_KEY)
        }
    }

    private fun addToIndex(peripheralId: String) {
        val current = getIndex()
        if (peripheralId !in current) {
            setIndex(current + peripheralId)
        }
    }

    private fun removeFromIndex(peripheralId: String) {
        val current = getIndex()
        if (peripheralId in current) {
            setIndex(current - peripheralId)
        }
    }

    // --- BackpressureStrategy serialization ---

    private fun serializeBackpressure(strategy: BackpressureStrategy): String =
        when (strategy) {
            is BackpressureStrategy.Latest -> "latest"
            is BackpressureStrategy.Buffer -> "buffer:${strategy.capacity}"
            is BackpressureStrategy.Unbounded -> "unbounded"
        }

    private fun deserializeBackpressure(value: String?): BackpressureStrategy =
        when {
            value == null -> BackpressureStrategy.Latest // fallback for pre-strategy data
            value == "latest" -> BackpressureStrategy.Latest
            value.startsWith("buffer:") -> {
                val capacity = value.removePrefix("buffer:").toIntOrNull() ?: 64
                BackpressureStrategy.Buffer(capacity)
            }
            value == "unbounded" -> BackpressureStrategy.Unbounded
            else -> BackpressureStrategy.Latest
        }
}
