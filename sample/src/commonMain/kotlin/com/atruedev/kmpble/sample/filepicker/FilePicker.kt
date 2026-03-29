package com.atruedev.kmpble.sample.filepicker

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePicker(onResult: (FilePickerResult?) -> Unit): () -> Unit
