package dev.showroom.fold.demos.hinge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.showroom.fold.fold.FoldState
import dev.showroom.fold.fold.HingeOrientation
import dev.showroom.fold.fold.LocalFoldState
import kotlin.math.roundToInt

/** How [FoldAwareTwoPane] lays out its content. */
enum class TwoPaneMode {
    /** One pane fills the layout. */
    Single,

    /** Two panes side by side, split by fraction (no fold to align to). */
    Balanced,

    /** Vertical separating fold: first pane at the start of the hinge, second at the end. */
    FoldStartEnd,

    /** Horizontal separating fold: first pane above the hinge, second below. */
    FoldTopBottom,
}

/** Why [FoldAwareTwoPane] chose its mode; [label] is ready to show (French). */
enum class TwoPaneReason(val label: String) {
    SeparatingFold("Pli séparant"),
    NoFold("Pas de pli dans la fenêtre"),
    NotSeparating("Pli non séparant"),
    HingeOutside("Pli hors de cette zone"),
    HingePartial("Pli qui ne traverse qu'une partie de la zone"),
    PaneTooSmall("Pli trop près du bord"),
}

/**
 * The decision taken by [FoldAwareTwoPane], passed to its content and exposed through
 * [FoldAwareTwoPaneState] so a demo can say on screen which layout is active and why.
 *
 * @param hinge the hinge in the layout's local pixels, whether it was used or not.
 * @param hingeThicknessDp thickness of the area left empty between the panes (0 on a seamless fold).
 */
@Immutable
data class TwoPaneArrangement(
    val mode: TwoPaneMode,
    val reason: TwoPaneReason,
    val hinge: LocalHinge?,
    val widthDp: Int,
    val hingeThicknessDp: Int,
) {
    val isFoldSplit: Boolean get() = mode == TwoPaneMode.FoldStartEnd || mode == TwoPaneMode.FoldTopBottom
    val isTwoPane: Boolean get() = mode != TwoPaneMode.Single
}

/** Optional hoisted state: lets a caller read the current [TwoPaneArrangement] outside the panes. */
@Stable
class FoldAwareTwoPaneState {
    var arrangement by mutableStateOf<TwoPaneArrangement?>(null)
        internal set
}

@Composable
fun rememberFoldAwareTwoPaneState(): FoldAwareTwoPaneState = remember { FoldAwareTwoPaneState() }

/**
 * A two-pane layout that respects the fold, 100 % shared code.
 *
 * - Separating fold crossing the whole layout: the panes sit exactly on either side of the hinge
 *   (vertical hinge → start / end, horizontal → top / bottom). Nothing is placed under the hinge, so
 *   an occluding hinge never hides content.
 * - Otherwise: [singlePane] below [wideBreakpoint], a balanced side-by-side split at or above it.
 *
 * Hinge bounds come in window coordinates; the layout measures its own position in the window, so it
 * works when it does not fill the window (top bar above, a status line or hint card...).
 * A fold leaving less than [MIN_PANE_SIZE] on one side is ignored (fallback).
 */
@Composable
fun FoldAwareTwoPane(
    first: @Composable (TwoPaneArrangement) -> Unit,
    second: @Composable (TwoPaneArrangement) -> Unit,
    modifier: Modifier = Modifier,
    state: FoldAwareTwoPaneState? = null,
    wideBreakpoint: Dp = 600.dp,
    singlePane: @Composable (TwoPaneArrangement) -> Unit = first,
) {
    FoldAwareArrangement(modifier, state, wideBreakpoint) { arrangement ->
        if (arrangement.mode == TwoPaneMode.Single) {
            Box(Modifier.fillMaxSize()) { singlePane(arrangement) }
        } else {
            TwoPaneLayout(arrangement, first, second)
        }
    }
}

/**
 * The same fold-aware decision as [FoldAwareTwoPane], for content that places its panes itself
 * (with [paneRects]), for instance to animate them.
 */
@Composable
fun FoldAwareArrangement(
    modifier: Modifier = Modifier,
    state: FoldAwareTwoPaneState? = null,
    wideBreakpoint: Dp = 600.dp,
    content: @Composable (TwoPaneArrangement) -> Unit,
) {
    val fold = LocalFoldState.current
    val density = LocalDensity.current
    val tracker = rememberWindowBoundsTracker()

    BoxWithConstraints(modifier.then(tracker.modifier)) {
        val arrangement = arrange(
            fold = fold,
            layoutInWindow = tracker.bounds,
            widthPx = constraints.maxWidth,
            heightPx = constraints.maxHeight,
            densityValue = density.density,
            wideBreakpointPx = with(density) { wideBreakpoint.toPx() },
            minPanePx = with(density) { MIN_PANE_SIZE.toPx() },
        )
        if (state != null) SideEffect { state.arrangement = arrangement }
        content(arrangement)
    }
}

/**
 * The first and second pane rectangles of a two-pane [TwoPaneArrangement], in the local pixels of a
 * [width] x [height] layout: on either side of a separating hinge, else split in the middle. In RTL
 * the first (start) pane of a side-by-side split is the right one.
 */
fun TwoPaneArrangement.paneRects(width: Int, height: Int, layoutDirection: LayoutDirection): Pair<IntRect, IntRect> {
    val hinge = hinge?.bounds
    // Physical rectangles (left-to-right, top-to-bottom), in local pixels.
    val (a, b) = when (mode) {
        TwoPaneMode.FoldStartEnd -> {
            val left = hinge!!.left.roundToInt().coerceIn(0, width)
            val right = hinge.right.roundToInt().coerceIn(left, width)
            IntRect(0, 0, left, height) to IntRect(right, 0, width, height)
        }
        TwoPaneMode.FoldTopBottom -> {
            val top = hinge!!.top.roundToInt().coerceIn(0, height)
            val bottom = hinge.bottom.roundToInt().coerceIn(top, height)
            IntRect(0, 0, width, top) to IntRect(0, bottom, width, height)
        }
        else -> {
            val split = width / 2
            IntRect(0, 0, split, height) to IntRect(split, 0, width, height)
        }
    }
    val swap = layoutDirection == LayoutDirection.Rtl && mode != TwoPaneMode.FoldTopBottom
    return if (swap) b to a else a to b
}

@Composable
private fun TwoPaneLayout(
    arrangement: TwoPaneArrangement,
    first: @Composable (TwoPaneArrangement) -> Unit,
    second: @Composable (TwoPaneArrangement) -> Unit,
) {
    val aboveFold = arrangement.mode == TwoPaneMode.FoldTopBottom
    val bottomInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    Layout(
        contents = listOf(
            // Above a horizontal fold, the first pane never reaches the bottom safe area.
            { Box(Modifier.clipToBounds().then(if (aboveFold) Modifier.consumeWindowInsets(bottomInsets) else Modifier)) { first(arrangement) } },
            { Box(Modifier.clipToBounds()) { second(arrangement) } },
        ),
        modifier = Modifier.fillMaxSize(),
    ) { (firstMeasurables, secondMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val (firstRect, secondRect) = arrangement.paneRects(width, height, layoutDirection)
        val p1 = firstMeasurables.map { it.measure(Constraints.fixed(firstRect.width, firstRect.height)) }
        val p2 = secondMeasurables.map { it.measure(Constraints.fixed(secondRect.width, secondRect.height)) }
        layout(width, height) {
            // `place`, not `placeRelative`: positions are already physical.
            p1.forEach { it.place(firstRect.left, firstRect.top) }
            p2.forEach { it.place(secondRect.left, secondRect.top) }
        }
    }
}

private val MIN_PANE_SIZE = 120.dp

private fun arrange(
    fold: FoldState,
    layoutInWindow: Rect?,
    widthPx: Int,
    heightPx: Int,
    densityValue: Float,
    wideBreakpointPx: Float,
    minPanePx: Float,
): TwoPaneArrangement {
    val hinge = layoutInWindow?.let { fold.hingeIn(it) }

    fun result(mode: TwoPaneMode, reason: TwoPaneReason, hingeThicknessPx: Float = 0f) = TwoPaneArrangement(
        mode = mode,
        reason = reason,
        hinge = hinge,
        widthDp = (widthPx / densityValue).roundToInt(),
        hingeThicknessDp = (hingeThicknessPx / densityValue).roundToInt(),
    )

    val reason = when {
        fold.hingeBounds == null -> TwoPaneReason.NoFold
        hinge == null -> TwoPaneReason.HingeOutside
        !fold.isSeparating -> TwoPaneReason.NotSeparating
        !hinge.crossesFully -> TwoPaneReason.HingePartial
        else -> {
            val b = hinge.bounds
            val vertical = hinge.orientation == HingeOrientation.Vertical
            val before = if (vertical) b.left else b.top
            val after = if (vertical) widthPx - b.right else heightPx - b.bottom
            if (before >= minPanePx && after >= minPanePx) {
                return if (vertical) {
                    result(TwoPaneMode.FoldStartEnd, TwoPaneReason.SeparatingFold, hingeThicknessPx = b.width)
                } else {
                    result(TwoPaneMode.FoldTopBottom, TwoPaneReason.SeparatingFold, hingeThicknessPx = b.height)
                }
            }
            TwoPaneReason.PaneTooSmall
        }
    }
    return result(if (widthPx >= wideBreakpointPx) TwoPaneMode.Balanced else TwoPaneMode.Single, reason)
}
