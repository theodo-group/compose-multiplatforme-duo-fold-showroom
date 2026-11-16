package dev.showroom.fold.demos.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
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

val nav3SceneDemo = Demo(
    id = "nav3-scenes",
    title = "Navigation 3 : scènes",
    summary = "Une même pile de navigation affichée en un ou deux panneaux selon la largeur, grâce à une Scene personnalisée.",
    sharing = Sharing.Common,
    notes = listOf(
        "Navigation 3 (org.jetbrains.androidx.navigation3 1.1.2) compile et fonctionne depuis commonMain pour Android et iOS : NavDisplay, NavEntry et un SceneStrategy maison.",
        "La pile appartient à la démo (mutableStateListOf de clés Screen) ; la scène choisit seulement combien d'entrées afficher : 2 si la fenêtre fait au moins 600 dp, 1 sinon.",
        "Perdu faute de plugin : rememberNavBackStack (restauration après mort du processus) exige des clés NavKey @Serializable et un SerializersModule polymorphique hors Android, donc le plugin kotlinx-serialization et sa dépendance, absents du projet.",
        "Retour : NavDisplay installe son propre handler (NavigationEvent) quand la pile a plus d'une entrée ; il passe avant celui de l'hôte et dépile, la démo ne quitte qu'à la racine. Une flèche visible est ajoutée pour iOS.",
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
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.4f).fillMaxHeight()) { entries.first().Content() }
            VerticalDivider()
            Box(Modifier.weight(0.6f).fillMaxHeight()) {
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
    }
}

private class TwoPaneSceneStrategy(private val wide: Boolean) : SceneStrategy<Screen> {
    override fun SceneStrategyScope<Screen>.calculateScene(entries: List<NavEntry<Screen>>): Scene<Screen>? {
        if (!wide) return null // falls back to the single-pane scene
        val shown = entries.takeLast(2)
        return TwoPaneScene(
            key = TwoPaneKey(shown.map { it.contentKey }),
            entries = shown,
            previousEntries = entries.dropLast(shown.size),
        )
    }
}

@Composable
private fun Nav3SceneContent() {
    // Not saveable on purpose: see notes.
    val backStack: SnapshotStateList<Screen> = remember { mutableStateListOf(Screen.Home) }
    val widthDp = currentWidthDp()
    val wide = widthDp >= WIDE_BREAKPOINT_DP
    val strategy = remember(wide) { TwoPaneSceneStrategy(wide) }

    fun push(screen: Screen) {
        if (backStack.lastOrNull() != screen) backStack.add(screen)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

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
            IconButton(onClick = ::pop, enabled = backStack.size > 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Dépiler")
            }
            Text(
                "Pile : $trail",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
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
                    ArticleListPane(onOpen = { push(Screen.Detail(it)) }, selected = (backStack.last() as? Screen.Detail)?.articleId)
                }
                entry<Screen.Detail> { key ->
                    DetailPane(articleId = key.articleId, onOpen = { push(Screen.Detail(it)) })
                }
            },
        )
    }
}

@Composable
private fun ArticleListPane(onOpen: (Int) -> Unit, selected: Int?) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(sampleArticles, key = { it.id }) { article ->
            ListItem(
                headlineContent = { Text(article.title, maxLines = 1) },
                supportingContent = { Text(article.author, maxLines = 1) },
                colors = ListItemDefaults.colors(
                    containerColor = if (article.id == selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier.clickableItem { onOpen(article.id) },
            )
        }
    }
}

@Composable
private fun DetailPane(articleId: Int, onOpen: (Int) -> Unit) {
    val article = articleById(articleId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(article.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            article.author,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(article.body, style = MaterialTheme.typography.bodyLarge)
        Text(
            "À lire ensuite (ajoute une entrée à la pile)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        article.related.forEach { id ->
            ListItem(
                headlineContent = { Text(articleById(id).title) },
                modifier = Modifier.clickableItem { onOpen(id) },
            )
        }
    }
}
