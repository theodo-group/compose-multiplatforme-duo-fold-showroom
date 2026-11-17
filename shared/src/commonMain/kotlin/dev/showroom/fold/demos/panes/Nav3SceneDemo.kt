package dev.showroom.fold.demos.panes

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import dev.showroom.fold.demos.hinge.FoldAwareTwoPane
import dev.showroom.fold.fold.LocalFoldState

val nav3SceneDemo = Demo(
    id = "nav3-scenes",
    title = "Navigation 3 : scènes",
    summary = "Une même pile de navigation affichée en un ou deux panneaux selon la largeur, grâce à une Scene personnalisée.",
    sharing = Sharing.Common,
    notes = listOf(
        "Navigation 3 (org.jetbrains.androidx.navigation3 1.1.2) compile et fonctionne depuis commonMain pour Android et iOS : NavDisplay, NavEntry et un SceneStrategy maison.",
        "La pile appartient à la démo (mutableStateListOf de clés Screen) ; la scène choisit seulement combien d'entrées afficher : 2 si la fenêtre fait au moins 600 dp ou qu'un pli séparant la coupe, 1 sinon.",
        "Pli : la scène à deux panneaux utilise FoldAwareTwoPane, donc chaque entrée se cale d'un côté du pli (moitié / moitié sans pli), jamais à cheval dessus.",
        "Perdu faute de plugin : rememberNavBackStack (restauration après mort du processus) exige des clés NavKey @Serializable et un SerializersModule polymorphique hors Android, donc le plugin kotlinx-serialization et sa dépendance, absents du projet.",
        "Retour : NavDisplay installe son propre handler (NavigationEvent) quand la pile a plus d'une entrée ; il passe avant celui de l'hôte et dépile, la démo ne quitte qu'à la racine. Une flèche « Dépiler » est ajoutée avec la barre du haut ; iPhone Duo en barre verticale : c'est le Retour en haut de cette barre qui dépile, la ligne ne garde que la pile.",
        "Gotcha : la clé d'une scène doit changer quand ses entrées changent, sinon la transition entre scènes ne se déclenche pas ; l'état de défilement d'une entrée suit le contentKey de son entrée.",
    ),
    content = { Nav3SceneContent() },
)

/** Back stack keys. Plain classes: no serialization needed with a non-saveable stack. */
private sealed interface Screen {
    data object Home : Screen
    data class Detail(val articleId: Int) : Screen
}

private const val WIDE_BREAKPOINT_DP = 600

/** The scene key changes whenever the displayed entries change, so scene transitions run. */
private data class TwoPaneKey(val contentKeys: List<Any>)

/**
 * Shows the last two entries side by side. With a single entry, the right pane holds a placeholder.
 */
private class TwoPaneScene(
    override val key: Any,
    override val entries: List<NavEntry<Screen>>,
    override val previousEntries: List<NavEntry<Screen>>,
) : Scene<Screen> {
    override val content: @Composable () -> Unit = {
        // Fold-aware: each pane on one side of a separating fold, else an even 50 / 50 split.
        // The strategy already decided on two panes, so never fall back to one here.
        FoldAwareTwoPane(
            modifier = Modifier.fillMaxSize(),
            wideBreakpoint = 0.dp,
            first = { entries.first().Content() },
            second = { arrangement ->
                Row(Modifier.fillMaxSize()) {
                    // The hinge gap already separates the panes on a fold split.
                    if (!arrangement.isFoldSplit) VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxSize()) { SecondPane() }
                }
            },
        )
    }

    @Composable
    private fun SecondPane() {
        if (entries.size >= 2) {
            entries.last().Content()
        } else {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Choisissez un article",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private class TwoPaneSceneStrategy(private val wide: Boolean) : SceneStrategy<Screen> {
    override fun SceneStrategyScope<Screen>.calculateScene(entries: List<NavEntry<Screen>>): Scene<Screen>? {
        if (!wide) return null // falls back to the single-pane scene
        val shown = entries.takeLast(2)
        return TwoPaneScene(
            key = TwoPaneKey(shown.map { it.contentKey }),
            entries = shown,
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
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onBack = ::pop,
            sceneStrategies = listOf(strategy),
            entryProvider = entryProvider {
                entry<Screen.Home> {
                    ArticleList(
                        selectedId = (backStack.last() as? Screen.Detail)?.articleId,
                        onSelect = { push(Screen.Detail(it)) },
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
