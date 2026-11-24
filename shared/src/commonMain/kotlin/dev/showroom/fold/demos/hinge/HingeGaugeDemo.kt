package dev.showroom.fold.demos.hinge

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.DemoBottomInsets
import dev.showroom.fold.core.LocalBarPlacement
import dev.showroom.fold.core.Sharing
import dev.showroom.fold.core.isSide
import dev.showroom.fold.fold.FoldSource
import dev.showroom.fold.fold.FoldState
import dev.showroom.fold.fold.HingeOrientation
import dev.showroom.fold.fold.LocalFoldState
import dev.showroom.fold.fold.Posture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.TimeSource

val hingeGaugeDemo = Demo(
    id = "hinge-gauge",
    title = "Jauge de charnière",
    summary = "La posture, l'angle et la géométrie du pli en direct, lus depuis un FoldState commun aux deux plateformes.",
    sharing = Sharing.ExpectActual,
    notes = listOf(
        "Partagé : tout l'écran (schéma animé, mesures, historique, mise en page) est dans commonMain et ne lit que LocalFoldState.",
        "Spécifique : la source du FoldState. Android combine Jetpack WindowManager (FoldingFeature) et le capteur d'angle de charnière ; iOS 27.1 lit UIHingeInteraction et la région réservée « division » via un petit pont Objective-C.",
        "Les limites de la charnière arrivent en pixels de la fenêtre : il faut les convertir dans le repère de la vue (positionInWindow), sinon tout est décalé de la barre de l'app (en haut, ou sur le côté quand elle devient verticale : iPhone Duo fermé ou en paysage).",
        "Pixel Fold et iPhone Duo ont un pli sans couture : la charnière a souvent une épaisseur nulle. Sans capteur d'angle, l'angle affiché est une estimation tirée de la posture.",
        "Posture et orientation viennent toujours de l'appareil (ou de l'émulateur Android / du simulateur iOS) : l'app ne les simule jamais.",
        "Seul cet écran propose deux réglages par-dessus l'état réel : charnière occultante (bande de 28 dp centrée sur le vrai pli) et angle, limité à la plage de l'état actuel (fermé 0–30°, entrouvert 30–150°, à plat 150–180°). Ils ne s'appliquent qu'ici et disparaissent en quittant l'écran.",
        "L'historique ne couvre que le temps passé sur cet écran.",
    ),
    content = { HingeGaugeScreen() },
)

@Immutable
private data class PostureEvent(
    val posture: Posture,
    val source: FoldSource,
    val angle: Float?,
    val atSeconds: Float,
)

private const val MAX_EVENTS = 8

@Composable
private fun HingeGaugeScreen() {
    val real = LocalFoldState.current
    // Local to this screen: leaving it drops the tweaks, nothing leaks to other demos.
    val tweaks = remember { HingeTweaksState() }
    val range = real.allowedAngleRange()
    // The device moved to another state: a simulated angle from the old range no longer makes sense.
    LaunchedEffect(range) {
        val angle = tweaks.angleDegrees
        if (angle != null && (range == null || angle !in range)) tweaks.angleDegrees = null
    }
    val thicknessPx = with(LocalDensity.current) { OCCLUDING_HINGE_THICKNESS.toPx() }
    val shown = real.withTweaks(tweaks.value, thicknessPx)

    // Everything below (FoldAwareTwoPane included) sees the tweaked state.
    CompositionLocalProvider(LocalFoldState provides shown) {
        HingeGaugeContent(real = real, fold = shown, tweaks = tweaks)
    }
}

@Composable
private fun HingeGaugeContent(real: FoldState, fold: FoldState, tweaks: HingeTweaksState) {
    val rootTracker = rememberWindowBoundsTracker()
    val paneState = rememberFoldAwareTwoPaneState()
    val angleTweaked = tweaks.angleDegrees != null && real.angleBlocker() == null

    // Short timeline of posture changes, newest first.
    val start = remember { TimeSource.Monotonic.markNow() }
    val events = remember { mutableStateListOf<PostureEvent>() }
    LaunchedEffect(fold.posture, fold.source) {
        val seconds = start.elapsedNow().inWholeMilliseconds / 1000f
        events.add(0, PostureEvent(fold.posture, fold.source, fold.hingeAngleDegrees, seconds))
        while (events.size > MAX_EVENTS) events.removeAt(events.lastIndex)
    }

    Column(Modifier.fillMaxSize().then(rootTracker.modifier)) {
        val arrangement = paneState.arrangement
        LayoutStatusBar(
            title = fold.postureSummary() + if (fold.source == FoldSource.Tweaked) " · réglages actifs" else "",
            detail = arrangement?.let { gaugeLayoutText(it) } ?: "Mesure de la mise en page…",
        )
        FoldAwareTwoPane(
            state = paneState,
            modifier = Modifier.weight(1f),
            singlePane = {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(DemoBottomInsets).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TweaksCard(real, tweaks)
                    GaugeCard(real, fold, angleTweaked, Modifier.fillMaxWidth().height(300.dp))
                    Measurements(real, fold, angleTweaked, rootTracker.bounds)
                    Timeline(events)
                }
            },
            first = { GaugeCard(real, fold, angleTweaked, Modifier.fillMaxSize().windowInsetsPadding(DemoBottomInsets).padding(16.dp)) },
            second = {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(DemoBottomInsets).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TweaksCard(real, tweaks)
                    Measurements(real, fold, angleTweaked, rootTracker.bounds)
                    Timeline(events)
                }
            },
        )
    }
}

private fun gaugeLayoutText(a: TwoPaneArrangement): String = when (a.mode) {
    TwoPaneMode.FoldStartEnd -> "Pli vertical séparant → schéma d'un côté, mesures de l'autre" + gapText(a)
    TwoPaneMode.FoldTopBottom -> "Pli horizontal séparant → schéma au-dessus, mesures en dessous" + gapText(a)
    TwoPaneMode.Balanced -> "${a.reason.label} · ${a.widthDp} dp de large → 2 colonnes"
    TwoPaneMode.Single -> "${a.reason.label} · ${a.widthDp} dp de large → 1 colonne"
}

/**
 * The two tweaks. Each is disabled with a one-line reason when the real state does not allow it;
 * posture and orientation are never offered.
 */
@Composable
private fun TweaksCard(real: FoldState, tweaks: HingeTweaksState) {
    val occlusionBlocker = real.occlusionBlocker()
    val angleBlocker = real.angleBlocker()
    val range = real.allowedAngleRange()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Réglages simulés · cet écran uniquement", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Posture et orientation viennent de l'appareil ou du simulateur du système. Ces réglages s'ajoutent par-dessus, ici seulement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TweakSwitch(
                title = "Charnière occultante",
                detail = occlusionBlocker?.let { "Indisponible : $it" }
                    ?: "Bande de ${OCCLUDING_HINGE_THICKNESS.value.roundToInt()} dp centrée sur le vrai pli, séparante.",
                checked = tweaks.occluding && occlusionBlocker == null,
                enabled = occlusionBlocker == null,
                onCheckedChange = { tweaks.occluding = it },
            )
            TweakSwitch(
                title = "Angle simulé",
                detail = angleBlocker?.let { "Indisponible : $it" }
                    ?: "Plage permise par l'état actuel (${real.posture.label.lowercase()}) : ${range!!.degreesLabel()}.",
                checked = tweaks.angleDegrees != null && angleBlocker == null,
                enabled = angleBlocker == null,
                onCheckedChange = { on ->
                    tweaks.angleDegrees = if (on && range != null) real.displayAngle().first.coerceIn(range) else null
                },
            )
            val angle = tweaks.angleDegrees
            if (angle != null && range != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${angle.coerceIn(range).roundToInt()}°",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(48.dp),
                    )
                    Slider(
                        value = angle.coerceIn(range),
                        onValueChange = { tweaks.angleDegrees = it },
                        valueRange = range,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!real.hasHinge) {
                Text(
                    text = HOW_TO_GET_A_FOLD,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TweakSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Angle to draw: the real one, or an estimate from the posture when the platform gives none. */
private fun FoldState.displayAngle(): Pair<Float, Boolean> {
    hingeAngleDegrees?.let { return it.coerceIn(0f, 180f) to false }
    val estimate = when (posture) {
        Posture.Closed -> 0f
        Posture.Book, Posture.Tabletop -> 90f
        Posture.Flat, Posture.None -> 180f
    }
    return estimate to true
}

@Composable
private fun GaugeCard(real: FoldState, fold: FoldState, angleTweaked: Boolean, modifier: Modifier) {
    val (target, estimated) = fold.displayAngle()
    val angle by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
    )
    ElevatedCard(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${angle.roundToInt()}°",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(fold.posture.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            angleTweaked -> "angle simulé · mesuré : " +
                                (real.hingeAngleDegrees?.let { "${it.roundToInt()}°" } ?: "non fourni")
                            estimated -> "angle estimé depuis la posture"
                            else -> "angle mesuré"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                HingeSideView(
                    angleDegrees = angle,
                    onTable = fold.posture == Posture.Tabletop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = when (fold.orientation) {
                    HingeOrientation.Vertical -> "Vue de dessus : la charnière est verticale"
                    HingeOrientation.Horizontal -> "Vue de profil : la moitié basse repose sur la table"
                    null -> "Vue schématique de la charnière"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Side view of the device: a fixed half lying to the left of the hinge and a moving half rotated by
 * the hinge angle (0° = shut on top of it, 180° = flat). Inner faces (the screen) in primary.
 */
@Composable
private fun HingeSideView(angleDegrees: Float, onTable: Boolean, modifier: Modifier) {
    val body = MaterialTheme.colorScheme.onSurfaceVariant
    val screen = MaterialTheme.colorScheme.primary
    val arc = MaterialTheme.colorScheme.tertiary
    val ground = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val thickness = 12.dp.toPx()
        val length = min(size.width * 0.42f, size.height * 0.8f)
        val pivot = Offset(size.width / 2f + length * 0.1f, size.height * 0.5f + length * 0.4f)

        if (onTable) {
            val y = pivot.y + thickness / 2f + 2.dp.toPx()
            drawLine(ground, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        }

        // Fixed half, pointing left.
        val baseEnd = Offset(pivot.x - length, pivot.y)
        drawHalf(pivot, baseEnd, inner = Offset(0f, -1f), body, screen, thickness)

        // Moving half, at `angle` from the fixed one.
        val theta = angleDegrees * PI.toFloat() / 180f
        val dir = Offset(-cos(theta), -sin(theta))
        val movingEnd = pivot + dir * length
        drawHalf(pivot, movingEnd, inner = Offset(dir.y, -dir.x), body, screen, thickness)

        // Angle arc between the two halves.
        val r = length * 0.32f
        drawArc(
            color = arc,
            startAngle = 180f,
            sweepAngle = angleDegrees,
            useCenter = false,
            topLeft = pivot - Offset(r, r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
        )
        drawCircle(arc, radius = thickness * 0.55f, center = pivot)
    }
}

private fun DrawScope.drawHalf(from: Offset, to: Offset, inner: Offset, body: Color, screen: Color, thickness: Float) {
    drawLine(body, from, to, strokeWidth = thickness, cap = StrokeCap.Round)
    val shift = inner * (thickness / 2f)
    // Screen face, slightly shorter than the body.
    val along = to - from
    drawLine(screen, from + shift + along * 0.04f, to + shift - along * 0.04f, strokeWidth = thickness * 0.35f, cap = StrokeCap.Round)
}

@Composable
private fun Measurements(real: FoldState, fold: FoldState, angleTweaked: Boolean, contentInWindow: Rect?) {
    val density = LocalDensity.current.density
    val local = contentInWindow?.let { fold.hingeIn(it) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mesures", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            InfoRow("Posture", fold.posture.label)
            InfoRow("Angle mesuré", real.hingeAngleDegrees?.let { "${it.fmt(1)}°" } ?: "non fourni par la plateforme")
            InfoRow(
                "Angle simulé",
                if (angleTweaked) fold.hingeAngleDegrees?.let { "${it.fmt(1)}° (réglage)" } ?: "—" else "— (suit l'appareil)",
            )
            InfoRow("Orientation", fold.orientation.label())
            InfoRow(
                "Séparant (isSeparating)",
                realVsTweaked(if (real.isSeparating) "oui" else "non", if (fold.isSeparating) "oui" else "non"),
            )
            InfoRow("Source", fold.source.label)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            InfoRow("Charnière (fenêtre)", real.hingeBounds?.describe(density) ?: "aucune")
            if (fold.hingeBounds != real.hingeBounds) {
                InfoRow("Bande occultante (réglage)", fold.hingeBounds?.describe(density) ?: "aucune")
            }
            InfoRow(
                "Charnière (cette vue)",
                when {
                    fold.hingeBounds == null -> "—"
                    local == null -> "hors de la vue"
                    else -> local.bounds.describe(density) + if (local.crossesFully) "" else " · partielle"
                },
            )
            Spacer(Modifier.height(4.dp))
            WindowMiniMap(real, fold, contentInWindow)
        }
    }
}

/** "non → oui (réglage)" when a tweak changed the value, the real value alone otherwise. */
private fun realVsTweaked(real: String, shown: String): String =
    if (real == shown) real else "$real → $shown (réglage)"

private fun Rect.describe(density: Float): String {
    return "x ${left.roundToInt()} · y ${top.roundToInt()} px · ${(width / density).fmt(0)} × ${(height / density).fmt(0)} dp"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 120.dp, max = 180.dp).padding(end = 12.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}

/** The window to scale: where this screen sits in it, the real hinge and the simulated band, if any. */
@Composable
private fun WindowMiniMap(real: FoldState, fold: FoldState, contentInWindow: Rect?) {
    val window = LocalWindowInfo.current.containerSize
    if (window.width <= 0 || window.height <= 0) return
    val outline = MaterialTheme.colorScheme.outline
    val contentColor = MaterialTheme.colorScheme.secondaryContainer
    val hingeColor = MaterialTheme.colorScheme.tertiary
    val bandColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    val band = fold.hingeBounds?.takeIf { it != real.hingeBounds }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .width(if (window.width > window.height) 140.dp else 90.dp)
                .aspectRatio(window.width.toFloat() / window.height),
        ) {
            val scale = size.width / window.width
            drawRoundRect(outline, style = Stroke(1.5.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx()))
            contentInWindow?.let {
                drawRect(contentColor, topLeft = it.topLeft * scale, size = it.size * scale)
            }
            fun drawHinge(h: Rect, color: Color) {
                val minLine = 2.dp.toPx()
                val vertical = real.isHingeVertical(h)
                val w = max(h.width * scale, if (vertical) minLine else 0f)
                val hh = max(h.height * scale, if (vertical) 0f else minLine)
                val cx = h.center.x * scale
                val cy = h.center.y * scale
                drawRect(color, topLeft = Offset(cx - w / 2f, cy - hh / 2f), size = Size(w, hh))
            }
            band?.let { drawHinge(it, bandColor) }
            real.hingeBounds?.let { drawHinge(it, hingeColor) }
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend(outline, "fenêtre")
            Legend(contentColor, if (LocalBarPlacement.current.isSide) "cet écran (à côté de la barre)" else "cet écran (sous la barre)")
            Legend(hingeColor, "charnière réelle")
            if (band != null) Legend(bandColor, "bande occultante (réglage)")
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Timeline(events: List<PostureEvent>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Derniers changements de posture", style = MaterialTheme.typography.titleMedium)
            if (events.isEmpty()) {
                Text("Aucun pour l'instant.", style = MaterialTheme.typography.bodyMedium)
            }
            events.forEachIndexed { index, event ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                        modifier = Modifier.size(10.dp),
                    ) {}
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "+${event.atSeconds.fmt(1)} s",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        text = buildString {
                            append(event.posture.label)
                            event.angle?.let { append(" · ${it.roundToInt()}°") }
                            append(" · ${event.source.label}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
