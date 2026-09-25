package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.macos.Macos
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Loads `libkmpble_macos.dylib` once per process: from [Macos.LIBRARY_PATH_PROPERTY] when set,
 * otherwise extracted from the jar into a temporary directory.
 */
internal object NativeLibrary {
    const val RESOURCE = "/native/macos-arm64/libkmpble_macos.dylib"

    @Volatile private var loaded = false

    fun isBundled(): Boolean = NativeLibrary::class.java.getResource(RESOURCE) != null

    @Synchronized
    fun load() {
        if (loaded) return
        val override = System.getProperty(Macos.LIBRARY_PATH_PROPERTY)?.takeIf { it.isNotBlank() }
        if (override != null) {
            System.load(override)
        } else {
            val stream =
                NativeLibrary::class.java.getResourceAsStream(RESOURCE)
                    ?: throw UnsatisfiedLinkError(
                        "kmp-ble-macos native library is missing from the jar ($RESOURCE). " +
                            "The artifact must be built on a macOS host.",
                    )
            val directory = Files.createTempDirectory("kmpble-macos")
            val file = directory.resolve("libkmpble_macos.dylib")
            stream.use { Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING) }
            file.toFile().deleteOnExit()
            directory.toFile().deleteOnExit()
            System.load(file.toAbsolutePath().toString())
        }
        loaded = true
    }
}
