package dev.showroom.fold.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Host of every demo: a top bar (back, title, sharing badge, info sheet). The demo content fills the
 * rest; insets are consumed here. The fold is always the real one (see LocalFoldState).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DemoScreen(demo: Demo, onBack: () -> Unit) {
    var showInfo by rememberSaveable { mutableStateOf(false) }

    // Shared back handling: Android system back / predictive back, iOS edge swipe.
    BackHandler(enabled = true, onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(demo.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        SharingBadge(demo.sharing)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Partagé ou spécifique ?")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            demo.content()
        }
    }

    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(demo.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                SharingBadge(demo.sharing)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = demo.sharing.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                demo.notes.forEach { note ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
