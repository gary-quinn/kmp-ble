package com.atruedev.kmpble.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.atruedev.kmpble.permissions.PermissionResult
import com.atruedev.kmpble.permissions.checkBlePermissions
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingUncached
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberPermissionRequester(onResult: (PermissionResult) -> Unit): () -> Unit {
    // Creating a CBCentralManager triggers the iOS Bluetooth permission dialog
    // when authorization is NotDetermined. After the user responds, we re-check.
    return {
        val delegate =
            object : NSObject(), CBCentralManagerDelegateProtocol {
                override fun centralManagerDidUpdateState(central: CBCentralManager) {
                    // Small delay to let the authorization status propagate.
                    MainScope().launch {
                        delay(500)
                        onResult(checkBlePermissions())
                    }
                }
            }
        CBCentralManager(delegate = delegate, queue = null)
    }
}

actual fun openAppSettings(context: Any?) {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url)
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePickerLauncher(onResult: (name: String?, bytes: ByteArray?) -> Unit): () -> Unit {
    // Hold a strong reference to the delegate so it survives until the picker is dismissed.
    val delegateRef = remember { mutableStateOf<NSObject?>(null) }

    return {
        val zipType = UTType.typeWithFilenameExtension("zip")
        if (zipType != null) {
            val controller =
                UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(zipType),
                )
            controller.allowsMultipleSelection = false
            val delegate =
                object : NSObject(), UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>,
                    ) {
                        delegateRef.value = null
                        val picked = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                        if (picked == null) {
                            onResult(null, null)
                            return
                        }
                        picked.startAccessingSecurityScopedResource()
                        try {
                            val data =
                                NSData.dataWithContentsOfURL(
                                    picked,
                                    NSDataReadingUncached,
                                    null,
                                )
                            val name = picked.lastPathComponent ?: "firmware.zip"
                            val bytes = data?.toByteArray()
                            onResult(name, bytes)
                        } finally {
                            picked.stopAccessingSecurityScopedResource()
                        }
                    }

                    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                        delegateRef.value = null
                        onResult(null, null)
                    }
                }
            delegateRef.value = delegate
            controller.delegate = delegate

            val rootVc =
                UIApplication.sharedApplication.connectedScenes
                    .filterIsInstance<UIWindowScene>()
                    .firstOrNull()
                    ?.keyWindow
                    ?.rootViewController
            rootVc?.presentViewController(controller, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { bytes ->
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length)
        }
    }
}
