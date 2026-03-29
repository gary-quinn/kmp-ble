package com.atruedev.kmpble.sample.permission

sealed interface PermissionResult {
    data object Granted : PermissionResult

    data class Denied(
        val permissions: List<String>,
    ) : PermissionResult

    data class PermanentlyDenied(
        val permissions: List<String>,
    ) : PermissionResult
}

expect class PermissionRequester {
    suspend fun requestBlePermissions(): PermissionResult
}
