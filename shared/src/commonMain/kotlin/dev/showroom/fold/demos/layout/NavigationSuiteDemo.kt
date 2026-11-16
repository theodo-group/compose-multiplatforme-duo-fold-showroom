package dev.showroom.fold.demos.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing

val navigationSuiteDemo = Demo(
    id = "nav-suite",
    title = "Navigation adaptative",
    summary = "NavigationSuiteScaffold choisit seul barre du bas, rail ou tiroir selon la taille de la fenêtre.",
    sharing = Sharing.Common,
    notes = listOf(
        "NavigationSuiteScaffold (material3-adaptive-navigation-suite) est entièrement dans commonMain : même code sur Android et iOS.",
        "Le type par défaut vient de calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2()) : barre en bas en compact, rail en medium et expanded. Seule la taille de fenêtre décide, pas l'appareil.",
        "Le sélecteur force un type via le paramètre layoutType ; le tiroir permanent n est jamais choisi par le calcul automatique par défaut, seulement via ce sélecteur.",
        "Rien de spécifique plateforme ici. Le seul point d'attention : sur iOS il n'y a pas de barre d'onglets native UIKit, c'est un rendu Material.",
    ),
    content = { NavigationSuiteContent() },
)

private enum class Dest(val label: String, val icon: ImageVector) {
    Home("Accueil", Icons.Filled.Home),
    Favorites("Favoris", Icons.Filled.Favorite),
    Profile("Profil", Icons.Filled.AccountCircle),
    Settings("Réglages", Icons.Filled.Settings),
}

private enum class Forced(val label: String) {
    Auto("Auto"), Bar("Barre"), Rail("Rail"), Drawer("Tiroir")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavigationSuiteContent() {
    var selected by rememberSaveable { mutableStateOf(Dest.Home) }
    var forced by rememberSaveable { mutableStateOf(Forced.Auto) }

    val adaptive = currentWindowAdaptiveInfoV2()
    val auto = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptive)
    val layoutType = when (forced) {
        Forced.Auto -> auto
        Forced.Bar -> NavigationSuiteType.NavigationBar
        Forced.Rail -> NavigationSuiteType.NavigationRail
        Forced.Drawer -> NavigationSuiteType.NavigationDrawer
    }
    val density = LocalDensity.current
    val widthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }.value.toInt()
    val widthClass = when {
        widthDp < 600 -> "compact"
        widthDp < 840 -> "medium"
        else -> "expanded"
    }
    fun typeName(t: NavigationSuiteType) = when (t) {
        NavigationSuiteType.NavigationBar -> "barre en bas"
        NavigationSuiteType.NavigationRail -> "rail latéral"
        NavigationSuiteType.NavigationDrawer -> "tiroir permanent"
        else -> t.toString()
    }
    val caption = if (forced == Forced.Auto) {
        "Largeur $widthDp dp → $widthClass → ${typeName(layoutType)} (automatique)"
    } else {
        "Largeur $widthDp dp ($widthClass) → ${typeName(layoutType)} (forcé ; auto aurait choisi : ${typeName(auto)})"
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            Dest.entries.forEach { dest ->
                item(
                    selected = dest == selected,
                    onClick = { selected = dest },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        caption,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Forced.entries.forEach { f ->
                            FilterChip(selected = forced == f, onClick = { forced = f }, label = { Text(f.label) })
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                DestinationContent(selected)
            }
        }
    }
}

@Composable
private fun DestinationContent(dest: Dest) {
    val (container, onContainer) = when (dest) {
        Dest.Home -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Dest.Favorites -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Dest.Profile -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Dest.Settings -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = container, contentColor = onContainer, shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(dest.icon, contentDescription = null, modifier = Modifier.padding(8.dp))
                Text(dest.label, style = MaterialTheme.typography.headlineMedium)
                Text(
                    when (dest) {
                        Dest.Home -> "Contenu de l'accueil."
                        Dest.Favorites -> "Vos éléments préférés."
                        Dest.Profile -> "Informations du profil."
                        Dest.Settings -> "Options de l'application."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(Icons.Filled.Star, contentDescription = null)
            }
        }
    }
}
