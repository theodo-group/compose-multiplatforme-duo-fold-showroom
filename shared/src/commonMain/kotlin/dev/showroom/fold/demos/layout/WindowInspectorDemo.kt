package dev.showroom.fold.demos.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.BarEdge
import dev.showroom.fold.core.BarPlacement
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.DemoBottomInsets
import dev.showroom.fold.core.LocalBarPlacement
import dev.showroom.fold.core.Sharing
import dev.showroom.fold.fold.LocalFoldState
import kotlin.math.roundToInt

/** Material width breakpoints, in dp. */
private val Breakpoints = listOf(600, 840, 1200, 1600)

private fun widthClassName(widthDp: Float): String = when {
    widthDp < 600f -> "compact"
    widthDp < 840f -> "medium"
    widthDp < 1200f -> "expanded"
    widthDp < 1600f -> "large"
    else -> "extra-large"
}

private fun heightClassName(heightDp: Float): String = when {
    heightDp < 480f -> "compact"
    heightDp < 900f -> "medium"
    else -> "expanded"
}

private fun Float.fmt(): String = ((this * 10).roundToInt() / 10f).toString().removeSuffix(".0")

val windowInspectorDemo = Demo(
    id = "window-inspector",
    title = "Inspecteur de fenêtre",
    summary = "Tableau de bord en direct : taille, densité, classes de taille, insets et posture, mesurés depuis du code commun.",
    sharing = Sharing.Common,
    notes = listOf(
        "Tout est lu depuis commonMain : LocalWindowInfo (taille en px), LocalDensity, currentWindowAdaptiveInfoV2() et WindowInsets.*.",
        "Les insets sont lus via WindowInsets.statusBars.getTop(density) etc. : ces valeurs viennent du système et restent exactes même si l'hôte les a déjà consommés (la consommation n'affecte que les modifiers windowInsetsPadding).",
        "La posture de la bibliothèque adaptive vient de WindowManager sur Android ; sur iOS elle n'a pas de source de pliure et reste à plat. Notre LocalFoldState (expect/actual) permet de comparer les deux.",
        "Piège : la hauteur de fenêtre en mode multi-fenêtre ou avec le clavier ouvert n'est pas celle de l'écran ; les classes de taille suivent la fenêtre, jamais l'appareil.",
        "Les seuils (600 / 840 / 1200 / 1600 dp) sont les breakpoints Material ; le libellé « large » et « extra-large » est calculé ici à la main.",
    ),
    content = { WindowInspector() },
)

@Composable
private fun WindowInspector() {
    val density = LocalDensity.current
    val sizePx = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { sizePx.width.toDp() }.value
    val heightDp = with(density) { sizePx.height.toDp() }.value
    val adaptive = currentWindowAdaptiveInfoV2()
    val foldState = LocalFoldState.current
    val landscape = sizePx.width > sizePx.height

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(DemoBottomInsets).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val wClass = widthClassName(widthDp)
        Caption(
            "Largeur ${widthDp.fmt()} dp → $wClass, " + if (landscape) "paysage" else "portrait",
        )

        Section("Fenêtre") {
            Stat("Taille (dp)", "${widthDp.fmt()} × ${heightDp.fmt()}")
            Stat("Taille (px)", "${sizePx.width} × ${sizePx.height}")
            Stat("Densité", "${density.density.fmt()}x (police ${density.fontScale.fmt()}x)")
            Stat("Orientation", if (landscape) "Paysage" else "Portrait")
            Stat("Largeur", "$wClass (bibliothèque : ≥ ${adaptive.windowSizeClass.minWidthDp} dp)")
            Stat("Hauteur", "${heightClassName(heightDp)} (bibliothèque : ≥ ${adaptive.windowSizeClass.minHeightDp} dp)")
        }

        Section("Règle des breakpoints") { BreakpointRuler(widthDp) }

        Section("Insets système (valeurs réelles)") {
            val ld = LocalLayoutDirection.current
            Stat("Barre d'état", insetText(WindowInsets.statusBars, density, ld))
            Stat("Barre de navigation", insetText(WindowInsets.navigationBars, density, ld))
            Stat("Encoche / cutout", insetText(WindowInsets.displayCutout, density, ld))
            Stat("Clavier (IME)", insetText(WindowInsets.ime, density, ld))
            Stat("Safe drawing", insetText(WindowInsets.safeDrawing, density, ld))
            Stat("Barre de l'app", barPlacementText(LocalBarPlacement.current))
            Text(
                "La barre verticale n'est pas un WindowInsets : sur iPhone Duo elle vient de UITraitCollection.verticalBarEdge (iOS 27); sur Android elle reste toujours en haut (Material 3 : retour et actions dans la barre d'app du haut).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Posture : bibliothèque vs notre source") {
            val libPosture = adaptive.windowPosture
            val libText = when {
                libPosture.isTabletop -> "Chevalet"
                libPosture.hingeList.isNotEmpty() -> "Charnière (${libPosture.hingeList.size})"
                else -> "À plat / aucune"
            }
            Stat("material3-adaptive", libText)
            Stat("  charnières", libPosture.hingeList.size.toString())
            Stat("LocalFoldState", foldState.posture.label)
            Stat("  source", foldState.source.label)
            Stat("  charnière physique", if (foldState.hasHinge) "oui" else "non")
            Stat("  séparante", if (foldState.isSeparating) "oui" else "non")
            Stat("  angle", foldState.hingeAngleDegrees?.let { "${it.roundToInt()}°" } ?: "n/a")
        }
    }
}

private fun barPlacementText(placement: BarPlacement): String = when (placement) {
    BarPlacement.Top -> "en haut"
    is BarPlacement.Side -> when (placement.edge) {
        BarEdge.Left -> "latérale gauche (barre verticale système)"
        BarEdge.Right -> "latérale droite (barre verticale système)"
    }
}

private fun insetText(insets: WindowInsets, density: Density, ld: LayoutDirection): String {
    fun Int.dp() = with(density) { toDp() }.value.fmt()
    return "H ${insets.getTop(density).dp()} · B ${insets.getBottom(density).dp()} · " +
        "G ${insets.getLeft(density, ld).dp()} · D ${insets.getRight(density, ld).dp()} dp"
}

@Composable
private fun Caption(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A horizontal ruler from 0 to 1800 dp with the breakpoints and the current width. */
@Composable
private fun BreakpointRuler(widthDp: Float) {
    val max = 1800f
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
            val w = maxWidth
            fun x(dp: Float): Dp = w * (dp.coerceIn(0f, max) / max)
            // Track and filled part.
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).background(track, RoundedCornerShape(4.dp)))
            Box(Modifier.align(Alignment.CenterStart).width(x(widthDp)).height(8.dp).background(primary, RoundedCornerShape(4.dp)))
            Breakpoints.forEach { bp ->
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = x(bp.toFloat()) - 1.dp)
                        .width(2.dp).height(24.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Text(
                    bp.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomStart).offset(x = (x(bp.toFloat()) - 12.dp).coerceAtLeast(0.dp)),
                )
            }
            // Current width marker.
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = (x(widthDp) - 7.dp).coerceAtLeast(0.dp))
                    .width(14.dp).height(14.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(7.dp)),
            )
            Text(
                "${widthDp.fmt()} dp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(x = (x(widthDp) - 24.dp).coerceIn(0.dp, (w - 56.dp).coerceAtLeast(0.dp))),
            )
        }
        Text(
            "compact < 600 ≤ medium < 840 ≤ expanded < 1200 ≤ large < 1600 ≤ extra-large",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
