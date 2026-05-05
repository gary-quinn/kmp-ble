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
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.logging.BleLogConfig
import com.atruedev.kmpble.logging.PrintBleLogger
import com.atruedev.kmpble.scanner.Advertisement

sealed interface Screen {
    data object Scanner : Screen

    data class DeviceDetail(
        val advertisement: Advertisement,
    ) : Screen

    data class ServiceExplorer(
        val advertisement: Advertisement,
    ) : Screen

    data class HeartRateDemo(
        val advertisement: Advertisement,
    ) : Screen

    data class BatteryDemo(
        val advertisement: Advertisement,
    ) : Screen

    data class DeviceInfoDemo(
        val advertisement: Advertisement,
    ) : Screen

    data class DfuDemo(
        val advertisement: Advertisement,
    ) : Screen

    data class CodecDemo(
        val advertisement: Advertisement,
    ) : Screen

    data object Server : Screen
}

@Composable
fun App() {
    // enableStateRestoration() is intentionally omitted — it requires UIBackgroundModes
    // bluetooth-central in Info.plist, which the sample app does not declare.
    // Apps that need background BLE must call enableStateRestoration() here, before
    // BluetoothAdapter(), to avoid a race with CBCentralManager lazy initialization.
    val adapter =
        remember {
            BleLogConfig.logger = PrintBleLogger()
            BluetoothAdapter()
        }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Scanner) }

    SampleTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            PermissionGate {
                Column(modifier = Modifier.fillMaxSize()) {
                    AdapterBanner(adapter)

                    when (val screen = currentScreen) {
                        Screen.Scanner ->
                            ScannerScreen(
                                onDeviceSelected = { currentScreen = Screen.DeviceDetail(it) },
                                onServerTapped = { currentScreen = Screen.Server },
                            )
                        is Screen.DeviceDetail ->
                            DeviceDetailScreen(
                                advertisement = screen.advertisement,
                                onBack = { currentScreen = Screen.Scanner },
                                onExploreServices = { currentScreen = Screen.ServiceExplorer(screen.advertisement) },
                                onHeartRateDemo = { currentScreen = Screen.HeartRateDemo(screen.advertisement) },
                                onBatteryDemo = { currentScreen = Screen.BatteryDemo(screen.advertisement) },
                                onDeviceInfoDemo = { currentScreen = Screen.DeviceInfoDemo(screen.advertisement) },
                                onDfuDemo = { currentScreen = Screen.DfuDemo(screen.advertisement) },
                                onCodecDemo = { currentScreen = Screen.CodecDemo(screen.advertisement) },
                            )
                        is Screen.ServiceExplorer ->
                            ServiceExplorerScreen(
                                advertisement = screen.advertisement,
                                onBack = { currentScreen = Screen.DeviceDetail(screen.advertisement) },
                            )
                        is Screen.HeartRateDemo ->
                            HeartRateDemoScreen(
                                advertisement = screen.advertisement,
                                onBack = { currentScreen = Screen.DeviceDetail(screen.advertisement) },
                            )
                        is Screen.BatteryDemo ->
                            BatteryDemoScreen(
                                advertisement = screen.advertisement,
                                onBack = { currentScreen = Screen.DeviceDetail(screen.advertisement) },
                            )
                        is Screen.DeviceInfoDemo ->
                            DeviceInfoDemoScreen(
                                advertisement = screen.advertisement,
                                onBack = { currentScreen = Screen.DeviceDetail(screen.advertisement) },
                            )
                        is Screen.DfuDemo ->
                            DfuDemoScreen(
                                advertisement = screen.advertisement,
                                onBack = { currentScreen = Screen.DeviceDetail(screen.advertisement) },
                            )
                        is Screen.CodecDemo ->
                            CodecDemoScreen(
                                advertisement = screen.advertisement,
                                onBack = { currentScreen = Screen.DeviceDetail(screen.advertisement) },
                            )
                        Screen.Server ->
                            ServerScreen(
                                onBack = { currentScreen = Screen.Scanner },
                            )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { adapter.close() } }
}
