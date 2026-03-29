package com.atruedev.kmpble.sample.navigation

sealed interface Tab {
    data object Scanner : Tab

    data object Peripheral : Tab
}
