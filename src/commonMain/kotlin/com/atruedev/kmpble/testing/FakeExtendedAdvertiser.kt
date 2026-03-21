package com.atruedev.kmpble.testing

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.server.AdvertiserException
import com.atruedev.kmpble.server.ExtendedAdvertiseConfig
import com.atruedev.kmpble.server.ExtendedAdvertiser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fake extended advertiser for unit testing.
 *
 * Records advertising set configurations and tracks active sets.
 *
 * ```kotlin
 * val advertiser = FakeExtendedAdvertiser()
 * val setId = advertiser.startAdvertisingSet(ExtendedAdvertiseConfig(name = "Test"))
 * assertTrue(setId in advertiser.activeSets.value)
 * ```
 */
@ExperimentalBleApi
public class FakeExtendedAdvertiser : ExtendedAdvertiser {

    private val _activeSets = MutableStateFlow<Set<Int>>(emptySet())
    override val activeSets: StateFlow<Set<Int>> = _activeSets.asStateFlow()

    private var nextSetId = 0
    private val configs = mutableMapOf<Int, ExtendedAdvertiseConfig>()
    private var isClosed = false

    override suspend fun startAdvertisingSet(config: ExtendedAdvertiseConfig): Int {
        check(!isClosed) { "Extended advertiser has been closed" }
        val setId = ++nextSetId
        configs[setId] = config
        _activeSets.update { it + setId }
        return setId
    }

    override suspend fun stopAdvertisingSet(setId: Int) {
        configs.remove(setId)
        _activeSets.update { it - setId }
    }

    override fun close() {
        isClosed = true
        configs.clear()
        _activeSets.value = emptySet()
    }

    /** Get the config for a specific advertising set. */
    public fun getConfig(setId: Int): ExtendedAdvertiseConfig? = configs[setId]

    /** Get all active configs. */
    public fun getAllConfigs(): Map<Int, ExtendedAdvertiseConfig> = configs.toMap()
}
