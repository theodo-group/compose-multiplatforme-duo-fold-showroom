package dev.showroom.fold.demos.hinge

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing
import dev.showroom.fold.fold.LocalFoldState
import dev.showroom.fold.fold.Posture

val bookModeDemo = Demo(
    id = "book-mode",
    title = "Mode livre",
    summary = "Une liseuse qui affiche une page fermée et deux pages face à face de part et d'autre du pli en posture livre.",
    sharing = Sharing.ExpectActual,
    notes = listOf(
        "Partagé : la liseuse et le composant réutilisable FoldAwareTwoPane (commonMain) qui place les volets exactement de chaque côté de la charnière.",
        "Spécifique : uniquement la source du FoldState (WindowManager sur Android, UIHingeInteraction + région réservée sur iOS 27.1).",
        "La charnière est donnée en coordonnées de la fenêtre : FoldAwareTwoPane mesure sa propre position (positionInWindow) et ignore un pli hors de sa zone, partiel ou trop près du bord.",
        "Ici le pli est toujours celui de l'appareil (ou de l'émulateur / du simulateur iOS) : l'app ne simule ni la posture ni l'orientation.",
        "Une charnière occultante laisse un vide : rien n'est dessiné dessous. Sur Pixel Fold et iPhone Duo le pli est sans couture, d'épaisseur nulle ; la « Jauge de charnière » permet d'essayer une bande occultante de 28 dp.",
        "Sans pli séparant, repli classique : une page en largeur compacte, deux pages équilibrées à partir de 600 dp.",
    ),
    content = { BookReaderScreen() },
)

private const val PAGE_COUNT = 14

@Composable
private fun BookReaderScreen() {
    val fold = LocalFoldState.current
    val paneState = rememberFoldAwareTwoPaneState()
    // Index of the first page shown (the left page of a spread in two-page mode).
    var firstPage by rememberSaveable { mutableIntStateOf(0) }

    val arrangement = paneState.arrangement
    val twoPages = arrangement?.isTwoPane == true
    val pagesPerView = if (twoPages) 2 else 1
    // A spread always starts on an even page so pages keep facing the same partner.
    val shownFirst = if (twoPages) firstPage - firstPage % 2 else firstPage
    val shownLast = (shownFirst + pagesPerView - 1).coerceAtMost(PAGE_COUNT - 1)
    fun previous() { firstPage = (shownFirst - pagesPerView).coerceAtLeast(0) }
    fun next() { if (shownFirst + pagesPerView < PAGE_COUNT) firstPage = shownFirst + pagesPerView }

    Column(Modifier.fillMaxSize()) {
        LayoutStatusBar(
            title = fold.postureSummary(),
            detail = arrangement?.let { readerLayoutText(it, fold.posture) } ?: "Mesure de la mise en page…",
        )
        if (!fold.hasHinge) {
            FoldHelpCard(
                title = "Aucune charnière détectée",
                message = "Cet appareil ne signale pas de pli : la liseuse montre son repli (1 page en largeur compacte, 2 pages équilibrées à partir de 600 dp). Les pages face à face demandent une vraie posture livre : charnière verticale, appareil entrouvert.",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else if (arrangement?.isFoldSplit == false && fold.posture != Posture.Book) {
            Text(
                text = "Posture actuelle : ${fold.posture.label.lowercase()}. Entrouvrez l'appareil (~110°), charnière verticale, pour passer en posture livre (sur émulateur ou simulateur : commandes de pliage).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        FoldAwareTwoPane(
            state = paneState,
            modifier = Modifier.weight(1f),
            singlePane = { a ->
                BookPage(index = shownFirst, side = PageSide.Single, arrangement = a, onTap = { forward -> if (forward) next() else previous() })
            },
            first = { a ->
                BookPage(index = shownFirst, side = PageSide.First, arrangement = a, onTap = { previous() })
            },
            second = { a ->
                if (shownFirst + 1 < PAGE_COUNT) {
                    BookPage(index = shownFirst + 1, side = PageSide.Second, arrangement = a, onTap = { next() })
                } else {
                    EndOfBook(a)
                }
            },
        )

        ReaderNavigation(
            label = if (shownLast > shownFirst) "Pages ${shownFirst + 1}–${shownLast + 1} sur $PAGE_COUNT" else "Page ${shownFirst + 1} sur $PAGE_COUNT",
            progress = (shownLast + 1f) / PAGE_COUNT,
            canGoBack = shownFirst > 0,
            canGoForward = shownFirst + pagesPerView < PAGE_COUNT,
            onBack = ::previous,
            onForward = ::next,
        )
    }
}

private fun readerLayoutText(a: TwoPaneArrangement, posture: Posture): String = when (a.mode) {
    TwoPaneMode.FoldStartEnd ->
        "Charnière verticale séparante → 2 pages face à face, de part et d'autre du pli" + gapText(a)
    TwoPaneMode.FoldTopBottom ->
        "Charnière horizontale séparante → page du haut et page du bas, de part et d'autre du pli" + gapText(a)
    TwoPaneMode.Balanced ->
        "${a.reason.label.replaceFirstChar { it.uppercase() }} · ${a.widthDp} dp ≥ 600 dp → 2 pages équilibrées"
    TwoPaneMode.Single -> {
        val why = if (posture == Posture.Closed) "Écran fermé" else a.reason.label.replaceFirstChar { it.uppercase() }
        "$why · ${a.widthDp} dp < 600 dp → 1 page"
    }
}

private enum class PageSide { Single, First, Second }

private val PAGE_TITLES = listOf(
    "I. La charnière", "II. Deux moitiés", "III. Le pli", "IV. La table", "V. Le livre ouvert",
    "VI. Le couvercle", "VII. Le partage",
)

private val PARAGRAPHS = listOf(
    "Il y avait, dans l'atelier de l'horloger, un objet qui ne ressemblait à rien de connu : une plaque de verre si fine qu'on aurait juré une feuille, et qui pourtant se pliait sans jamais se rompre.",
    "Les apprentis l'ouvraient comme on ouvre un livre. À mi-course, l'image se scindait d'elle-même en deux pages, et chaque page savait exactement où s'arrêter, juste au bord du pli.",
    "Posée sur l'établi, la plaque devenait un pupitre : la moitié basse portait les boutons, la moitié haute montrait le cadran. Personne n'avait besoin de la tenir.",
    "L'horloger disait que le secret n'était pas dans le verre, mais dans la façon dont chaque moitié écoutait l'autre. Un seul mécanisme, disait-il, pour deux royaumes.",
    "Quand on la refermait, il ne restait qu'une petite fenêtre sur le dessus, étroite comme une lettre, et tout ce qui comptait s'y rangeait sans rien perdre.",
    "On venait de loin pour voir la plaque. Certains voulaient savoir comment elle était faite ; d'autres, plus sages, demandaient seulement ce qu'on pourrait y écrire.",
)

@Composable
private fun BookPage(index: Int, side: PageSide, arrangement: TwoPaneArrangement, onTap: (forward: Boolean) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminanceApprox() < 0.5f
    val paper = if (dark) Color(0xFF2A2620) else Color(0xFFFFF8EC)
    val ink = if (dark) Color(0xFFEDE3D2) else Color(0xFF2B2418)
    val gutter = Color.Black.copy(alpha = if (dark) 0.45f else 0.16f)

    Surface(
        color = paper,
        contentColor = ink,
        modifier = Modifier
            .fillMaxSize()
            .foldGutter(side, arrangement, gutter)
            .pointerInput(side) {
                detectTapGestures { position ->
                    // Facing pages: left page goes back, right page forward.
                    // Single page: the first third goes back, the rest goes forward.
                    onTap(
                        when (side) {
                            PageSide.First -> false
                            PageSide.Second -> true
                            PageSide.Single -> position.x > size.width / 3f
                        },
                    )
                }
            },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (index == 0) "Le verre qui se pliait" else PAGE_TITLES[(index / 2) % PAGE_TITLES.size],
                style = if (index == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontStyle = if (index == 0) FontStyle.Normal else FontStyle.Italic,
            )
            repeat(3) { i ->
                Text(
                    text = PARAGRAPHS[(index * 2 + i) % PARAGRAPHS.size],
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Justify,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "— ${index + 1} —",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Rough luminance, enough to pick paper colours in light and dark themes. */
private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Subtle fold indicator: a soft shadow on the edge of each page that touches the hinge (the
 * "gutter" of a real book). A thin rule for a balanced split, nothing for a single page.
 */
private fun Modifier.foldGutter(side: PageSide, arrangement: TwoPaneArrangement, shadow: Color): Modifier =
    drawWithContent {
        drawContent()
        if (side == PageSide.Single) return@drawWithContent
        val depth = 28.dp.toPx()
        val clear = Color.Transparent
        when (arrangement.mode) {
            TwoPaneMode.FoldStartEnd -> {
                // Physical side of the hinge: first pane is on the left in LTR.
                val hingeOnRight = (side == PageSide.First) == (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr)
                if (hingeOnRight) {
                    drawRect(Brush.horizontalGradient(listOf(clear, shadow), startX = size.width - depth, endX = size.width))
                } else {
                    drawRect(Brush.horizontalGradient(listOf(shadow, clear), startX = 0f, endX = depth))
                }
            }
            TwoPaneMode.FoldTopBottom -> {
                if (side == PageSide.First) {
                    drawRect(Brush.verticalGradient(listOf(clear, shadow), startY = size.height - depth, endY = size.height))
                } else {
                    drawRect(Brush.verticalGradient(listOf(shadow, clear), startY = 0f, endY = depth))
                }
            }
            TwoPaneMode.Balanced -> {
                val x = if ((side == PageSide.First) == (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr)) size.width else 0f
                drawLine(shadow, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            TwoPaneMode.Single -> Unit
        }
    }

@Composable
private fun EndOfBook(arrangement: TwoPaneArrangement) {
    Box(
        Modifier.fillMaxSize().foldGutter(PageSide.Second, arrangement, Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Fin", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
    }
}

@Composable
private fun ReaderNavigation(
    label: String,
    progress: Float,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                FilledTonalIconButton(onClick = onBack, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Page précédente")
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = onForward, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Page suivante")
                }
            }
        }
    }
}
