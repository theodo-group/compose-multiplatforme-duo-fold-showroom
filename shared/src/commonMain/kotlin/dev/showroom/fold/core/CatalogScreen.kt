package dev.showroom.fold.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.showroom.fold.fold.LocalFoldState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(demos: List<Demo>, gridState: LazyGridState, onOpen: (Demo) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Fold Showroom")
                        Text(
                            text = liveWindowSummary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Sharing.entries.forEach { sharing ->
                val group = demos.filter { it.sharing == sharing }
                if (group.isNotEmpty()) {
                    item(key = "header-${sharing.name}", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 12.dp)) {
                            SharingBadge(sharing)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = sharing.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(group, key = { it.id }) { demo ->
                        ElevatedCard(onClick = { onOpen(demo) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(demo.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = demo.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "673 × 841 dp · posture à plat": proves at a glance that the shared UI sees the real window. */
@Composable
private fun liveWindowSummary(): String {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val fold = LocalFoldState.current
    val width = (size.width / density).roundToInt()
    val height = (size.height / density).roundToInt()
    return "$width × $height dp · ${fold.posture.label.lowercase()}"
}
