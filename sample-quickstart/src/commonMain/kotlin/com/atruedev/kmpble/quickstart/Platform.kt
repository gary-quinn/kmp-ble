package com.atruedev.kmpble.quickstart

import androidx.compose.runtime.Composable
import com.atruedev.kmpble.permissions.PermissionResult

@Composable
expect fun rememberPermissionRequester(onResult: (PermissionResult) -> Unit): () -> Unit

expect fun openAppSettings(context: Any?)
