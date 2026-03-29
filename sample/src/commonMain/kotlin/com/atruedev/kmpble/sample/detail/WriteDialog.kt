package com.atruedev.kmpble.sample.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.gatt.WriteType

@Composable
internal fun WriteDialog(
    characteristicName: String,
    onDismiss: () -> Unit,
    onWrite: (ByteArray, WriteType) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    var isHexMode by remember { mutableStateOf(true) }
    var writeType by remember { mutableStateOf(WriteType.WithResponse) }
    var parseError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write to $characteristicName") },
        text = {
            Column {
                InputModeSelector(isHexMode = isHexMode, onModeChange = { isHexMode = it })

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        parseError = null
                    },
                    label = { Text(if (isHexMode) "Hex value (e.g., 01 FF A3)" else "Text value") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = parseError != null,
                    supportingText = parseError?.let { { Text(it) } },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )

                Spacer(modifier = Modifier.height(8.dp))
                WriteTypeSelector(writeType = writeType, onTypeChange = { writeType = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val data =
                    if (isHexMode) {
                        val parsed = ValueFormatter.parseHex(inputText)
                        if (parsed == null) {
                            parseError = "Invalid hex format"
                            return@TextButton
                        }
                        parsed
                    } else {
                        inputText.encodeToByteArray()
                    }
                onWrite(data, writeType)
            }) { Text("Write") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun InputModeSelector(
    isHexMode: Boolean,
    onModeChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = { onModeChange(true) }) {
            Text(
                "HEX",
                color =
                    if (isHexMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        TextButton(onClick = { onModeChange(false) }) {
            Text(
                "UTF-8",
                color =
                    if (!isHexMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun WriteTypeSelector(
    writeType: WriteType,
    onTypeChange: (WriteType) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Type:", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onTypeChange(WriteType.WithResponse) }) {
            Text(
                "With Response",
                color =
                    if (writeType == WriteType.WithResponse) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        TextButton(onClick = { onTypeChange(WriteType.WithoutResponse) }) {
            Text(
                "Without Response",
                color =
                    if (writeType == WriteType.WithoutResponse) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
