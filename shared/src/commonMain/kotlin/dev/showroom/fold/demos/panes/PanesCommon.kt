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

/** One fake French article, shared by the three pane demos; each has its own short body. */
internal class Article(
    val id: Int,
    val title: String,
    val author: String,
    val body: String,
    val related: List<Int>,
)

internal val sampleArticles: List<Article> = listOf(
    Triple(
        "Le pliage du verre ultra-fin",
        "Camille Durand",
        "Un écran pliable n'est pas en plastique souple : c'est un verre de moins de 0,1 mm, assez fin " +
            "pour se courber sans casser. Il est trempé chimiquement, puis protégé par un film qui encaisse " +
            "les rayures.\n\nLe pli reste visible sous certains angles : c'est le prix d'un rayon de courbure " +
            "de quelques millimètres seulement.",
    ),
    Triple(
        "Réparer son téléphone soi-même",
        "Julien Moreau",
        "Changer la batterie d'un pliable demande de décoller deux écrans au lieu d'un, et de ne pas " +
            "abîmer la nappe qui traverse la charnière.\n\nLes fabricants publient de plus en plus de " +
            "guides et de pièces détachées, mais l'écran intérieur reste la réparation la plus chère.",
    ),
    Triple(
        "Pourquoi les charnières sont si difficiles",
        "Inès Lefèvre",
        "Une charnière doit tenir des centaines de milliers d'ouvertures, rester fine, laisser l'écran " +
            "former une goutte d'eau une fois fermé et tenir seule à n'importe quel angle.\n\nChaque " +
            "génération gagne quelques dixièmes de millimètre et quelques grammes : c'est elle qui décide de " +
            "l'épaisseur du téléphone.",
    ),
    Triple(
        "Un clavier pour deux écrans",
        "Hugo Bernard",
        "À moitié plié, l'écran du bas devient un clavier et celui du haut affiche le texte, comme un " +
            "petit ordinateur portable.\n\nLe clavier logiciel doit alors se caler sur la moitié basse, " +
            "et l'application garder le curseur visible au-dessus du pli.",
    ),
    Triple(
        "Mode livre : lire comme sur papier",
        "Léa Martin",
        "Ouvert comme un livre, l'écran intérieur montre deux pages face à face, de part et d'autre du " +
            "pli.\n\nUne liseuse bien faite ne coupe jamais une ligne en deux sur la charnière : chaque " +
            "page tient d'un seul côté, et le geste pour tourner la page suit le bord de l'écran.",
    ),
    Triple(
        "Mode chevalet : appels vidéo mains libres",
        "Louis Petit",
        "Posé à moitié ouvert sur une table, le téléphone tient debout tout seul : la caméra en haut, " +
            "les commandes en bas.\n\nPour une visio, l'image de l'interlocuteur occupe la moitié haute " +
            "et les boutons raccrocher, micro et caméra passent sous le pli, à portée de doigt.",
    ),
    Triple(
        "Écrans pliables et autonomie",
        "Manon Roux",
        "Deux écrans, c'est deux dalles à alimenter, et la plus grande consomme presque autant qu'une " +
            "tablette.\n\nLes constructeurs répartissent la batterie en deux cellules, une de chaque côté " +
            "de la charnière, et l'écran extérieur sert aux consultations rapides pour économiser l'autre.",
    ),
    Triple(
        "Le retour du format carré",
        "Théo Girard",
        "Déplié, un pliable offre un écran presque carré, un format qu'on n'avait plus vu depuis les " +
            "premiers smartphones à clavier.\n\nLes interfaces pensées en colonnes étroites y semblent " +
            "perdues : il faut passer à deux panneaux ou à une grille plus large.",
    ),
    Triple(
        "Poser son téléphone sur la table",
        "Chloé Fournier",
        "Plié à 90 degrés et posé à plat, le téléphone devient un petit trépied : photos de groupe, " +
            "time-lapse ou recette affichée pendant qu'on cuisine.\n\nL'application détecte cette posture " +
            "et range ses commandes dans la moitié posée sur la table.",
    ),
    Triple(
        "Multitâche : deux applications côte à côte",
        "Nathan Lambert",
        "Sur l'écran intérieur, deux applications tiennent côte à côte, chacune dans sa moitié.\n\n" +
            "Chaque application reçoit alors une fenêtre de taille téléphone et doit s'y adapter sans " +
            "redémarrer, ni perdre ce que l'utilisateur était en train d'écrire.",
    ),
    Triple(
        "Le glisser-déposer entre fenêtres",
        "Emma Fontaine",
        "Avec deux applications ouvertes, il devient naturel de faire glisser une photo de la galerie " +
            "vers un message.\n\nPour l'accepter, une application déclare les types de contenu qu'elle " +
            "reçoit et indique visuellement où on peut lâcher l'élément.",
    ),
    Triple(
        "Concevoir pour tous les formats",
        "Lucas Rousseau",
        "Plutôt que de dessiner un écran par appareil, on décrit l'interface en panneaux, et la largeur " +
            "disponible décide combien sont visibles en même temps.\n\nLe même code s'affiche alors " +
            "sur un téléphone, un pliable fermé ou ouvert, et une tablette.",
    ),
    Triple(
        "Accessibilité et grands écrans",
        "Sarah Vincent",
        "Un grand écran ne doit pas servir à rapetisser le texte : quand la taille de police augmente, " +
            "la mise en page doit passer d'un à deux panneaux, pas l'inverse.\n\nLes zones tactiles " +
            "restent grandes, et l'ordre de lecture au lecteur d'écran suit l'ordre des panneaux.",
    ),
    Triple(
        "Les tests sur appareils pliables",
        "Maxime Muller",
        "Tester un pliable, c'est plier et déplier au milieu d'une saisie, tourner l'appareil et passer " +
            "en écran partagé.\n\nLes émulateurs simulent la charnière et ses postures, mais rien ne " +
            "remplace un appareil réel pour sentir où tombe le pouce.",
    ),
    Triple(
        "Demain : des écrans enroulables ?",
        "Zoé Faure",
        "Après le pli, les prototypes enroulent l'écran dans le boîtier et le déroulent à la demande, " +
            "sans charnière ni pli visible.\n\nPour les applications, rien ne change vraiment : la largeur " +
            "varie en continu, et l'interface doit déjà savoir s'y adapter.",
    ),
).mapIndexed { index, (title, author, body) ->
    Article(
        id = index,
        title = title,
        author = author,
        body = "Article ${index + 1} par $author.\n\n$body",
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
