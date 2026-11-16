package dev.showroom.fold.demos.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.showroom.fold.fold.findActivity

@Composable
internal actual fun rememberActivityRecreator(): (() -> Unit)? {
    val activity = LocalContext.current.findActivity()
    return remember(activity) { activity?.let { { it.recreate() } } }
}
