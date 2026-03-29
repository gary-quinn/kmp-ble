package com.atruedev.kmpble.sample.permission

import androidx.compose.runtime.Composable

@Composable
actual fun rememberBlePermissionLauncher(onResult: (PermissionResult) -> Unit): () -> Unit {
    // iOS triggers Bluetooth permission automatically on first CBCentralManager usage.
    // No explicit permission request needed — the system prompt appears when kmp-ble
    // creates its first CBCentralManager instance.
    return { onResult(PermissionResult.Granted) }
}
