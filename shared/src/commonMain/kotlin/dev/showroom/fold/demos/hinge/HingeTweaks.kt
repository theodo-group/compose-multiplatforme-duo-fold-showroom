package dev.showroom.fold.demos.hinge

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.showroom.fold.fold.FoldSource
import dev.showroom.fold.fold.FoldState
import dev.showroom.fold.fold.HingeOrientation
import dev.showroom.fold.fold.Posture
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The two tweaks of the hinge-gauge screen, layered on the real FoldState. They never change
 * `posture` or `orientation`: those always come from the device or the OS simulator.
 */

/** Same thresholds as the Android emulator's foldable profiles. */
private const val CLOSED_BELOW = 30f
private const val FLAT_ABOVE = 150f

/** Thickness of the simulated occluding hinge. */
internal val OCCLUDING_HINGE_THICKNESS = 28.dp

/**
 * What the user asked for on the gauge screen.
 *
 * @param occluding turn the real fold into a 28 dp occluding band.
 * @param angleDegrees simulated hinge angle; null = follow the measured one.
 */
@Immutable
data class HingeTweaks(
    val occluding: Boolean = false,
    val angleDegrees: Float? = null,
)

/** Small state holder, remembered by the gauge screen only (gone when the screen leaves). */
@Stable
class HingeTweaksState {
    var occluding by mutableStateOf(false)
    var angleDegrees by mutableStateOf<Float?>(null)
    val value: HingeTweaks get() = HingeTweaks(occluding, angleDegrees)
}

/** Degrees the device's current state allows; null when there is no hinge or no known state. */
fun FoldState.allowedAngleRange(): ClosedFloatingPointRange<Float>? {
    if (!hasHinge) return null
    return when (posture) {
        Posture.Closed -> 0f..CLOSED_BELOW
        Posture.Book, Posture.Tabletop -> CLOSED_BELOW..FLAT_ABOVE
        Posture.Flat -> FLAT_ABOVE..180f
        Posture.None -> null
    }
}

fun ClosedFloatingPointRange<Float>.degreesLabel(): String =
    "${start.roundToInt()}–${endInclusive.roundToInt()}°"

private const val NO_HINGE = "l'appareil ne signale pas de charnière."

/** Why the occluding tweak cannot apply right now; null when it can. */
fun FoldState.occlusionBlocker(): String? = when {
    !hasHinge -> NO_HINGE
    hingeBounds == null && posture == Posture.Closed -> "appareil fermé, aucun pli ne traverse la fenêtre."
    hingeBounds == null -> "le pli ne traverse pas la fenêtre de l'app."
    else -> null
}

/** Why the angle tweak cannot apply right now; null when it can. */
fun FoldState.angleBlocker(): String? = when {
    !hasHinge -> NO_HINGE
    allowedAngleRange() == null -> "le système ne donne pas l'état de la charnière, donc aucune plage d'angle."
    else -> null
}

/**
 * The real state with [tweaks] applied. Each tweak is ignored when its blocker says it cannot apply;
 * the angle is clamped to [allowedAngleRange]. [FoldState.source] becomes [FoldSource.Tweaked] only
 * when something actually changed.
 */
fun FoldState.withTweaks(tweaks: HingeTweaks, occludingThicknessPx: Float): FoldState {
    var result = this
    val angle = tweaks.angleDegrees
    val range = allowedAngleRange()
    if (angle != null && angleBlocker() == null && range != null) {
        result = result.copy(hingeAngleDegrees = angle.coerceIn(range))
    }
    val hinge = hingeBounds
    if (tweaks.occluding && occlusionBlocker() == null && hinge != null) {
        val vertical = orientation?.let { it == HingeOrientation.Vertical } ?: (hinge.height >= hinge.width)
        // Never thinner than the real hinge.
        val half = max(occludingThicknessPx, if (vertical) hinge.width else hinge.height) / 2f
        val band = if (vertical) {
            Rect(hinge.center.x - half, hinge.top, hinge.center.x + half, hinge.bottom)
        } else {
            Rect(hinge.left, hinge.center.y - half, hinge.right, hinge.center.y + half)
        }
        result = result.copy(hingeBounds = band, isSeparating = true)
    }
    return if (result == this) this else result.copy(source = FoldSource.Tweaked)
}
