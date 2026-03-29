package com.atruedev.kmpble.sample.dfu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.sample.filepicker.FilePickerResult
import com.atruedev.kmpble.sample.filepicker.rememberFilePicker

@Composable
internal fun DfuSection(
    dfuState: DfuState,
    onFileSelected: (FilePickerResult) -> Unit,
    onStartDfu: () -> Unit,
    onCancelDfu: () -> Unit,
    onReset: () -> Unit,
) {
    val launchPicker =
        rememberFilePicker { result ->
            if (result != null) onFileSelected(result)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Firmware Update", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            when (dfuState) {
                DfuState.Idle -> IdleContent(onSelectFile = launchPicker)
                is DfuState.Ready ->
                    ReadyContent(
                        firmware = dfuState.firmware,
                        onStart = onStartDfu,
                        onCancel = onReset,
                    )
                is DfuState.InProgress ->
                    ProgressContent(
                        percent = dfuState.percent,
                        onCancel = onCancelDfu,
                    )
                DfuState.Completed -> CompletedContent(onReset = onReset)
                is DfuState.Failed ->
                    FailedContent(
                        message = dfuState.message,
                        onRetry = launchPicker,
                        onReset = onReset,
                    )
            }
        }
    }
}

@Composable
private fun IdleContent(onSelectFile: () -> Unit) {
    OutlinedButton(onClick = onSelectFile) {
        Text("Select Firmware File")
    }
}

@Composable
private fun ReadyContent(
    firmware: FirmwareInfo,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        Text(firmware.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${firmware.type.name} - ${firmware.sizeBytes / 1024} KB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart) { Text("Start DFU") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun ProgressContent(
    percent: Int,
    onCancel: () -> Unit,
) {
    Column {
        Text("Uploading firmware... $percent%", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun CompletedContent(onReset: () -> Unit) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            "Firmware update completed successfully",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onReset) { Text("Done") }
    }
}

@Composable
private fun FailedContent(
    message: String,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    Column {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Try Again") }
            TextButton(onClick = onReset) { Text("Cancel") }
        }
    }
}
