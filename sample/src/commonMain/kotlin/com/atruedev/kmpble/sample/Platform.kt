package com.atruedev.kmpble.sample

import androidx.compose.runtime.Composable
import com.atruedev.kmpble.permissions.PermissionResult

/**
 * Returns a lambda that requests BLE permissions from the OS.
 * [onResult] is called with the updated [PermissionResult] after the user responds.
 */
@Composable
expect fun rememberPermissionRequester(onResult: (PermissionResult) -> Unit): () -> Unit

/**
 * Opens the OS app settings screen so the user can manually grant permissions.
 */
expect fun openAppSettings(context: Any?)

/**
 * Returns a launcher that opens a file picker for selecting a single file.
 * [onResult] is called with the file name and contents, or (null, null) if cancelled.
 */
@Composable
expect fun rememberFilePickerLauncher(onResult: (name: String?, bytes: ByteArray?) -> Unit): () -> Unit
