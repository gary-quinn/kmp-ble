package com.atruedev.kmpble.dfu.protocol

internal object DfuOpcode {
    const val CREATE = 0x01
    const val SET_PRN = 0x02
    const val CALCULATE_CHECKSUM = 0x03
    const val EXECUTE = 0x04
    const val SELECT = 0x06
    const val RESPONSE = 0x60
}

internal object DfuObjectType {
    const val COMMAND = 0x01
    const val DATA = 0x02
}

internal object DfuResultCode {
    const val INVALID_CODE = 0x00
    const val SUCCESS = 0x01
    const val OPCODE_NOT_SUPPORTED = 0x02
    const val INVALID_PARAMETER = 0x03
    const val INSUFFICIENT_RESOURCES = 0x04
    const val INVALID_OBJECT = 0x05
    const val UNSUPPORTED_TYPE = 0x06
    const val OPERATION_NOT_PERMITTED = 0x07
    const val OPERATION_FAILED = 0x0A
    const val EXTENDED_ERROR = 0x0B

    fun describe(code: Int): String = when (code) {
        INVALID_CODE -> "Invalid code"
        SUCCESS -> "Success"
        OPCODE_NOT_SUPPORTED -> "Opcode not supported"
        INVALID_PARAMETER -> "Invalid parameter"
        INSUFFICIENT_RESOURCES -> "Insufficient resources"
        INVALID_OBJECT -> "Invalid object"
        UNSUPPORTED_TYPE -> "Unsupported type"
        OPERATION_NOT_PERMITTED -> "Operation not permitted"
        OPERATION_FAILED -> "Operation failed"
        EXTENDED_ERROR -> "Extended error"
        else -> "Unknown result code: 0x${code.toString(16)}"
    }
}

internal object DfuExtendedError {
    const val NO_ERROR = 0x00
    const val INVALID_ERROR_CODE = 0x01
    const val WRONG_COMMAND_FORMAT = 0x02
    const val UNKNOWN_COMMAND = 0x03
    const val INIT_COMMAND_INVALID = 0x04
    const val FW_VERSION_FAILURE = 0x05
    const val HW_VERSION_FAILURE = 0x06
    const val SD_VERSION_FAILURE = 0x07
    const val SIGNATURE_MISSING = 0x08
    const val WRONG_HASH_TYPE = 0x09
    const val HASH_FAILED = 0x0A
    const val WRONG_SIGNATURE_TYPE = 0x0B
    const val VERIFICATION_FAILED = 0x0C
    const val INSUFFICIENT_SPACE = 0x0D

    fun describe(code: Int): String = when (code) {
        NO_ERROR -> "No error"
        INVALID_ERROR_CODE -> "Invalid error code"
        WRONG_COMMAND_FORMAT -> "Wrong command format"
        UNKNOWN_COMMAND -> "Unknown command"
        INIT_COMMAND_INVALID -> "Init command invalid"
        FW_VERSION_FAILURE -> "FW version failure"
        HW_VERSION_FAILURE -> "HW version failure"
        SD_VERSION_FAILURE -> "SD version failure"
        SIGNATURE_MISSING -> "Signature missing"
        WRONG_HASH_TYPE -> "Wrong hash type"
        HASH_FAILED -> "Hash failed"
        WRONG_SIGNATURE_TYPE -> "Wrong signature type"
        VERIFICATION_FAILED -> "Verification failed"
        INSUFFICIENT_SPACE -> "Insufficient space"
        else -> "Unknown extended error: 0x${code.toString(16)}"
    }
}
