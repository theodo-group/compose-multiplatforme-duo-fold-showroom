package dev.showroom.fold.demos.hinge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.showroom.fold.fold.FoldState
import dev.showroom.fold.fold.HingeOrientation
import dev.showroom.fold.fold.Posture
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The hinge expressed in a layout's own coordinates.
 *
 * @param bounds the part of the hinge inside the layout, in local pixels (can have zero thickness).
 * @param crossesFully whether the hinge spans the whole layout across its axis. A hinge that only
 *   cuts part of the layout (e.g. the layout is offset or the window is not full screen) cannot be
 *   used to split it cleanly.
 */
@Immutable
data class LocalHinge(
    val bounds: Rect,
    val orientation: HingeOrientation,
    val crossesFully: Boolean,
)

/** Tolerance, in pixels, for rounding differences between the platform and Compose. */
private const val EDGE_TOLERANCE_PX = 2f

/** The platform's orientation when it gives one, else the hinge's longer side. */
internal fun FoldState.isHingeVertical(hinge: Rect): Boolean =
    orientation?.let { it == HingeOrientation.Vertical } ?: (hinge.height >= hinge.width)

/**
 * Converts [FoldState.hingeBounds] (window pixels) into the coordinates of a layout whose bounds in
 * the window are [layoutInWindow]. Returns null when the hinge does not touch the layout.
 */
fun FoldState.hingeIn(layoutInWindow: Rect): LocalHinge? {
    val hinge = hingeBounds ?: return null
    val vertical = isHingeVertical(hinge)
    val l = layoutInWindow
    val clipped = Rect(max(hinge.left, l.left), max(hinge.top, l.top), min(hinge.right, l.right), min(hinge.bottom, l.bottom))
    // A seamless hinge has zero thickness across its axis, but it must have some length along it.
    val across = if (vertical) clipped.width else clipped.height
    val along = if (vertical) clipped.height else clipped.width
    if (across < 0f || along <= 0f) return null
    val crossesFully = if (vertical) {
        hinge.top <= l.top + EDGE_TOLERANCE_PX && hinge.bottom >= l.bottom - EDGE_TOLERANCE_PX
    } else {
        hinge.left <= l.left + EDGE_TOLERANCE_PX && hinge.right >= l.right - EDGE_TOLERANCE_PX
    }
    return LocalHinge(
        bounds = clipped.translate(-l.left, -l.top),
        orientation = if (vertical) HingeOrientation.Vertical else HingeOrientation.Horizontal,
        crossesFully = crossesFully,
    )
}

/** Remembers the bounds of a layout in window pixels, the reference frame of hinge bounds; attach [modifier] to it. */
class WindowBoundsTracker {
    var bounds by mutableStateOf<Rect?>(null)
        private set
    val modifier: Modifier = Modifier.onGloballyPositioned { bounds = Rect(it.positionInWindow(), it.size.toSize()) }
}

@Composable
fun rememberWindowBoundsTracker(): WindowBoundsTracker = remember { WindowBoundsTracker() }

/** "Posture livre · charnière verticale": what the fold is doing, in one line. */
fun FoldState.postureSummary(): String = when (posture) {
    Posture.None -> if (hasHinge) "Charnière présente, hors de la fenêtre" else "Aucune charnière"
    Posture.Closed -> "Appareil fermé"
    Posture.Book -> "Posture livre · charnière verticale"
    Posture.Tabletop -> "Posture chevalet · charnière horizontale"
    Posture.Flat -> if (isSeparating) "À plat · pli séparant" else "À plat · pli non séparant"
}

fun HingeOrientation?.label(): String = when (this) {
    HingeOrientation.Vertical -> "verticale"
    HingeOrientation.Horizontal -> "horizontale"
    null -> "inconnue"
}

/** French decimal formatting without platform APIs: 12.345f.fmt(1) = "12,3". */
fun Float.fmt(decimals: Int = 1): String {
    if (decimals <= 0) return roundToInt().toString()
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(this) * factor).roundToLong()
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$whole,$fraction"
}

/** The line every demo shows on top: which layout is active and why. */
@Composable
fun LayoutStatusBar(title: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

/** " (zone de 28 dp laissée vide)" when the panes leave a gap for an occluding hinge. */
fun gapText(a: TwoPaneArrangement): String =
    if (a.hingeThicknessDp > 0) " (zone de ${a.hingeThicknessDp} dp laissée vide)" else ""

/** How to reach a posture with real hardware or the OS tools: the app never fakes it. */
internal const val HOW_TO_GET_A_FOLD =
    "iPhone Duo : pliez ou tournez l'appareil ; dans le simulateur iOS, utilisez les commandes de pliage et de rotation du Simulator. " +
        "Android : sur l'émulateur pliable, utilisez les commandes de pliage ou le capteur d'angle de charnière des contrôles étendus."

/** Where the only on-screen fold tweaks live. */
private const val GAUGE_TWEAKS_POINTER =
    "Pour tester une charnière occultante ou un autre angle dans la plage permise, ouvrez la démo « Jauge de charnière »."

/**
 * Says plainly that the device does not give the fold a demo is about, and how to get it with the
 * real device or the OS tools. The demo keeps its fallback layout below.
 */
@Composable
fun FoldHelpCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = HOW_TO_GET_A_FOLD,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = GAUGE_TWEAKS_POINTER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
