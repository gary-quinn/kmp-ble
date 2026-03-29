package com.atruedev.kmpble.sample.peripheral

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.sample.peripheral.advertising.AdvertisingScreen
import com.atruedev.kmpble.sample.peripheral.advertising.AdvertisingViewModel
import com.atruedev.kmpble.sample.peripheral.server.GattServerScreen
import com.atruedev.kmpble.sample.peripheral.server.GattServerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeripheralScreen() {
    val gattServerViewModel = viewModel { GattServerViewModel() }
    val advertisingViewModel = viewModel { AdvertisingViewModel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Peripheral") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
        ) {
            GattServerScreen(viewModel = gattServerViewModel)

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            AdvertisingScreen(viewModel = advertisingViewModel)
        }
    }
}
