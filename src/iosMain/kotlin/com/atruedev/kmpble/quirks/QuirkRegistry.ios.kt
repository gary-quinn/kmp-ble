package com.atruedev.kmpble.quirks

/**
 * On iOS, [QuirkProvider] implementations are registered manually
 * rather than discovered via ServiceLoader (which doesn't exist on Kotlin/Native).
 *
 * Call [IosQuirkProviders.register] during app startup before [QuirkRegistry.getInstance].
 */
public object IosQuirkProviders {
    private val providers = mutableListOf<QuirkProvider>()

    /** Register a quirk provider for iOS. Must be called before [QuirkRegistry.getInstance]. */
    public fun register(provider: QuirkProvider) {
        providers.add(provider)
    }

    internal fun loadAll(): List<QuirkProvider> = providers.toList()
}

internal actual fun buildRegistry(
    device: DeviceInfo,
    userConfig: (QuirkRegistry.Builder.() -> Unit)?,
): QuirkRegistry =
    QuirkRegistry
        .Builder(device)
        .apply {
            IosQuirkProviders.loadAll().forEach(::addProvider)
            userConfig?.invoke(this)
        }.build()
