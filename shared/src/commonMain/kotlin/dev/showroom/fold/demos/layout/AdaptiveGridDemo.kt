package dev.showroom.fold.demos.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing
import kotlin.math.max

val adaptiveGridDemo = Demo(
    id = "adaptive-grid",
    title = "Grille adaptative",
    summary = "Une galerie dont le nombre de colonnes et la disposition interne des cartes suivent l'espace disponible.",
    sharing = Sharing.Common,
    notes = listOf(
        "LazyVerticalGrid + GridCells.Fixed(n) : n est calculé à partir de la largeur disponible et de la largeur minimale de colonne, puis arrondi au nombre pair inférieur (sauf s'il n'en tient qu'une) pour qu'aucune colonne ne chevauche la charnière (recommandation Apple pour l'iPhone Duo) ; l'élément hero prend toute la largeur avec span = GridItemSpan(maxLineSpan).",
        "Chaque carte utilise BoxWithConstraints : elle choisit vertical ou horizontal selon la largeur que la grille lui donne, pas selon la fenêtre. Un même composant s'adapte donc aussi dans un panneau de fenêtre partagée.",
        "Tout est en commonMain, sans code plateforme ; les « images » sont des dégradés dessinés (aucune ressource).",
        "Piège : GridCells.Adaptive ne dit pas combien de colonnes il a choisi et peut en produire un nombre impair (3, 5…) dont celle du milieu chevauche le pli. On calcule donc soi-même le nombre de colonnes (BoxWithConstraints) et on le passe à GridCells.Fixed : la légende affiche le nombre exact utilisé par la grille.",
        "Les clés stables (key = id) évitent de perdre la position de défilement quand le nombre de colonnes change au pliage.",
    ),
    content = { AdaptiveGrid() },
)

private class Item(val id: Int, val title: String, val subtitle: String, val colorA: Color, val colorB: Color)

private val Items: List<Item> = run {
    val palette = listOf(
        Color(0xFF0B6B5C) to Color(0xFF8AD6C3),
        Color(0xFF7A4E9B) to Color(0xFFDDB8FF),
        Color(0xFFB3541E) to Color(0xFFFFC7A0),
        Color(0xFF2B5FA8) to Color(0xFFA9C8FF),
        Color(0xFF9B2C4E) to Color(0xFFFFB3C8),
        Color(0xFF5E7A1E) to Color(0xFFD3EC8F),
    )
    val names = listOf("Aube", "Brume", "Canopée", "Dune", "Éclat", "Falaise", "Givre", "Horizon", "Îlot", "Jungle", "Lagune", "Mirage")
    names.mapIndexed { i, n ->
        Item(i, n, "Collection n° ${i + 1} · ${(i % 5 + 2) * 3} éléments", palette[i % palette.size].first, palette[i % palette.size].second)
    }
}

private val HorizontalCardMinWidth = 320.dp
private val GridPadding = 16.dp
private val GridSpacing = 12.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdaptiveGrid() {
    var minColumnDp by rememberSaveable { mutableIntStateOf(180) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val available = totalWidth - GridPadding * 2
        val fitting = max(1, ((available + GridSpacing) / (minColumnDp.dp + GridSpacing)).toInt())
        // Apple (iPhone Duo): prefer an even column count so no column straddles the center fold.
        val columns = if (fitting > 1 && fitting % 2 == 1) fitting - 1 else fitting
        val columnWidth = (available - GridSpacing * (columns - 1)) / columns
        val horizontal = columnWidth >= HorizontalCardMinWidth

        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Largeur ${totalWidth.value.toInt()} dp → $columns colonne(s) de ${columnWidth.value.toInt()} dp → " +
                            if (horizontal) "cartes horizontales (≥ 320 dp)" else "cartes verticales (< 320 dp)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(140, 180, 340).forEach { v ->
                            FilterChip(
                                selected = minColumnDp == v,
                                onClick = { minColumnDp = v },
                                label = { Text("Colonne min. $v dp") },
                            )
                        }
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(GridPadding),
                horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                verticalArrangement = Arrangement.spacedBy(GridSpacing),
            ) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    HeroCard()
                }
                items(Items, key = { it.id }) { item -> FeedCard(item) }
            }
        }
    }
}

@Composable
private fun Placeholder(item: Item, modifier: Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(item.colorA, item.colorB))))
}

@Composable
private fun HeroCard() {
    Card(Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            val wide = maxWidth >= 600.dp
            val hero = Items.first()
            if (wide) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Placeholder(hero, Modifier.weight(1f).fillMaxHeight().height(200.dp))
                    HeroText(Modifier.weight(1f))
                }
            } else {
                Column {
                    Placeholder(hero, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    HeroText(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HeroText(modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("À la une", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("L'élément hero occupe toute la largeur", style = MaterialTheme.typography.titleLarge)
        Text(
            "GridItemSpan(maxLineSpan) : quel que soit le nombre de colonnes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedCard(item: Item) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        BoxWithConstraints {
            if (maxWidth >= HorizontalCardMinWidth) {
                Row(Modifier.height(96.dp)) {
                    Placeholder(item, Modifier.width(96.dp).fillMaxHeight())
                    CardText(item, Modifier.weight(1f).padding(12.dp))
                }
            } else {
                Column {
                    Placeholder(item, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                    CardText(item, Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun CardText(item: Item, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            item.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}
