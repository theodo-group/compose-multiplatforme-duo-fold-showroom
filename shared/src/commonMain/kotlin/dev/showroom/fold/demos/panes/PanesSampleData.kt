package dev.showroom.fold.demos.panes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** One fake French article / message, shared by the three pane demos. */
internal class Article(
    val id: Int,
    val title: String,
    val author: String,
    val summary: String,
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
        summary = "Un regard sur « ${title.lowercase()} » : ce qui change pour les concepteurs d'applications.",
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.Start) {
            Text(
                text = "Largeur $widthDp dp → ${widthClassLabel(widthDp)} → " +
                    (if (panes == 1) "1 panneau" else "$panes panneaux") + extra,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Clickable modifier kept in one place so list rows stay one-liners. */
internal fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
