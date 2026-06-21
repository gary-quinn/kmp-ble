package com.atruedev.kmpble.quirks

import java.util.ServiceLoader

internal actual fun buildRegistry(
    device: DeviceInfo,
    userConfig: (QuirkRegistry.Builder.() -> Unit)?,
): QuirkRegistry =
    QuirkRegistry
        .Builder(device)
        .apply {
            ServiceLoader.load(QuirkProvider::class.java).forEach(::addProvider)
            userConfig?.invoke(this)
        }.build()
