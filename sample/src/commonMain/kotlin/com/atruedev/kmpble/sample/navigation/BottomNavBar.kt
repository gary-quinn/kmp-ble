package com.atruedev.kmpble.sample.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun BottomNavBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentTab is Tab.Scanner,
            onClick = { onTabSelected(Tab.Scanner) },
            icon = { Text("⊕") },
            label = { Text("Scanner") },
        )
        NavigationBarItem(
            selected = currentTab is Tab.Peripheral,
            onClick = { onTabSelected(Tab.Peripheral) },
            icon = { Text("⊛") },
            label = { Text("Peripheral") },
        )
    }
}
