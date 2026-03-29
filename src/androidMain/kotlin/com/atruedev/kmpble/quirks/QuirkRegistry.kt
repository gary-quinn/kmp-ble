package com.atruedev.kmpble.quirks

import java.util.ServiceLoader
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Immutable registry that resolves device-specific BLE quirks.
 *
 * All values are pre-resolved at build time — [resolve] is O(1).
 * Resolution priority: user overrides > providers > [QuirkKey.default].
 *
 * The registry is frozen after construction — all mutation happens through [Builder].
 */
public class QuirkRegistry private constructor(
    @PublishedApi internal val resolved: Map<QuirkKey<*>, Any>,
    private val description: String,
) {
    public inline fun <reified T : Any> resolve(key: QuirkKey<T>): T = resolved[key] as? T ?: key.default

    /** Pre-computed human-readable summary of active quirks. */
    public fun describe(): String = description

    /** Mutable builder for constructing a [QuirkRegistry]. */
    public class Builder internal constructor(
        private val device: DeviceInfo,
    ) {
        private val entries = mutableListOf<Entry<*>>()

        /** Register a quirk for devices matching [match]. */
        public fun <T : Any> register(
            key: QuirkKey<T>,
            value: T,
            match: (DeviceInfo) -> Boolean,
        ) {
            entries.add(Entry(key) { d -> if (match(d)) value else null })
        }

        /** Register a quirk using a hierarchical device key (e.g. `"manufacturer:model"`). */
        public fun <T : Any> register(
            key: QuirkKey<T>,
            value: T,
            deviceKey: String,
        ) {
            entries.add(
                Entry(key) { d ->
                    if (d.matchKeys.any { it == deviceKey }) value else null
                },
            )
        }

        /** Register a quirk with a device-key-to-value map. Uses hierarchical matching. */
        public fun <T : Any> register(
            key: QuirkKey<T>,
            entries: Map<String, T>,
        ) {
            this.entries.add(Entry(key) { device -> DeviceMatch.matchFirst(device, entries) })
        }

        public fun addProvider(provider: QuirkProvider) {
            provider.contribute(this)
        }

        /** Build an immutable registry. Later registrations take priority (last-write-wins). */
        internal fun build(): QuirkRegistry {
            val resolved = mutableMapOf<QuirkKey<*>, Any>()
            val seen = mutableSetOf<QuirkKey<*>>()
            val active =
                buildList {
                    for (entry in entries.asReversed()) {
                        if (entry.key in seen) continue
                        val (value, desc) = entry.resolveAndDescribe(device) ?: continue
                        seen.add(entry.key)
                        resolved[entry.key] = value
                        if (desc != null) add(desc)
                    }
                }
            val suffix = if (active.isEmpty()) "no device-specific quirks" else active.joinToString()
            val description = "${device.manufacturer}/${device.model} — $suffix"
            return QuirkRegistry(resolved, description)
        }

        private class Entry<T : Any>(
            val key: QuirkKey<T>,
            private val resolver: (DeviceInfo) -> T?,
        ) {
            /** @return (value, description) or null if this entry doesn't match the device. */
            fun resolveAndDescribe(device: DeviceInfo): Pair<Any, String?>? {
                val value = resolver(device) ?: return null
                return value to key.describe(value)
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    public companion object {
        private val userConfig = AtomicReference<(Builder.() -> Unit)?>(null)

        private val defaultRegistryLazy: Lazy<QuirkRegistry> =
            lazy {
                Builder(DeviceInfo.current())
                    .apply {
                        ServiceLoader.load(QuirkProvider::class.java).forEach(::addProvider)
                        userConfig.load()?.invoke(this)
                    }.build()
            }

        /**
         * Configure custom quirks. Must be called before [getInstance].
         * Typically called from `Application.onCreate`.
         */
        public fun configure(block: Builder.() -> Unit) {
            check(!defaultRegistryLazy.isInitialized()) {
                "configure() must be called before getInstance()"
            }
            check(userConfig.compareAndSet(null, block)) {
                "configure() must be called at most once"
            }
        }

        public fun getInstance(): QuirkRegistry = defaultRegistryLazy.value

        /** Create an isolated registry for testing. Providers are NOT auto-loaded. */
        public fun createForTest(
            device: DeviceInfo,
            block: Builder.() -> Unit = {},
        ): QuirkRegistry = Builder(device).apply(block).build()
    }
}
