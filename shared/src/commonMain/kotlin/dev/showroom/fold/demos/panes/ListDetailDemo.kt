@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.showroom.fold.demos.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing

val listDetailDemo = Demo(
    id = "list-detail",
    title = "Liste / détail",
    summary = "Une liste d'articles qui devient liste + détail côte à côte dès que la fenêtre est assez large.",
    sharing = Sharing.Common,
    notes = listOf(
        "Tout vient de commonMain : ListDetailPaneScaffold et son navigateur (material3-adaptive 1.3.0) tournent à l'identique sur Android et iOS.",
        "Le navigateur garde l'historique des panneaux : en dépliant l'appareil avec un article ouvert, cet article reste affiché à côté de la liste.",
        "Le nombre de panneaux dépend de la taille de fenêtre (classes de largeur Material), pas de la charnière : aucun code spécifique n'est nécessaire.",
        "Retour : un BackHandler placé dans la démo (donc enregistré après celui de l'hôte, qui a la priorité) ferme d'abord le détail ; sur iOS le geste de bord déclenche le même handler.",
        "Écueil : currentWindowAdaptiveInfo est déprécié en 1.3.0 (V2 utilisé) ; la navigation prédictive animée des panneaux n'est pas branchée ici (BackHandler simple).",
    ),
    content = { ListDetailContent() },
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ListDetailContent() {
    // The content key is the article id; the navigator keeps the pane history across resizes.
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    // Pop the detail first: registered after (so above) the host's leave-the-demo handler.
    val scope = rememberCoroutineScope()
    BackHandler(enabled = navigator.canNavigateBack()) { scope.launch { navigator.navigateBack() } }

    val widthDp = currentWidthDp()
    // Make sure adaptive info is read from the window (drives the scaffold directive).
    currentWindowAdaptiveInfoV2()
    val value = navigator.scaffoldValue
    val listShown = value[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val detailShown = value[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val panes = listOf(listShown, detailShown).count { it }
    val selected = navigator.currentDestination?.contentKey

    Column(Modifier.fillMaxSize()) {
        AdaptationCaption(
            widthDp = widthDp,
            panes = panes,
            extra = if (panes == 1) " (navigation entre liste et détail)" else " (liste + détail)",
        )
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = value,
            modifier = Modifier.weight(1f),
            listPane = {
                AnimatedPane {
                    ArticleList(
                        selectedId = if (detailShown && panes == 2) selected else null,
                        onSelect = { scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, it) } },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    if (selected == null) {
                        EmptyDetail()
                    } else {
                        ArticleDetail(
                            article = articleById(selected),
                            // Visible back affordance only when the list is hidden (single pane).
                            onBack = if (!listShown) ({ scope.launch { navigator.navigateBack() } }) else null,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ArticleList(selectedId: Int?, onSelect: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(sampleArticles, key = { it.id }) { article ->
            val isSelected = article.id == selectedId
            ListItem(
                headlineContent = { Text(article.title, maxLines = 1) },
                supportingContent = { Text(article.author, maxLines = 1) },
                colors = ListItemDefaults.colors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier.clickableItem { onSelect(article.id) },
            )
        }
    }
}

@Composable
private fun EmptyDetail() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "Sélectionnez un article dans la liste",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ArticleDetail(article: Article, onBack: (() -> Unit)?) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            if (onBack != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour à la liste")
                    }
                    Text("Retour à la liste", style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(article.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    article.author,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(article.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
