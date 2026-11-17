@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.showroom.fold.demos.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing
import dev.showroom.fold.core.LocalBarPlacement
import dev.showroom.fold.core.isSide
import dev.showroom.fold.core.rememberSystemBack

val supportingPaneDemo = Demo(
    id = "supporting-pane",
    title = "Panneau secondaire",
    summary = "Un article avec un panneau d'articles liés à côté quand la place le permet, sinon accessible par un bouton.",
    sharing = Sharing.Common,
    notes = listOf(
        "SupportingPaneScaffold et son navigateur sont 100 % commonMain : même code sur Android et iOS.",
        "Large : article principal + panneau secondaire côte à côte ; compact : le panneau secondaire est une destination (bouton « Articles liés ») avec flèche de retour visible.",
        "L'article affiché est un état local de la démo, pas une destination du navigateur : choisir un article lié ne remplit pas l'historique de retour.",
        "Retour : un BackHandler enregistré dans la démo (priorité sur celui de l'hôte) ferme d'abord le panneau secondaire, puis la démo quitte à la racine.",
        "iPhone Duo : avec la barre verticale, son Retour (en haut) ferme le panneau secondaire ; la ligne « Retour à l'article » n'apparaît qu'avec la barre du haut.",
        "Pli : la directive reçoit le pli séparant dans excludedBounds (material3-adaptive ne le voit pas sur iOS), donc article et panneau secondaire se placent de part et d'autre du pli.",
        "Honnêteté : nous n'avons pas branché l'animation de retour prédictif des panneaux ; hors pli séparant, la mise en page vient de la taille de fenêtre.",
    ),
    content = { SupportingPaneContent() },
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SupportingPaneContent() {
    // Fold-aware directive: on iOS the library has no hinge, so the panes would ignore the fold.
    val navigator = rememberSupportingPaneScaffoldNavigator(scaffoldDirective = rememberFoldAwarePaneDirective())
    var articleId by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = navigator.canNavigateBack()) { scope.launch { navigator.navigateBack() } }
    // On-screen Back goes through the dispatcher, so it hits the BackHandler above like system back.
    val systemBack = rememberSystemBack { scope.launch { navigator.navigateBack() } }
    // Side bar (iPhone Duo vertical bar; never on Android): Back lives at the top of the host bar.
    val hostHasBack = LocalBarPlacement.current.isSide

    val widthDp = currentWidthDp()
    val value = navigator.scaffoldValue
    val mainShown = value[SupportingPaneScaffoldRole.Main] == PaneAdaptedValue.Expanded
    val supportShown = value[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded
    val panes = listOf(mainShown, supportShown).count { it }
    val article = articleById(articleId)

    Column(Modifier.fillMaxSize()) {
        AdaptationCaption(
            widthDp = widthDp,
            panes = panes,
            extra = if (panes == 1) " (panneau secondaire via le bouton)" else " (article + liés)",
        )
        SupportingPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = value,
            modifier = Modifier.weight(1f),
            mainPane = {
                AnimatedPane {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            Text(article.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                article.author,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Text(article.body, style = MaterialTheme.typography.bodyLarge)
                            if (!supportShown) {
                                Button(
                                    onClick = { scope.launch { navigator.navigateTo(SupportingPaneScaffoldRole.Supporting) } },
                                    modifier = Modifier.padding(top = 16.dp),
                                ) { Text("Articles liés") }
                            }
                        }
                    }
                }
            },
            supportingPane = {
                AnimatedPane {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (!mainShown && !hostHasBack) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    IconButton(onClick = systemBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour à l'article")
                                    }
                                    Text("Retour à l'article", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Text(
                                "Articles liés",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            article.related.forEach { id ->
                                val related = articleById(id)
                                ListItem(
                                    headlineContent = { Text(related.title) },
                                    supportingContent = { Text(related.author) },
                                    modifier = Modifier.fillMaxWidth().clickableItem {
                                        articleId = id
                                        // Compact: go back to the main pane to read the chosen article.
                                        if (!mainShown) scope.launch { navigator.navigateBack() }
                                    },
                                )
                            }
                            Text(
                                "Notes",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            Text(
                                "Notes de lecture pour « ${article.title} » : à relire, à partager avec l'équipe design.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}
