package dev.showroom.fold.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.dp
import dev.showroom.fold.cinterop.SHVerticalBarObserverView
import kotlinx.cinterop.ExperimentalForeignApi

private const val EDGE_LEFT = 1L
private const val EDGE_RIGHT = 2L

/**
 * iOS source of the bar placement: an invisible [SHVerticalBarObserverView] behind the Compose
 * content reads `UITraitCollection.verticalBarEdge` (iOS 27.1, iPhone Duo) for this window, and the
 * system bar layout region on that edge (`UIViewLayoutRegion`) for where controls start below the
 * status bar. The system resolves the edge per window, so Split View windows each get their outer
 * edge. Before iOS 27.1, and on devices without a vertical bar, the placement stays
 * [BarPlacement.Top].
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPlatformBarPlacement(): State<BarPlacement> {
    val host = LocalUIViewController.current.view
    val state = remember { mutableStateOf<BarPlacement>(BarPlacement.Top) }

    DisposableEffect(host) {
        var observer: SHVerticalBarObserverView? = null
        observer = SHVerticalBarObserverView(changeHandler = {
            observer?.let { state.value = it.toBarPlacement() }
        })
        observer.setFrame(host.bounds)
        // At the back, so it never sits over the content.
        host.insertSubview(observer, atIndex = 0)
        state.value = observer.toBarPlacement()
        onDispose { observer.detach() }
    }
    return state
}

@OptIn(ExperimentalForeignApi::class)
private fun SHVerticalBarObserverView.toBarPlacement(): BarPlacement {
    val edge = when (barEdge) {
        EDGE_LEFT -> BarEdge.Left
        EDGE_RIGHT -> BarEdge.Right
        else -> return BarPlacement.Top
    }
    // UIKit points are Compose dp on iOS.
    return BarPlacement.Side(edge = edge, itemsTop = barItemsTop.dp)
}
