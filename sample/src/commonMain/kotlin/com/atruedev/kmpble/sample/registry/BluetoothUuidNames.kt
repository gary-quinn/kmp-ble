package com.atruedev.kmpble.sample.registry

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object BluetoothUuidNames {
    fun serviceName(uuid: Uuid): String? = serviceNames[uuid.toShortForm()]

    fun characteristicName(uuid: Uuid): String? = characteristicNames[uuid.toShortForm()]

    fun descriptorName(uuid: Uuid): String? = descriptorNames[uuid.toShortForm()]

    fun companyName(companyId: Int): String? = companyNames[companyId]

    private fun Uuid.toShortForm(): String {
        val hex = toString().replace("-", "").lowercase()
        if (hex.endsWith("00001000800000805f9b34fb")) {
            return hex.substring(4, 8)
        }
        return toString().lowercase()
    }

    private val serviceNames =
        mapOf(
            "1800" to "Generic Access",
            "1801" to "Generic Attribute",
            "1802" to "Immediate Alert",
            "1803" to "Link Loss",
            "1804" to "Tx Power",
            "1805" to "Current Time",
            "1806" to "Reference Time Update",
            "1807" to "Next DST Change",
            "180a" to "Device Information",
            "180d" to "Heart Rate",
            "180e" to "Phone Alert Status",
            "180f" to "Battery",
            "1810" to "Blood Pressure",
            "1811" to "Alert Notification",
            "1812" to "Human Interface Device",
            "1813" to "Scan Parameters",
            "1814" to "Running Speed and Cadence",
            "1815" to "Automation IO",
            "1816" to "Cycling Speed and Cadence",
            "1818" to "Cycling Power",
            "1819" to "Location and Navigation",
            "181a" to "Environmental Sensing",
            "181b" to "Body Composition",
            "181c" to "User Data",
            "181d" to "Weight Scale",
            "181e" to "Bond Management",
            "181f" to "Continuous Glucose Monitoring",
            "1820" to "Internet Protocol Support",
            "1821" to "Indoor Positioning",
            "1822" to "Pulse Oximeter",
            "1823" to "HTTP Proxy",
            "1824" to "Transport Discovery",
            "1825" to "Object Transfer",
            "1826" to "Fitness Machine",
            "1827" to "Mesh Provisioning",
            "1828" to "Mesh Proxy",
            "1829" to "Reconnection Configuration",
        )

    private val characteristicNames =
        mapOf(
            "2a00" to "Device Name",
            "2a01" to "Appearance",
            "2a02" to "Peripheral Privacy Flag",
            "2a03" to "Reconnection Address",
            "2a04" to "Peripheral Preferred Connection Parameters",
            "2a05" to "Service Changed",
            "2a06" to "Alert Level",
            "2a07" to "Tx Power Level",
            "2a19" to "Battery Level",
            "2a23" to "System ID",
            "2a24" to "Model Number String",
            "2a25" to "Serial Number String",
            "2a26" to "Firmware Revision String",
            "2a27" to "Hardware Revision String",
            "2a28" to "Software Revision String",
            "2a29" to "Manufacturer Name String",
            "2a2a" to "IEEE Regulatory Certification",
            "2a37" to "Heart Rate Measurement",
            "2a38" to "Body Sensor Location",
            "2a39" to "Heart Rate Control Point",
            "2a49" to "Blood Pressure Feature",
            "2a35" to "Blood Pressure Measurement",
            "2a36" to "Intermediate Cuff Pressure",
            "2a4d" to "Report",
            "2a4e" to "Protocol Mode",
            "2a50" to "PnP ID",
            "2a51" to "Glucose Feature",
            "2a18" to "Glucose Measurement",
            "2a34" to "Glucose Measurement Context",
            "2a52" to "Record Access Control Point",
            "2a53" to "RSC Measurement",
            "2a54" to "RSC Feature",
            "2a55" to "SC Control Point",
            "2a5b" to "CSC Measurement",
            "2a5c" to "CSC Feature",
            "2a5d" to "Sensor Location",
            "2a63" to "Cycling Power Measurement",
            "2a64" to "Cycling Power Vector",
            "2a65" to "Cycling Power Feature",
            "2a66" to "Cycling Power Control Point",
            "2a6e" to "Temperature",
            "2a6f" to "Humidity",
            "2a76" to "UV Index",
            "2a77" to "Irradiance",
            "2a7a" to "Heat Index",
            "2a7b" to "Dew Point",
            "2a9d" to "Weight Measurement",
            "2a9e" to "Weight Scale Feature",
            "2aa1" to "Magnetic Flux Density 2D",
            "2aa2" to "Magnetic Flux Density 3D",
        )

    private val descriptorNames =
        mapOf(
            "2900" to "Characteristic Extended Properties",
            "2901" to "Characteristic User Description",
            "2902" to "Client Characteristic Configuration",
            "2903" to "Server Characteristic Configuration",
            "2904" to "Characteristic Presentation Format",
            "2905" to "Characteristic Aggregate Format",
            "2906" to "Valid Range",
            "2907" to "External Report Reference",
            "2908" to "Report Reference",
            "290b" to "Environmental Sensing Configuration",
            "290c" to "Environmental Sensing Measurement",
            "290d" to "Environmental Sensing Trigger Setting",
        )

    private val companyNames =
        mapOf(
            0x0000 to "Ericsson Technology Licensing",
            0x0001 to "Nokia Mobile Phones",
            0x0002 to "Intel",
            0x0003 to "IBM",
            0x0004 to "Toshiba",
            0x0006 to "Microsoft",
            0x000D to "Texas Instruments",
            0x000F to "Broadcom",
            0x001D to "Qualcomm",
            0x0059 to "Nordic Semiconductor",
            0x004C to "Apple",
            0x0075 to "Samsung Electronics",
            0x0006 to "Microsoft",
            0x00E0 to "Google",
            0x0171 to "Amazon",
            0x0157 to "Huawei Technologies",
            0x038F to "Xiaomi",
            0x0087 to "Garmin International",
            0x0078 to "Nike",
            0x00D2 to "Bose",
            0x0310 to "Fitbit",
            0x0131 to "Sony Corporation",
            0x0154 to "LG Electronics",
            0x0097 to "Realtek Semiconductor",
            0x02FF to "Facebook Technologies",
            0x0499 to "Ruuvi Innovations",
            0x0583 to "Espressif",
            0x0118 to "STMicroelectronics",
            0x0009 to "Infineon Technologies",
            0x000A to "Cambridge Silicon Radio",
            0x00B0 to "Qualcomm Technologies",
            0x0046 to "MediaTek",
            0x024F to "Tile",
            0x0672 to "Shenzhen Goodix Technology",
            0x0060 to "GN Audio (Jabra)",
            0x012D to "Sony Ericsson Mobile Communications",
            0x0189 to "OnePlus Technology",
            0x0385 to "OPPO",
            0x02D5 to "Vivo Mobile Communication",
            0x0122 to "Dialog Semiconductor",
            0x00D9 to "Sennheiser Communications",
            0x000B to "Silicon Wave",
            0x0134 to "Panasonic",
            0x0013 to "Atmel",
            0x0057 to "Harman International",
            0x0058 to "Vizio",
            0x0031 to "Renesas Electronics",
            0x006B to "Cypress Semiconductor",
            0x007D to "Marvell Technology Group",
            0x0092 to "Realme",
        )
}
