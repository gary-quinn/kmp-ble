package io.github.garyquinn.kmpble.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.garyquinn.kmpble.permissions.PermissionResult
import io.github.garyquinn.kmpble.permissions.checkBlePermissions

@Composable
fun PermissionGate(
    platformContext: Any? = null,
    content: @Composable () -> Unit,
) {
    var result by remember { mutableStateOf(checkBlePermissions()) }
    val requestPermissions = rememberPermissionRequester { result = it }

    // Re-check on resume (e.g. after returning from Settings)
    LaunchedEffect(Unit) { result = checkBlePermissions() }

    when (val current = result) {
        is PermissionResult.Granted -> content()

        is PermissionResult.Denied -> {
            PermissionPrompt(
                message = "BLE permissions are required to scan and connect to devices.",
                buttonText = "Grant Permissions",
                onClick = requestPermissions,
            )
        }

        is PermissionResult.PermanentlyDenied -> {
            PermissionPrompt(
                message = "BLE permissions were permanently denied. Please enable them in Settings.",
                buttonText = "Open Settings",
                onClick = { openAppSettings(platformContext) },
            )
        }
    }
}

@Composable
private fun PermissionPrompt(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}
