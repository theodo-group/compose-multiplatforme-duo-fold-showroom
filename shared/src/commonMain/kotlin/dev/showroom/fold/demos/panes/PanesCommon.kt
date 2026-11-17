@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.showroom.fold.demos.panes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.showroom.fold.core.rememberSystemBack
import dev.showroom.fold.fold.FoldState
import dev.showroom.fold.fold.HingeOrientation
import dev.showroom.fold.fold.LocalFoldState
import kotlinx.coroutines.launch

/** One fake French article, shared by the three pane demos. */
internal class Article(
    val id: Int,
    val title: String,
    val author: String,
    val body: String,
    val related: List<Int>,
)

internal val sampleArticles: List<Article> = listOf(
    "Le pliage du verre ultra-fin" to "Camille Durand",
    "Réparer son téléphone soi-même" to "Julien Moreau",
    "Pourquoi les charnières sont si difficiles" to "Inès Lefèvre",
    "Un clavier pour deux écrans" to "Hugo Bernard",
    "Mode livre : lire comme sur papier" to "Léa Martin",
    "Mode chevalet : appels vidéo mains libres" to "Louis Petit",
    "Écrans pliables et autonomie" to "Manon Roux",
    "Le retour du format carré" to "Théo Girard",
    "Poser son téléphone sur la table" to "Chloé Fournier",
    "Multitâche : deux applications côte à côte" to "Nathan Lambert",
    "Le glisser-déposer entre fenêtres" to "Emma Fontaine",
    "Concevoir pour tous les formats" to "Lucas Rousseau",
    "Accessibilité et grands écrans" to "Sarah Vincent",
    "Les tests sur appareils pliables" to "Maxime Muller",
    "Demain : des écrans enroulables ?" to "Zoé Faure",
).mapIndexed { index, (title, author) ->
    Article(
        id = index,
        title = title,
        author = author,
        body = "Article ${index + 1} par $author.\n\n" +
            "Les téléphones pliables obligent à repenser l'interface : la même application doit " +
            "s'afficher sur un petit écran extérieur, puis sur un écran intérieur presque deux fois " +
            "plus large, sans perdre l'endroit où l'utilisateur en était.\n\n" +
            "Une bonne pratique consiste à décrire l'interface en panneaux, et à laisser la taille " +
            "de la fenêtre décider combien de panneaux sont visibles en même temps.",
        related = listOf((index + 1) % 15, (index + 4) % 15, (index + 7) % 15),
    )
}

internal fun articleById(id: Int): Article = sampleArticles[id]

/** Current window width in dp, as seen by the composition (updates on fold / resize). */
@Composable
internal fun currentWidthDp(): Int {
    val px = LocalWindowInfo.current.containerSize.width
    return (px / LocalDensity.current.density).toInt()
}

/** Material window width classes: compact < 600 <= medium < 840 <= expanded. */
internal fun widthClassLabel(widthDp: Int): String = when {
    widthDp < 600 -> "compact"
    widthDp < 840 -> "moyen"
    else -> "étendu"
}

/** Visible caption stating the current adaptation and why. */
@Composable
internal fun AdaptationCaption(widthDp: Int, panes: Int, extra: String = "") {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Largeur $widthDp dp → ${widthClassLabel(widthDp)} → " +
                (if (panes == 1) "1 panneau" else "$panes panneaux") + extra,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun ArticleList(selectedId: Int?, onSelect: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(sampleArticles, key = { it.id }) { article ->
            ListItem(
                headlineContent = { Text(article.title, maxLines = 1) },
                supportingContent = { Text(article.author, maxLines = 1) },
                colors = ListItemDefaults.colors(
                    containerColor = if (article.id == selectedId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier.clickable { onSelect(article.id) },
            )
        }
    }
}

/** Title, author and body, to place in the caller's scrolling column. */
@Composable
internal fun ArticleText(article: Article) {
    Text(article.title, style = MaterialTheme.typography.headlineSmall)
    Text(
        article.author,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Text(article.body, style = MaterialTheme.typography.bodyLarge)
}

/** In-content Back, only for a top host bar: a side bar (iPhone Duo) already has Back at its top. */
@Composable
internal fun BackRow(label: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Makes system back (and the iOS edge swipe) close panes before the host leaves the demo: this
 * handler is registered after the host's, so it wins while [navigator] can go back.
 * Returns the action for on-screen Back buttons, sent through the same dispatcher.
 */
@Composable
internal fun rememberPaneBack(navigator: ThreePaneScaffoldNavigator<*>): () -> Unit {
    val scope = rememberCoroutineScope()
    val navigateBack: () -> Unit = { scope.launch { navigator.navigateBack() } }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = navigator.canNavigateBack(),
        onBackCompleted = navigateBack,
    )
    return rememberSystemBack(navigateBack)
}

/**
 * The Material pane directive for the window, made fold-aware from [LocalFoldState].
 *
 * material3-adaptive reads the hinge from the platform posture, which is empty on iOS, so on iPhone
 * Duo its panes ignore the fold. This puts the separating vertical hinge in
 * [PaneScaffoldDirective.excludedBounds] (window pixels, the space [FoldState.hingeBounds] uses): the
 * scaffold then places two panes exactly on either side of it, never under it. A window crossed by
 * such a fold also gets two horizontal partitions, so no single pane straddles the fold.
 * Pass it to the navigator (`scaffoldDirective = ...`) so its pane values agree with the layout.
 */
@Composable
internal fun rememberFoldAwarePaneDirective(): PaneScaffoldDirective {
    val base = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val fold = LocalFoldState.current
    return remember(base, fold) { base.avoidingFold(fold) }
}

/** The vertical separating hinge, in window pixels, or null when panes may ignore the fold. */
internal fun FoldState.separatingVerticalHinge() =
    hingeBounds?.takeIf { isSeparating && orientation == HingeOrientation.Vertical }

/**
 * Only a vertical hinge is excluded: the scaffold splits its width around excluded bounds, so a
 * horizontal (tabletop) hinge would be misread as a left/right boundary.
 */
internal fun PaneScaffoldDirective.avoidingFold(fold: FoldState): PaneScaffoldDirective {
    val hinge = fold.separatingVerticalHinge() ?: return this
    return copy(
        maxHorizontalPartitions = maxOf(maxHorizontalPartitions, 2),
        excludedBounds = listOf(hinge),
    )
}
