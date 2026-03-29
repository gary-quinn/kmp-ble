package com.atruedev.kmpble.sample.filepicker

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(onResult: (FilePickerResult?) -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) {
                onResult(null)
                return@rememberLauncherForActivityResult
            }

            val fileName =
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "unknown"

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

            if (bytes != null) {
                onResult(FilePickerResult(name = fileName, bytes = bytes))
            } else {
                onResult(null)
            }
        }

    return remember(launcher) {
        { launcher.launch(arrayOf("*/*")) }
    }
}
