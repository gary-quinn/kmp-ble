package com.atruedev.kmpble.monitoring

import kotlin.time.Duration

/**
 * Result of a [LePowerController.requestPeerPowerChange] call.
 *
 * Reports whether the peer accepted the power change request and
 * the actual negotiated connection parameters.
 *
 * @property accepted Whether the peer accepted the power change request.
 *   `false` if the platform does not support the operation or the peer rejected.
 * @property targetDbm The requested transmit power level in dBm.
 * @property negotiatedInterval The connection interval the central selected,
 *   or null if the operation is unsupported.
 * @property negotiatedLatency The slave latency the central selected,
 *   or null if the operation is unsupported.
 * @property negotiatedSupervisionTimeout The supervision timeout the central
 *   selected, or null if the operation is unsupported.
 */
public data class PeerPowerResponse(
    val accepted: Boolean,
    val targetDbm: Int,
    val negotiatedInterval: Duration?,
    val negotiatedLatency: Int?,
    val negotiatedSupervisionTimeout: Duration?,
)
