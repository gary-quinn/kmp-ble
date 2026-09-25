package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.backend.AdvertiserTransport
import com.atruedev.kmpble.backend.AdvertisingSetSpec
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.AdvertiseInterval
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.AdvertiserException
import com.atruedev.kmpble.server.ExtendedAdvertiseConfig
import com.atruedev.kmpble.server.ExtendedAdvertiser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(KmpBleBackendApi::class)
internal class BackendAdvertiser(
    private val transport: AdvertiserTransport,
) : Advertiser {
    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)

    override suspend fun startAdvertising(config: AdvertiseConfig) {
        mutex.withLock {
            if (closed.get()) throw AdvertiserException.StartFailed("Advertiser has been closed")
            if (_isAdvertising.value) throw AdvertiserException.AlreadyAdvertising()
            startSet(transport, LEGACY_SET_ID, config.toSpec())
            _isAdvertising.value = true
            logEvent(BleLogEvent.ServerLifecycle("advertising started"))
        }
    }

    override suspend fun stopAdvertising() {
        mutex.withLock {
            if (!_isAdvertising.value) return
            transport.stop(LEGACY_SET_ID)
            _isAdvertising.value = false
            logEvent(BleLogEvent.ServerLifecycle("advertising stopped"))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { transport.close() }
        _isAdvertising.value = false
    }

    private companion object {
        const val LEGACY_SET_ID = 0
    }
}

@OptIn(KmpBleBackendApi::class, ExperimentalBleApi::class)
internal class BackendExtendedAdvertiser(
    private val transport: AdvertiserTransport,
    private val backendId: String,
) : ExtendedAdvertiser {
    private val _activeSets = MutableStateFlow<Set<Int>>(emptySet())
    override val activeSets: StateFlow<Set<Int>> = _activeSets.asStateFlow()
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)
    private var nextSetId = 0

    override suspend fun startAdvertisingSet(config: ExtendedAdvertiseConfig): Int =
        mutex.withLock {
            if (closed.get()) throw AdvertiserException.StartFailed("Extended advertiser has been closed")
            if (config.periodicAdvertising != null) {
                throw AdvertiserException.NotSupported(
                    "Periodic advertising is not supported by the $backendId backend",
                )
            }
            if (_activeSets.value.size >= transport.maxAdvertisingSets) {
                throw AdvertiserException.StartFailed(
                    "The $backendId backend supports ${transport.maxAdvertisingSets} concurrent advertising set(s)",
                )
            }
            val setId = ++nextSetId
            startSet(transport, setId, config.toSpec(legacy = transport.maxAdvertisingSets == 1))
            _activeSets.update { it + setId }
            logEvent(BleLogEvent.ServerLifecycle("advertising set $setId started"))
            setId
        }

    override suspend fun stopAdvertisingSet(setId: Int) {
        mutex.withLock {
            if (setId !in _activeSets.value) return
            transport.stop(setId)
            _activeSets.update { it - setId }
            logEvent(BleLogEvent.ServerLifecycle("advertising set $setId stopped"))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { transport.close() }
        _activeSets.value = emptySet()
    }
}

@OptIn(KmpBleBackendApi::class)
private suspend fun startSet(
    transport: AdvertiserTransport,
    setId: Int,
    spec: AdvertisingSetSpec,
) {
    try {
        transport.start(setId, spec)
    } catch (e: AdvertiserException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AdvertiserException.StartFailed(e.message ?: "transport start failed", e)
    }
}

@OptIn(KmpBleBackendApi::class)
private fun AdvertiseConfig.toSpec(): AdvertisingSetSpec =
    AdvertisingSetSpec(
        name = name,
        serviceUuids = serviceUuids,
        manufacturerData = manufacturerData,
        serviceData = emptyMap(),
        connectable = connectable,
        scannable = true,
        includeTxPower = includeTxPower,
        mode = mode,
        txPower = txPower,
        legacy = true,
        primaryPhy = Phy.Le1M,
        secondaryPhy = null,
    )

@OptIn(KmpBleBackendApi::class, ExperimentalBleApi::class)
private fun ExtendedAdvertiseConfig.toSpec(legacy: Boolean): AdvertisingSetSpec =
    AdvertisingSetSpec(
        name = name,
        serviceUuids = serviceUuids,
        manufacturerData = manufacturerData,
        serviceData = serviceData,
        connectable = connectable,
        scannable = scannable,
        includeTxPower = includeTxPower,
        mode =
            when (interval) {
                AdvertiseInterval.LowPower -> AdvertiseMode.LowPower
                AdvertiseInterval.Balanced -> AdvertiseMode.Balanced
                AdvertiseInterval.LowLatency -> AdvertiseMode.LowLatency
            },
        txPower = txPower,
        legacy = legacy,
        primaryPhy = primaryPhy,
        secondaryPhy = if (legacy) null else secondaryPhy,
    )
