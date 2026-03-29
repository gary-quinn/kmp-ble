package com.atruedev.kmpble.sample.detail

import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.sample.detail.bonding.ConnectionRecipeType
import com.atruedev.kmpble.sample.dfu.DfuState
import com.atruedev.kmpble.sample.l2cap.L2capMessage
import com.atruedev.kmpble.sample.l2cap.L2capState
import com.atruedev.kmpble.sample.profiles.ProfileUiState
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class DisplayFormat { HEX, UTF8, DECIMAL, BINARY }

data class TimestampedValue(
    val value: ByteArray,
    val timestamp: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimestampedValue) return false
        return value.contentEquals(other.value) && timestamp == other.timestamp
    }

    override fun hashCode(): Int = 31 * value.contentHashCode() + timestamp.hashCode()
}

@OptIn(ExperimentalUuidApi::class)
data class DescriptorUiModel(
    val descriptor: Descriptor,
    val uuid: Uuid,
    val displayName: String,
    val lastReadValue: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DescriptorUiModel) return false
        return uuid == other.uuid &&
            displayName == other.displayName &&
            (lastReadValue contentEquals other.lastReadValue)
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + (lastReadValue?.contentHashCode() ?: 0)
        return result
    }
}

@OptIn(ExperimentalUuidApi::class)
data class CharacteristicUiModel(
    val characteristic: Characteristic,
    val uuid: Uuid,
    val displayName: String,
    val properties: Characteristic.Properties,
    val isExpanded: Boolean = false,
    val lastReadValue: ByteArray? = null,
    val displayFormat: DisplayFormat = DisplayFormat.HEX,
    val isNotifying: Boolean = false,
    val notificationValues: List<TimestampedValue> = emptyList(),
    val descriptors: List<DescriptorUiModel> = emptyList(),
    val error: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CharacteristicUiModel) return false
        return uuid == other.uuid &&
            displayName == other.displayName &&
            properties == other.properties &&
            isExpanded == other.isExpanded &&
            (lastReadValue contentEquals other.lastReadValue) &&
            displayFormat == other.displayFormat &&
            isNotifying == other.isNotifying &&
            notificationValues == other.notificationValues &&
            descriptors == other.descriptors &&
            error == other.error
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + isExpanded.hashCode()
        result = 31 * result + (lastReadValue?.contentHashCode() ?: 0)
        result = 31 * result + displayFormat.hashCode()
        result = 31 * result + isNotifying.hashCode()
        result = 31 * result + notificationValues.hashCode()
        result = 31 * result + descriptors.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

@OptIn(ExperimentalUuidApi::class)
data class ServiceUiModel(
    val uuid: Uuid,
    val displayName: String,
    val isExpanded: Boolean = false,
    val characteristics: List<CharacteristicUiModel> = emptyList(),
)

@OptIn(ExperimentalUuidApi::class)
data class DeviceDetailUiState(
    val deviceName: String = "",
    val identifier: String = "",
    val connectionState: State = State.Disconnected.ByRequest,
    val rssi: Int? = null,
    val mtu: Int? = null,
    val bondState: BondState? = null,
    val services: List<ServiceUiModel> = emptyList(),
    val error: String? = null,
    val writeDialogTarget: CharacteristicUiModel? = null,
    val selectedRecipe: ConnectionRecipeType? = null,
    val dfuState: DfuState = DfuState.Idle,
    val profileState: ProfileUiState = ProfileUiState(),
    val l2capState: L2capState = L2capState.Closed,
    val l2capMessages: List<L2capMessage> = emptyList(),
)
