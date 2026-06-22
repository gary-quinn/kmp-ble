package com.atruedev.kmpble.adapter

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

public class AndroidBluetoothAdapter(
    private val context: Context,
) : BluetoothAdapter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val state: StateFlow<BluetoothAdapterState> =
        callbackFlow {
            // Emit initial state
            trySend(currentState())

            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        ctx: Context,
                        intent: Intent,
                    ) {
                        if (intent.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                            trySend(currentState())
                        }
                    }
                }

            val filter = IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            awaitClose {
                context.unregisterReceiver(receiver)
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = currentState(),
        )

    override val capabilities: BleCapabilities by lazy {
        val adapter =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return@lazy BleCapabilities.None
        val bt = adapter.adapter ?: return@lazy BleCapabilities.None

        BleCapabilities(
            supportsExtendedAdvertising =
                if (Build.VERSION.SDK_INT >= 26) {
                    bt.isLeExtendedAdvertisingSupported
                } else {
                    false
                },
            supportsLe2mPhy = if (Build.VERSION.SDK_INT >= 26) bt.isLe2MPhySupported else false,
            supportsLeCodedPhy = if (Build.VERSION.SDK_INT >= 26) bt.isLeCodedPhySupported else false,
            supportsPeriodicAdvertising =
                if (Build.VERSION.SDK_INT >= 26) {
                    bt.isLePeriodicAdvertisingSupported
                } else {
                    false
                },
            // LE Power Control capability query is not exposed as a public Android API.
            supportsLePowerControl = false,
            // isLeAudioSupported() returns Int (BluetoothStatusCodes), not Boolean.
            supportsLeAudio =
                if (Build.VERSION.SDK_INT >= 33) {
                    bt.isLeAudioSupported == BluetoothStatusCodes.FEATURE_SUPPORTED
                } else {
                    false
                },
            // Connection Subrating may not be queryable on all compile SDK versions.
            supportsConnectionSubrating = false,
            // PAST (Periodic Advertising Sync Transfer) requires API 31+.
            supportsPast = Build.VERSION.SDK_INT >= 31,
            // Direction Finding (AoA/AoD) requires API 34+. Not queryable through public API.
            supportsDirectionFinding = false,
        )
    }

    private fun currentState(): BluetoothAdapterState {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BluetoothAdapterState.Unsupported
        }

        val hasScan =
            context.checkPermission(
                Manifest.permission.BLUETOOTH_SCAN,
                Process.myPid(),
                Process.myUid(),
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasScan) return BluetoothAdapterState.Unauthorized

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter =
            bluetoothManager?.adapter
                ?: return BluetoothAdapterState.Unsupported

        return when (adapter.state) {
            android.bluetooth.BluetoothAdapter.STATE_ON -> BluetoothAdapterState.On
            android.bluetooth.BluetoothAdapter.STATE_OFF -> BluetoothAdapterState.Off
            android.bluetooth.BluetoothAdapter.STATE_TURNING_ON,
            android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF,
            -> BluetoothAdapterState.Unavailable
            else -> BluetoothAdapterState.Unavailable
        }
    }

    override fun close() {
        scope.cancel()
    }
}
