package com.atruedev.kmpble.dfu

import com.atruedev.kmpble.dfu.transport.DfuUuids
import com.atruedev.kmpble.dfu.transport.EspOtaUuids
import com.atruedev.kmpble.dfu.transport.SmpUuids
import com.atruedev.kmpble.peripheral.Peripheral
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Detects which DFU protocol a connected peripheral supports by inspecting
 * its discovered GATT services.
 *
 * The peripheral must be connected with services discovered before calling
 * [detect]. Returns `null` if no known DFU service is found.
 *
 * ## Usage
 * ```
 * peripheral.connect(options)
 * val protocolType = DfuDetector.detect(peripheral)
 * val controller = when (protocolType) {
 *     DfuProtocolType.NORDIC -> DfuController(peripheral)
 *     DfuProtocolType.MCUBOOT -> DfuController(peripheral, McuBootDfuProtocol())
 *     DfuProtocolType.ESP_OTA -> DfuController(peripheral, EspOtaDfuProtocol())
 *     null -> error("No DFU service found")
 * }
 * ```
 */
public object DfuDetector {

    @OptIn(ExperimentalUuidApi::class)
    private val serviceToProtocol: List<Pair<Uuid, DfuProtocolType>> = listOf(
        DfuUuids.DFU_SERVICE to DfuProtocolType.NORDIC,
        SmpUuids.SMP_SERVICE to DfuProtocolType.MCUBOOT,
        EspOtaUuids.OTA_SERVICE to DfuProtocolType.ESP_OTA,
    )

    /**
     * Detect which DFU protocol the peripheral supports.
     *
     * Checks discovered services against known DFU service UUIDs in priority
     * order: Nordic DFU, MCUboot SMP, ESP OTA.
     *
     * @param peripheral a connected peripheral with discovered services
     * @return detected [DfuProtocolType], or `null` if no DFU service is found
     */
    @OptIn(ExperimentalUuidApi::class)
    public fun detect(peripheral: Peripheral): DfuProtocolType? {
        val services = peripheral.services.value ?: return null
        val serviceUuids = services.map { it.uuid }.toSet()

        return serviceToProtocol.firstOrNull { (uuid, _) ->
            uuid in serviceUuids
        }?.second
    }
}
