package dev.showroom.fold.demos.layout

import androidx.compose.runtime.Composable

/**
 * An action that destroys and recreates the host activity, exactly as Android does on a rotation or a
 * fold when the manifest does not declare `configChanges`. Null where the concept does not exist (iOS).
 *
 * A demonstration aid only: the state continuity shown on the screen is 100 % shared code.
 */
@Composable
internal expect fun rememberActivityRecreator(): (() -> Unit)?
