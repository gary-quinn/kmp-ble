package com.atruedev.kmpble.gatt.internal

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlin.uuid.ExperimentalUuidApi

/**
 * Characteristics whose notifications were already reset on the current connection, for
 * [com.atruedev.kmpble.connection.ConnectionOptions.resetNotificationsOnSubscribe].
 *
 * Atomic because enables run in the collector's context as well as on the peripheral's
 * dispatcher, while [clear] runs on the disconnect path.
 */
@OptIn(ExperimentalUuidApi::class)
internal class NotificationResets {
    private val resetKeys = atomic(emptySet<ObservationKey>())

    /**
     * Runs [enable] with `reset = true` when [requested] and [key] has not been reset since
     * the last [clear]. Concurrent first enables of the same key get one reset between them.
     */
    suspend fun enable(
        key: ObservationKey,
        requested: Boolean,
        enable: suspend (reset: Boolean) -> Unit,
    ) {
        val reset = requested && key !in resetKeys.getAndUpdate { it + key }
        try {
            enable(reset)
        } catch (e: Throwable) {
            // The peripheral may not have seen the 0 -> 1 transition, so let the next enable retry it.
            if (reset) resetKeys.update { it - key }
            throw e
        }
    }

    fun clear() {
        resetKeys.value = emptySet()
    }
}
