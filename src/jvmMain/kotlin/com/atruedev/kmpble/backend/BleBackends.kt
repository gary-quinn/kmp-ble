package com.atruedev.kmpble.backend

import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicReference

/**
 * Resolves the [BleBackend] that the portable JVM factories delegate to.
 *
 * Resolution order:
 * 1. A backend passed to [install].
 * 2. The discovered backend whose [BleBackend.id] equals the [BACKEND_PROPERTY] system property.
 * 3. The discovered backend with the highest [BleBackend.priority] among those reporting
 *    [BleBackend.isSupported].
 */
@KmpBleBackendApi
public object BleBackends {
    /** System property selecting a backend by [BleBackend.id], for example `-Dkmpble.backend=bluez`. */
    public const val BACKEND_PROPERTY: String = "kmpble.backend"

    private val installed = AtomicReference<BleBackend?>(null)

    private val discovered: List<BleBackend> by lazy { loadBackends() }

    /** Forces [backend] for every subsequent factory call. Pass `null` to restore discovery. */
    public fun install(backend: BleBackend?) {
        installed.set(backend)
    }

    /** Backends found on the classpath, supported or not. */
    public fun available(): List<BleBackend> = discovered

    /** The backend the portable factories use, or `null` when none applies to this host. */
    public fun current(): BleBackend? {
        installed.get()?.let { return it }
        val requested = System.getProperty(BACKEND_PROPERTY)?.trim()?.takeIf { it.isNotEmpty() }
        if (requested != null) return discovered.firstOrNull { it.id == requested }
        return discovered
            .filter { runCatching { it.isSupported() }.getOrDefault(false) }
            .maxByOrNull { it.priority }
    }

    internal fun require(feature: String): BleBackend =
        current() ?: throw UnsupportedOperationException(unavailableMessage(feature))

    internal fun unavailableMessage(feature: String): String {
        val host = "${System.getProperty("os.name", "unknown")} ${System.getProperty("os.arch", "unknown")}"
        val requested = System.getProperty(BACKEND_PROPERTY)
        val reason =
            if (requested.isNullOrBlank()) {
                "no kmp-ble backend supports this host ($host)"
            } else {
                "backend '$requested' requested via -D$BACKEND_PROPERTY is not on the classpath"
            }
        return "$feature is not supported on this JVM target: $reason. " +
            "Add com.atruedev:kmp-ble-bluez (Linux) or com.atruedev:kmp-ble-macos (macOS arm64), " +
            "or use the Fake* test doubles."
    }

    private fun loadBackends(): List<BleBackend> {
        val loaders =
            listOfNotNull(BleBackend::class.java.classLoader, Thread.currentThread().contextClassLoader).distinct()
        return loaders
            .flatMap { loader -> ServiceLoader.load(BleBackend::class.java, loader).toList() }
            .distinctBy { it.id }
    }
}
