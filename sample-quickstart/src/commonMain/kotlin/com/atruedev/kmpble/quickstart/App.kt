package com.atruedev.kmpble.quickstart

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun App(useFakeBle: Boolean = QuickstartConfig.useFakeBle) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (useFakeBle) {
                QuickstartScreen()
            } else {
                PermissionGate {
                    QuickstartScreen()
                }
            }
        }
    }
}
