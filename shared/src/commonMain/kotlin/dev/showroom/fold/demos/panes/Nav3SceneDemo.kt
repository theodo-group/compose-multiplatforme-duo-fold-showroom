package dev.showroom.fold.demos.panes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing
import dev.showroom.fold.core.LocalBarPlacement
import dev.showroom.fold.core.isSide
import dev.showroom.fold.core.rememberSystemBack
import dev.showroom.fold.demos.hinge.FoldAwareArrangement
import dev.showroom.fold.demos.hinge.TwoPaneArrangement
import dev.showroom.fold.demos.hinge.paneRects
import dev.showroom.fold.fold.LocalFoldState
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

val nav3SceneDemo = Demo(
    id = "nav3-scenes",
    title = "Navigation 3 : scènes",
    summary = "Une même pile de navigation affichée en un ou deux panneaux selon la largeur, grâce à une Scene personnalisée.",
    sharing = Sharing.Common,
    notes = listOf(
        "Navigation 3 (org.jetbrains.androidx.navigation3 1.1.2) compile et fonctionne depuis commonMain pour Android et iOS : NavDisplay, NavEntry et un SceneStrategy maison.",
        "La pile appartient à la démo (mutableStateListOf de clés Screen) ; la scène choisit seulement combien d'entrées afficher : 2 si la fenêtre fait au moins 600 dp ou qu'un pli séparant la coupe, 1 sinon.",
        "Pli : la scène à deux panneaux place ses panneaux avec la décision de FoldAwareTwoPane (FoldAwareArrangement + paneRects), donc chaque entrée se cale d'un côté du pli (moitié / moitié sans pli), jamais à cheval dessus.",
        "Perdu faute de plugin : rememberNavBackStack (restauration après mort du processus) exige des clés NavKey @Serializable et un SerializersModule polymorphique hors Android, donc le plugin kotlinx-serialization et sa dépendance, absents du projet.",
        "Retour : NavDisplay installe son propre handler (NavigationEvent) quand la pile a plus d'une entrée ; il passe avant celui de l'hôte et dépile, la démo ne quitte qu'à la racine. Une flèche « Dépiler » est ajoutée avec la barre du haut ; iPhone Duo en barre verticale : c'est le Retour en haut de cette barre qui dépile, la ligne ne garde que la pile.",
        "Gotcha : NavDisplay n'anime qu'un changement de clé de scène, et anime alors toute la scène (sur iOS : glissement « push » sous un voile gris). Pendant cette transition, les deux scènes sont à l'écran mais une entrée commune n'est dessinée que dans l'une : l'autre est trouée, ou les deux se superposent (iOS comme Android). Toutes les scènes à deux panneaux ont donc la même clé, et la scène anime elle-même ses panneaux : ouvrir un article lié (ou revenir) fait glisser les panneaux d'un cran, comme un carrousel, chaque entrée dessinée une seule fois (key(contentKey)) ; choisir dans la liste remplace le détail sur place, sans couper le ripple.",
        "Depuis la liste, un article remplace le détail au lieu de s'empiler : la liste reste à gauche. L'état de défilement d'une entrée suit le contentKey de son entrée.",
    ),
    content = { Nav3SceneContent() },
)

/** Back stack keys. Plain classes: no serialization needed with a non-saveable stack. */
private sealed interface Screen {
    data object Home : Screen
    data class Detail(val articleId: Int) : Screen
}

private const val WIDE_BREAKPOINT_DP = 600

/**
 * One key for every two-pane scene, so NavDisplay never runs a scene transition between them: the
 * scene stays and animates its own panes ([PaneCarousel]). A NavDisplay transition animates the
 * whole layout (on iOS a push sliding under a grey veil, cutting the tapped row's ripple short), and
 * while it runs both scenes are on screen with the entry they share drawn in only one of them: the
 * other shows a hole, or both overlap, on iOS and Android alike.
 */
private data object TwoPaneKey

/**
 * Shows the last two entries side by side. With a single entry, the right pane holds a placeholder.
 */
private class TwoPaneScene(
    override val entries: List<NavEntry<Screen>>,
    override val previousEntries: List<NavEntry<Screen>>,
) : Scene<Screen> {
    override val key: Any = TwoPaneKey

    override val content: @Composable () -> Unit = {
        // Fold-aware: each pane on one side of a separating fold, else an even 50 / 50 split.
        // The strategy already decided on two panes, so never fall back to one here.
        FoldAwareArrangement(Modifier.fillMaxSize(), wideBreakpoint = 0.dp) { arrangement ->
            PaneCarousel(entries, arrangement)
        }
    }
}

private val PANE_SHIFT_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val PANE_SHIFT_MILLIS = 400

/**
 * Three consecutive entries sliding by one pane: [from] and [to] are how many panes the strip is
 * shifted left (0: the first two entries are shown, 1: the last two).
 */
private class PaneStrip(val entries: List<NavEntry<Screen>>, val from: Float, val to: Float)

/** The strip between two displayed pairs when one is the other shifted by one pane, else null. */
private fun paneStrip(previous: List<NavEntry<Screen>>, current: List<NavEntry<Screen>>): PaneStrip? {
    if (previous.size != 2 || current.size != 2) return null
    val (p0, p1) = previous
    val (c0, c1) = current
    val strip = when {
        // Forward: the right entry moves left, a new one enters from the right.
        c0.contentKey == p1.contentKey && p0.contentKey != c0.contentKey -> PaneStrip(listOf(p0, c0, c1), 0f, 1f)
        // Back: the left entry moves right, the previous one comes back from the left.
        c1.contentKey == p0.contentKey && p1.contentKey != c1.contentKey -> PaneStrip(listOf(c0, c1, p1), 1f, 0f)
        else -> null
    }
    // An entry rendered twice would share its saved state: no animation then.
    return strip?.takeIf { s -> s.entries.map { it.contentKey }.toSet().size == s.entries.size }
}

/**
 * The two panes, which slide like a carousel when the displayed pair shifts by one entry (opening a
 * related article, or back from it). Each entry is drawn once, keyed by its contentKey, so the
 * article changing side keeps its state and nothing is ever drawn twice or left with a hole.
 * Otherwise (a new article picked from the list) the panes update in place.
 */
@Composable
private fun PaneCarousel(entries: List<NavEntry<Screen>>, arrangement: TwoPaneArrangement) {
    val keys = entries.map { it.contentKey }
    var previous by remember { mutableStateOf(entries) }
    val strip = remember(keys) { paneStrip(previous, entries) }
    val shift = remember(keys) { Animatable(strip?.from ?: 0f) }
    var settled by remember { mutableStateOf(keys) }
    LaunchedEffect(keys) {
        previous = entries
        if (strip != null) shift.animateTo(strip.to, tween(PANE_SHIFT_MILLIS, easing = PANE_SHIFT_EASING))
        settled = keys
    }
    val animating = strip != null && settled != keys
    val panes = if (animating) strip.entries else entries
    val offset = if (animating) shift.value else 0f

    Layout(
        contents = listOf(
            { panes.forEach { entry -> key(entry.contentKey) { Box(Modifier.clipToBounds()) { entry.Content() } } } },
            { if (panes.size < 2) Placeholder() },
            // The hinge gap already separates the panes on a fold split.
            { if (!arrangement.isFoldSplit) VerticalDivider() },
        ),
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) { (paneMeasurables, placeholderMeasurables, dividerMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val (first, second) = arrangement.paneRects(width, height, layoutDirection)
        val placeables = paneMeasurables.mapIndexed { index, measurable ->
            val rect = slotRect(first, second, index - offset)
            measurable.measure(Constraints.fixed(rect.width, rect.height)) to rect
        }
        val placeholder = placeholderMeasurables.map {
            it.measure(Constraints.fixed(second.width, second.height))
        }
        val divider = dividerMeasurables.map { it.measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height)) }
        layout(width, height) {
            placeables.forEach { (placeable, rect) -> placeable.place(rect.left, rect.top) }
            placeholder.forEach { it.place(second.left, second.top) }
            divider.forEach { it.place(second.left, second.top) }
        }
    }
}

/**
 * Where a pane sits at a fractional slot: 0 is the first pane, 1 the second, and beyond them the
 * same step continues off screen, so a shift of one slot moves every pane by exactly one pane.
 */
private fun slotRect(first: IntRect, second: IntRect, slot: Float): IntRect {
    val dx = second.left - first.left
    val dy = second.top - first.top
    fun IntRect.shifted(by: Float) = translate((dx * by).roundToInt(), (dy * by).roundToInt())
    return when {
        slot <= 0f -> first.shifted(slot)
        slot >= 1f -> second.shifted(slot - 1f)
        else -> IntRect(
            lerp(first.left, second.left, slot),
            lerp(first.top, second.top, slot),
            lerp(first.right, second.right, slot),
            lerp(first.bottom, second.bottom, slot),
        )
    }
}

@Composable
private fun Placeholder() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "Choisissez un article",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private class TwoPaneSceneStrategy(private val wide: Boolean) : SceneStrategy<Screen> {
    override fun SceneStrategyScope<Screen>.calculateScene(entries: List<NavEntry<Screen>>): Scene<Screen>? {
        if (!wide) return null // falls back to the single-pane scene
        return TwoPaneScene(
            entries = entries.takeLast(2),
            // What back leads to: one pop, not the whole scene. NavDisplay only handles back while
            // this is non-empty, so dropping both shown entries would let back leave the demo.
            previousEntries = entries.dropLast(1),
        )
    }
}

@Composable
private fun Nav3SceneContent() {
    // Not saveable on purpose: see notes.
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val widthDp = currentWidthDp()
    // Two entries when wide enough, or when a separating fold cuts the window (never one pane across it).
    val wide = widthDp >= WIDE_BREAKPOINT_DP || LocalFoldState.current.separatingVerticalHinge() != null
    val strategy = remember(wide) { TwoPaneSceneStrategy(wide) }

    fun push(screen: Screen) {
        if (backStack.lastOrNull() != screen) backStack.add(screen)
    }

    // From the list: replace the detail instead of stacking it, so the list stays on the left.
    fun select(articleId: Int) {
        val target = listOf(Screen.Home, Screen.Detail(articleId))
        if (backStack != target) {
            backStack.clear()
            backStack.addAll(target)
        }
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    // Through the dispatcher, so NavDisplay's own back handler pops, like system back.
    val systemBack = rememberSystemBack(::pop)
    // Side bar (iPhone Duo vertical bar; never on Android): Back lives at the top of the host bar.
    val hostHasBack = LocalBarPlacement.current.isSide

    val trail = backStack.joinToString(" › ") {
        when (it) {
            Screen.Home -> "Accueil"
            is Screen.Detail -> "Article ${it.articleId + 1}"
        }
    }

    Column(Modifier.fillMaxSize()) {
        AdaptationCaption(
            widthDp = widthDp,
            panes = if (wide) 2 else 1,
            extra = " (scène : ${if (wide) "deux dernières entrées" else "dernière entrée"})",
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            if (!hostHasBack) {
                IconButton(onClick = systemBack, enabled = backStack.size > 1) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Dépiler")
                }
            }
            Text(
                "Pile : $trail",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
                    .padding(start = if (hostHasBack) 16.dp else 0.dp, end = 12.dp)
                    .padding(vertical = if (hostHasBack) 8.dp else 0.dp),
            )
        }
        HorizontalDivider()
        NavDisplay(
            backStack = backStack,
            // Slides stay inside the demo area.
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            onBack = ::pop,
            sceneStrategies = listOf(strategy),
            entryProvider = entryProvider {
                entry<Screen.Home> {
                    ArticleList(
                        // The list is shown only with its own detail next to it: [Accueil, Article n].
                        selectedId = (backStack.getOrNull(1) as? Screen.Detail)?.articleId,
                        onSelect = ::select,
                    )
                }
                entry<Screen.Detail> { key ->
                    DetailPane(articleId = key.articleId, onOpen = { push(Screen.Detail(it)) })
                }
            },
        )
    }
}

@Composable
private fun DetailPane(articleId: Int, onOpen: (Int) -> Unit) {
    val article = articleById(articleId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ArticleText(article)
        Text(
            "À lire ensuite (ajoute une entrée à la pile)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        article.related.forEach { id ->
            ListItem(
                headlineContent = { Text(articleById(id).title) },
                modifier = Modifier.clickable { onOpen(id) },
            )
        }
    }
}
