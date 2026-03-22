package com.atruedev.kmpble.testing

import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.AdvertiserException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake advertiser for unit testing.
 *
 * Records advertising configurations and tracks state.
 *
 * ## Example
 *
 * ```kotlin
 * val advertiser = FakeAdvertiser()
 * advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
 *
 * assertTrue(advertiser.isAdvertising.value)
 * assertEquals("Test", advertiser.getLastConfig()?.name)
 * ```
 */
public class FakeAdvertiser : Advertiser {

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var lastConfig: AdvertiseConfig? = null
    private var isClosed = false

    override suspend fun startAdvertising(config: AdvertiseConfig) {
        check(!isClosed) { "Advertiser has been closed" }
        if (_isAdvertising.value) throw AdvertiserException.AlreadyAdvertising()
        lastConfig = config
        _isAdvertising.value = true
    }

    override suspend fun stopAdvertising() {
        _isAdvertising.value = false
    }

    override fun close() {
        isClosed = true
        _isAdvertising.value = false
    }

    // --- Test helpers ---

    /** Get the last config passed to [startAdvertising]. */
    public fun getLastConfig(): AdvertiseConfig? = lastConfig
}
