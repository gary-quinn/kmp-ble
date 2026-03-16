package com.atruedev.kmpble.permissions

public sealed interface PermissionResult {
    public data object Granted : PermissionResult
    public data class Denied(val permissions: List<String>) : PermissionResult
    public data class PermanentlyDenied(val permissions: List<String>) : PermissionResult
}
