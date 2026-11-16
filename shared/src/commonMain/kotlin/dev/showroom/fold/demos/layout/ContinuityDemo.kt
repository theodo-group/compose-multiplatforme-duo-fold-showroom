package dev.showroom.fold.demos.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing
import kotlin.time.TimeSource

val continuityDemo = Demo(
    id = "continuity",
    title = "Continuité d'état",
    summary = "Que survit au pliage, au dépliage et à la rotation : remember, rememberSaveable, texte, défilement et journal.",
    sharing = Sharing.Common,
    notes = listOf(
        "Le manifeste Android de ce projet déclare configChanges : l'activité n'est PAS recréée au pliage ni à la rotation, donc même un simple remember survit. C'est aussi le cas sur iOS, où il n'existe aucune recréation.",
        "Sans configChanges (le comportement par défaut d'Android), chaque rotation ou pliage recrée l'activité : seul l'état de rememberSaveable (ou d'un ViewModel) survit, le compteur remember repart de zéro.",
        "Le bouton « Recréer l'activité » (Android) reproduit cette recréation à la demande avec Activity.recreate(). C'est le seul appel natif de l'écran, un outil de démonstration : la continuité elle-même est du code commun.",
        "Tout le reste est commun. Les horodatages du journal sont relatifs (TimeSource.Monotonic depuis le lancement du processus) car il n'y a pas d'horloge murale en commonMain sans bibliothèque supplémentaire.",
        "rememberSaveable survit aussi à la mort du processus sur Android ; sur iOS, rien n'est restauré après un arrêt du processus, car il n'y a pas de mécanisme équivalent de restauration d'état.",
        "Le journal se base sur la taille de la fenêtre (snapshotFlow) : une rotation ou un pliage y ajoute une ligne, un clavier ouvert aussi si la hauteur change.",
    ),
    content = { Continuity() },
)

private val ProcessStart = TimeSource.Monotonic.markNow()

private fun elapsedLabel(): String {
    val total = ProcessStart.elapsedNow().inWholeSeconds
    val m = (total / 60).toString().padStart(2, '0')
    val s = (total % 60).toString().padStart(2, '0')
    return "+$m:$s"
}

private val JournalSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Continuity() {
    var plainCount by remember { mutableIntStateOf(0) }
    var savedCount by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var journal by rememberSaveable(stateSaver = JournalSaver) { mutableStateOf(emptyList<String>()) }
    var lastSize by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(Unit) {
        snapshotFlow { windowInfo.containerSize }.collect { px ->
            val w = with(density) { px.width.toDp() }.value.toInt()
            val h = with(density) { px.height.toDp() }.value.toInt()
            val now = "$w×$h"
            if (now != lastSize) {
                val line = if (lastSize.isEmpty()) "${elapsedLabel()} — départ : $now dp"
                else "${elapsedLabel()} — $lastSize → $now dp"
                journal = (listOf(line) + journal).take(30)
                lastSize = now
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val twoPanes = totalWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Text(
                    (if (twoPanes) "Largeur ${totalWidth.value.toInt()} dp → deux colonnes. " else "Largeur ${totalWidth.value.toInt()} dp → une colonne. ") +
                        "Le manifeste Android déclare configChanges : pivoter ou plier ne recrée pas l'activité, " +
                        "donc même remember survit, comme sur iOS où rien n'est jamais recréé. " +
                        "Le comportement par défaut d'Android est l'inverse : voir « Recréation d'activité ».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (twoPanes) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Controls(plainCount, { plainCount++ }, savedCount, { savedCount++ }, text) { text = it }
                        Journal(journal)
                    }
                    ScrollList(listState, Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Controls(plainCount, { plainCount++ }, savedCount, { savedCount++ }, text) { text = it }
                    Journal(journal)
                    ScrollList(listState, Modifier.fillMaxWidth().height(280.dp))
                }
            }
        }
    }
}

@Composable
private fun Controls(
    plain: Int,
    onPlain: () -> Unit,
    saved: Int,
    onSaved: () -> Unit,
    text: String,
    onText: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Compteurs", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlain, modifier = Modifier.weight(1f)) { Text("remember : $plain") }
                Button(onClick = onSaved, modifier = Modifier.weight(1f)) { Text("saveable : $saved") }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                label = { Text("Champ de texte (saveable)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    RecreationCard()
}

/** Makes the default Android behaviour visible on demand, instead of asking the reader to edit the manifest. */
@Composable
private fun RecreationCard() {
    val recreate = rememberActivityRecreator()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recréation d'activité", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (recreate != null) {
                Text(
                    "Sans configChanges, Android détruit et recrée l'activité à chaque rotation ou pliage. " +
                        "Ce bouton le fait à la demande : remember repart de 0 ; saveable, le texte, la liste et le journal restent.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = recreate, modifier = Modifier.fillMaxWidth()) { Text("Recréer l'activité") }
            } else {
                Text(
                    "Sur iOS, ni la rotation ni le pliage ne recréent l'écran : il n'y a rien à déclencher, remember survit toujours.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Journal(entries: List<String>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Journal des tailles (saveable)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (entries.isEmpty()) Text("…", style = MaterialTheme.typography.bodySmall)
            entries.take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (entries.size > 8) Text("… ${entries.size - 8} entrée(s) plus anciennes", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ScrollList(state: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Liste : premier élément visible n° ${state.firstVisibleItemIndex + 1} (décalage ${state.firstVisibleItemScrollOffset} px)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(12.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state) {
                itemsIndexed(List(200) { it }, key = { _, v -> v }) { _, v ->
                    Text("Élément ${v + 1}", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}
