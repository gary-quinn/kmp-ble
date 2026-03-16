package io.github.garyquinn.kmpble

import android.content.Context
import androidx.startup.Initializer

/**
 * Auto-initializes [KmpBle] via AndroidX App Startup.
 *
 * This runs automatically before the app's `Application.onCreate()`,
 * so consumers never need to call `KmpBle.init(context)` manually.
 *
 * To disable auto-init and call `KmpBle.init()` yourself, add to your
 * app's AndroidManifest.xml:
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="io.github.garyquinn.kmpble.KmpBleInitializer"
 *         tools:node="remove" />
 * </provider>
 * ```
 */
public class KmpBleInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        KmpBle.init(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
