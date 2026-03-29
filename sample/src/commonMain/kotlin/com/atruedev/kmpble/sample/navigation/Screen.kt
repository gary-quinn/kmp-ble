package com.atruedev.kmpble.sample.navigation

import com.atruedev.kmpble.scanner.Advertisement
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
sealed interface Screen {
    data object Scanner : Screen

    data class DeviceDetail(
        val advertisement: Advertisement,
    ) : Screen
}
