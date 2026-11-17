package dev.showroom.fold.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A physical screen edge, already resolved from the layout direction where the platform needs it. */
enum class BarEdge { Left, Right }

/**
 * Where the app's toolbar and navigation controls (Back, actions, tab bar) belong in the current
 * window.
 *
 * - [Top]: the usual horizontal top bar. Always on Android (Material 3 keeps Back and actions in the
 *   top app bar); iPhone Duo inner display in portrait; any other iPhone or iPad.
 * - [Side]: a vertical bar along [Side.edge], on the status bar edge: iPhone Duo outer display
 *   (closed) and inner display in landscape. In Split View each window gets its own outer edge (the
 *   left window's bar is on the left). Never mirrored for RTL.
 */
@Immutable
sealed interface BarPlacement {
    data object Top : BarPlacement

    /**
     * The app's controls share the iPhone Duo system vertical bar with the status bar, inside the
     * safe area the system reserves on that edge.
     *
     * @param edge the physical edge holding the bar.
     * @param itemsTop distance from the window top where app controls start, below the status bar
     *   that tops the vertical bar.
     */
    data class Side(
        val edge: BarEdge,
        val itemsTop: Dp = 0.dp,
    ) : BarPlacement
}

val BarPlacement.isSide: Boolean get() = this is BarPlacement.Side

/**
 * The bar placement of the current window, provided app-wide by [ProvideBarPlacement]. Screens that
 * draw their own bars (or a tab bar) read it to put their controls on the right edge.
 */
val LocalBarPlacement = compositionLocalOf<BarPlacement> { BarPlacement.Top }

/** Provides the platform [BarPlacement] as [LocalBarPlacement] to the whole app. */
@Composable
fun ProvideBarPlacement(content: @Composable () -> Unit) {
    val placement by rememberPlatformBarPlacement()
    CompositionLocalProvider(LocalBarPlacement provides placement, content = content)
}

/**
 * The platform source of [BarPlacement], reactive to rotation, fold / unfold and window resizing.
 * Android: always Top (Material 3 keeps Back and actions in the top app bar). iOS: the iPhone Duo vertical bar
 * trait (iOS 27.1+).
 */
@Composable
expect fun rememberPlatformBarPlacement(): State<BarPlacement>
