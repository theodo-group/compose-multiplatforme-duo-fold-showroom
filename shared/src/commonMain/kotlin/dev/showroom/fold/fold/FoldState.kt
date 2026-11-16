package dev.showroom.fold.fold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect

enum class Posture(val label: String) {
    /** No hinge, or the platform does not report one. */
    None("Aucune pliure"),

    /** Folded shut: the app runs on the outer display. */
    Closed("Fermé"),

    /** Half-opened with a vertical hinge: two side-by-side halves, like a book. */
    Book("Livre"),

    /** Half-opened with a horizontal hinge: bottom half lies on the table. */
    Tabletop("Chevalet"),

    /** Fully opened. */
    Flat("À plat"),
}

enum class HingeOrientation { Vertical, Horizontal }

enum class FoldSource(val label: String) {
    None("aucune"),
    Platform("appareil"),

    /** The platform state with on-screen tweaks layered on top (hinge-gauge screen only). */
    Tweaked("appareil + réglages"),
}

/**
 * The platform-neutral description of the fold, consumed by shared UI.
 *
 * @param hasHinge whether the device has a hinge at all, even when the app does not span it.
 * @param hingeAngleDegrees 0 = shut, 180 = flat; null when the platform gives no angle.
 * @param hingeBounds the fold area in **window pixel coordinates**; null when the fold does not cross
 *   the window (closed device, or no hinge). It can have zero thickness.
 * @param isSeparating true when content should not be laid out across [hingeBounds].
 */
@Immutable
data class FoldState(
    val hasHinge: Boolean = false,
    val posture: Posture = Posture.None,
    val hingeAngleDegrees: Float? = null,
    val hingeBounds: Rect? = null,
    val orientation: HingeOrientation? = null,
    val isSeparating: Boolean = false,
    val source: FoldSource = FoldSource.None,
) {
    val isHalfOpened: Boolean get() = posture == Posture.Book || posture == Posture.Tabletop

    companion object {
        val None = FoldState()
    }
}

/**
 * The fold as seen by shared UI. App-wide it is exactly the platform state, provided by
 * [ProvideFoldState]; only the hinge-gauge screen re-provides a tweaked copy to its own subtree.
 * Demos should read this rather than [rememberPlatformFoldState].
 */
val LocalFoldState = compositionLocalOf { FoldState.None }

/** Provides the real platform [FoldState] as [LocalFoldState] to the whole app. */
@Composable
fun ProvideFoldState(content: @Composable () -> Unit) {
    val platform by rememberPlatformFoldState()
    CompositionLocalProvider(LocalFoldState provides platform, content = content)
}

/**
 * THE platform-specific part of fold support.
 *
 * - Android: Jetpack WindowManager `FoldingFeature` + the hinge-angle sensor.
 * - iOS: `UIHingeInteraction` + reserved regions (iOS 27.1, iPhone Duo), reached through a small
 *   Objective-C bridge. Inert on older systems.
 */
@Composable
expect fun rememberPlatformFoldState(): State<FoldState>
