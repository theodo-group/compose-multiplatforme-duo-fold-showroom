package dev.showroom.fold

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point called from Swift (`MainViewControllerKt.MainViewController()`). */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
