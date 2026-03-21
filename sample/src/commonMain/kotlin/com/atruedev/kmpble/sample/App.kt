package com.atruedev.kmpble.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.connection.StateRestorationConfig
import com.atruedev.kmpble.connection.enableStateRestoration
import com.atruedev.kmpble.logging.BleLogConfig
import com.atruedev.kmpble.logging.PrintBleLogger
import com.atruedev.kmpble.scanner.Advertisement

sealed interface Screen {
    data object Scanner : Screen
    data class DeviceDetail(val advertisement: Advertisement) : Screen
    data object Server : Screen
}

@OptIn(ExperimentalBleApi::class)
@Composable
fun App() {
    val adapter = remember {
        BleLogConfig.logger = PrintBleLogger()
        // No-op on Android; enables BLE connection restoration after background termination on iOS.
        // Must run before BluetoothAdapter() which lazily creates CBCentralManager.
        enableStateRestoration(StateRestorationConfig(identifier = "com.atruedev.kmpble.sample"))
        BluetoothAdapter()
    }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Scanner) }

    SampleTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            PermissionGate {
                Column(modifier = Modifier.fillMaxSize()) {
                    AdapterBanner(adapter)

                    when (val screen = currentScreen) {
                        Screen.Scanner -> ScannerScreen(
                            onDeviceSelected = { currentScreen = Screen.DeviceDetail(it) },
                            onServerTapped = { currentScreen = Screen.Server },
                        )
                        is Screen.DeviceDetail -> DeviceDetailScreen(
                            advertisement = screen.advertisement,
                            onBack = { currentScreen = Screen.Scanner },
                        )
                        Screen.Server -> ServerScreen(
                            onBack = { currentScreen = Screen.Scanner },
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { adapter.close() } }
}
