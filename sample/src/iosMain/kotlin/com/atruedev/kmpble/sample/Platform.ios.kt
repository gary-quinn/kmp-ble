package com.atruedev.kmpble.sample

import androidx.compose.runtime.Composable
import com.atruedev.kmpble.permissions.PermissionResult
import com.atruedev.kmpble.permissions.checkBlePermissions
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun rememberPermissionRequester(onResult: (PermissionResult) -> Unit): () -> Unit {
    // Creating a CBCentralManager triggers the iOS Bluetooth permission dialog
    // when authorization is NotDetermined. After the user responds, we re-check.
    return {
        val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                // State updated after user responds to permission dialog.
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
