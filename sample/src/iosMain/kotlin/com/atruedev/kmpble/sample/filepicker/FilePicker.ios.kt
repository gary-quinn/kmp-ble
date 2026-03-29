package com.atruedev.kmpble.sample.filepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(onResult: (FilePickerResult?) -> Unit): () -> Unit {
    return remember {
        {
            val picker =
                UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(UTTypeData),
                )

            val delegate =
                object : NSObject(), UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>,
                    ) {
                        val url = didPickDocumentsAtURLs.firstOrNull() as? platform.Foundation.NSURL
                        if (url == null) {
                            onResult(null)
                            return
                        }

                        val accessing = url.startAccessingSecurityScopedResource()
                        try {
                            val data = NSData.dataWithContentsOfURL(url)
                            if (data == null) {
                                onResult(null)
                                return
                            }

                            val fileName = url.lastPathComponent ?: "unknown"
                            val bytes =
                                ByteArray(data.length.toInt()).also { byteArray ->
                                    byteArray.usePinned { pinned ->
                                        memcpy(pinned.addressOf(0), data.bytes, data.length)
                                    }
                                }

                            onResult(FilePickerResult(name = fileName, bytes = bytes))
                        } finally {
                            if (accessing) url.stopAccessingSecurityScopedResource()
                        }
                    }

                    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                        onResult(null)
                    }
                }

            picker.delegate = delegate
            picker.allowsMultipleSelection = false

            UIApplication.sharedApplication.keyWindow
                ?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}
