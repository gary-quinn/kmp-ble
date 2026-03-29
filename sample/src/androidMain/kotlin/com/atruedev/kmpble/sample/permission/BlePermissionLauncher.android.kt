package com.atruedev.kmpble.sample.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private val blePermissions =
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

@Composable
actual fun rememberBlePermissionLauncher(onResult: (PermissionResult) -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val denied = permissions.filterValues { !it }.keys.toList()
            if (denied.isEmpty()) {
                onResult(PermissionResult.Granted)
            } else {
                val activity = context as? Activity
                val permanentlyDenied =
                    if (activity != null) {
                        denied.filter { !activity.shouldShowRequestPermissionRationale(it) }
                    } else {
                        emptyList()
                    }
                if (permanentlyDenied.isNotEmpty()) {
                    onResult(PermissionResult.PermanentlyDenied(permanentlyDenied))
                } else {
                    onResult(PermissionResult.Denied(denied))
                }
            }
        }

    return {
        val alreadyGranted =
            blePermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        if (alreadyGranted) {
            onResult(PermissionResult.Granted)
        } else {
            launcher.launch(blePermissions)
        }
    }
}
