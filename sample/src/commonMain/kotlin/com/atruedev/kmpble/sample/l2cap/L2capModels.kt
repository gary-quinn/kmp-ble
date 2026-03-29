package com.atruedev.kmpble.sample.l2cap

import kotlin.time.Instant

sealed interface L2capState {
    data object Closed : L2capState

    data object Opening : L2capState

    data class Open(
        val psm: Int,
    ) : L2capState

    data class Error(
        val message: String,
    ) : L2capState
}

data class L2capMessage(
    val direction: Direction,
    val data: ByteArray,
    val timestamp: Instant,
) {
    enum class Direction { SENT, RECEIVED }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is L2capMessage) return false
        return direction == other.direction && data.contentEquals(other.data) && timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = direction.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
