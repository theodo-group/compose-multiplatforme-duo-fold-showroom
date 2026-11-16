package dev.showroom.fold.fold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import dev.showroom.fold.cinterop.SHHingeObserverView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.math.PI

private const val STATUS_CLOSED = 1L
private const val STATUS_PARTIALLY_OPEN = 2L
private const val STATUS_FULLY_OPEN = 3L

/**
 * iOS source of the fold: an invisible [SHHingeObserverView] placed behind the Compose content owns
 * a `UIHingeInteraction` and reads the fold "division" reserved region (iOS 27.1, iPhone Duo).
 * On older systems, and on devices without a hinge, the state stays [FoldState.None].
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPlatformFoldState(): State<FoldState> {
    val host = LocalUIViewController.current.view
    val density = rememberUpdatedState(LocalDensity.current.density)
    val state = remember { mutableStateOf(FoldState.None) }

    DisposableEffect(host) {
        var observer: SHHingeObserverView? = null
        observer = SHHingeObserverView(changeHandler = {
            observer?.let { state.value = it.toFoldState(density.value) }
        })
        observer.setFrame(host.bounds)
        // At the back, so it never sits over the content.
        host.insertSubview(observer, atIndex = 0)
        onDispose { observer.detach() }
    }
    return state
}

@OptIn(ExperimentalForeignApi::class)
private fun SHHingeObserverView.toFoldState(density: Float): FoldState {
    if (!hasHinge) return FoldState.None

    val orientation = when {
        !hasDivision -> null
        divisionHeight >= divisionWidth -> HingeOrientation.Vertical
        else -> HingeOrientation.Horizontal
    }
    val posture = when (hingeStatus) {
        STATUS_CLOSED -> Posture.Closed
        STATUS_PARTIALLY_OPEN -> if (orientation == HingeOrientation.Horizontal) Posture.Tabletop else Posture.Book
        STATUS_FULLY_OPEN -> Posture.Flat
        else -> Posture.None
    }
    // Compose measures in pixels; UIKit reports points.
    val bounds = if (hasDivision && posture != Posture.Closed) {
        Rect(
            left = (divisionX * density).toFloat(),
            top = (divisionY * density).toFloat(),
            right = ((divisionX + divisionWidth) * density).toFloat(),
            bottom = ((divisionY + divisionHeight) * density).toFloat(),
        )
    } else {
        null
    }
    return FoldState(
        hasHinge = true,
        posture = posture,
        hingeAngleDegrees = (hingeAngle * 180.0 / PI).toFloat(),
        hingeBounds = bounds,
        orientation = orientation,
        isSeparating = bounds != null && divisionActive,
        source = FoldSource.Platform,
    )
}
