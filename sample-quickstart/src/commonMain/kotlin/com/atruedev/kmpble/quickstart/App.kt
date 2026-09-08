package com.atruedev.kmpble.quickstart

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun App(ble: QuickstartBle = defaultQuickstartBle()) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (ble === FakeQuickstartBle) {
                QuickstartScreen(ble = ble)
            } else {
                PermissionGate {
                    QuickstartScreen(ble = ble)
                }
            }
        }
    }
}
