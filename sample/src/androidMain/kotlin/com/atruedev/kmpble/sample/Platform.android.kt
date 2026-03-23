package com.atruedev.kmpble.sample

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.atruedev.kmpble.permissions.PermissionResult
import com.atruedev.kmpble.permissions.checkBlePermissions

private val BLE_PERMISSIONS =
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

@Composable
actual fun rememberPermissionRequester(onResult: (PermissionResult) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ ->
            onResult(checkBlePermissions())
        }
    return { launcher.launch(BLE_PERMISSIONS) }
}

actual fun openAppSettings(context: Any?) {
    val ctx = context as Context
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    ctx.startActivity(intent)
}

@Composable
actual fun rememberFilePickerLauncher(onResult: (name: String?, bytes: ByteArray?) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                onResult(null, null)
                return@rememberLauncherForActivityResult
            }
            val name = uri.lastPathSegment ?: "firmware.zip"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            onResult(name, bytes)
        }
    return { launcher.launch(arrayOf("application/zip", "application/octet-stream")) }
}
