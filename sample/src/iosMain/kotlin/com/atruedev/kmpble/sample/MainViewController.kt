package com.atruedev.kmpble.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point. Use from SwiftUI:
 *
 * ```swift
 * import KmpBleSample
 *
 * struct ContentView: UIViewControllerRepresentable {
 *     func makeUIViewController(context: Context) -> UIViewController {
 *         MainViewControllerKt.mainViewController()
 *     }
 *     func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
 * }
 * ```
 */
fun mainViewController(): UIViewController = ComposeUIViewController { App() }
