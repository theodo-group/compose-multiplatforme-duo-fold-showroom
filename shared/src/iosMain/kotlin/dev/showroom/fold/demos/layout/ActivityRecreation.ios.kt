package dev.showroom.fold.demos.layout

import androidx.compose.runtime.Composable

/** iOS never recreates the view controller on a rotation or a fold: there is nothing to trigger. */
@Composable
internal actual fun rememberActivityRecreator(): (() -> Unit)? = null
