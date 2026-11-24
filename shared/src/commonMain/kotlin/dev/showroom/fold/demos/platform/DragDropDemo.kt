package dev.showroom.fold.demos.platform

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.DemoBottomInsets
import dev.showroom.fold.core.Sharing

// --- The only platform-specific part: building the payload and reading dropped text. ---

/** Plain-text payload, draggable inside the app and to other apps. */
internal expect fun plainTextTransferData(text: String): DragAndDropTransferData

/** Whether the drag session carries something readable as plain text. */
internal expect fun DragAndDropEvent.hasPlainText(): Boolean

/** Text dropped on a target, and whether it came from this app or another one. */
internal class DroppedText(val text: String, val fromThisApp: Boolean)

/**
 * Reads the dropped text. Synchronous on Android (ClipData is in the event); asynchronous on iOS
 * when the text comes from another app (NSItemProvider loads it), hence the callback, always on the
 * main thread.
 */
internal expect fun DragAndDropEvent.readPlainText(onResult: (DroppedText?) -> Unit)

/** One-line description of the platform payload, shown in the UI. */
internal expect val dragPayloadDescription: String

private val dragDropCosts = listOf(
    NativeCost(
        platform = PlatformKind.Android,
        apis = listOf(
            "DragAndDropTransferData(ClipData.newPlainText(…), localState, View.DRAG_FLAG_GLOBAL)",
            "DragAndDropEvent.toAndroidDragEvent().clipData, mimeTypes()",
        ),
        effort = "≈ 35 lignes Kotlin androidMain. Aucune permission pour du texte brut " +
            "(requestDragAndDropPermissions n'est nécessaire que pour des URI content://).",
        gotchas = listOf("Sans DRAG_FLAG_GLOBAL, le glisser reste confiné à l'app."),
    ),
    NativeCost(
        platform = PlatformKind.Ios,
        apis = listOf(
            "DragAndDropTransferData(listOf(UIDragItem(NSItemProvider(NSString))))",
            "UIDragItem.localObject, NSItemProvider.loadItemForTypeIdentifier(public.plain-text)",
        ),
        effort = "≈ 70 lignes Kotlin iosMain, appels UIKit directs, aucune ligne Swift.",
        gotchas = listOf(
            "Le texte venant d'une autre app arrive de façon asynchrone (callback hors thread principal).",
            "Le constructeur iOS de DragAndDropTransferData est @ExperimentalComposeUiApi.",
        ),
    ),
)

val dragDropDemo = Demo(
    id = "drag-drop",
    title = "Glisser-déposer",
    summary = "Glisser des éléments d'un volet à l'autre avec les modificateurs communs de Compose, et échanger du texte avec d'autres apps en écran partagé.",
    sharing = Sharing.ExpectActual,
    notes = listOf(
        "Partagé : Modifier.dragAndDropSource / dragAndDropTarget, le retour visuel (survol, zone active) et toute la logique.",
        "Spécifique : la charge utile et la lecture du texte déposé, soit trois fonctions expect/actual (≈ 35 lignes Android, ≈ 70 lignes iOS).",
        "Android : ClipData texte + View.DRAG_FLAG_GLOBAL pour sortir de l'app ; lecture via toAndroidDragEvent().clipData.",
        "iOS : UIDragItem + NSItemProvider(NSString) ; localObject pour le chemin rapide interne, loadItemForTypeIdentifier (asynchrone) pour le texte d'une autre app.",
        "Démarrage par appui long sur les deux plateformes (détecteur par défaut de Compose). Entre apps : écran partagé Android, Split View ou deux fenêtres sur iPhone Duo.",
    ),
    content = { DragDropContent() },
)

private val sampleItems = listOf(
    "Charnière", "Écran externe", "Posture livre", "Mode chevalet", "Double volet", "Fenêtre adjacente",
)

@Composable
private fun DragDropContent() {
    val dropped = remember { mutableStateListOf<DroppedText>() }
    var dragInProgress by remember { mutableStateOf(false) }
    var hovering by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf<String?>(null) }

    val target = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) { dragInProgress = true }
            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) {
                dragInProgress = false
                hovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                // A drop ends the drag: iOS does not reliably deliver onEnded afterwards.
                dragInProgress = false
                hovering = false
                event.readPlainText { result ->
                    if (result == null || result.text.isBlank()) {
                        lastMessage = "Dépôt reçu, mais aucun texte lisible."
                    } else {
                        dropped.add(0, result)
                        lastMessage = null
                    }
                }
                return true
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(DemoBottomInsets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Appui long sur un élément, puis glissez-le dans la zone de dépôt. En écran partagé, glissez-le aussi " +
                    "vers une autre app, ou déposez ici du texte venant d'ailleurs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SourcePane(Modifier.weight(1f))
                    DropPane(dropped, dragInProgress, hovering, lastMessage, target, { dropped.clear() }, Modifier.weight(1f))
                }
            } else {
                SourcePane(Modifier.fillMaxWidth())
                DropPane(dropped, dragInProgress, hovering, lastMessage, target, { dropped.clear() }, Modifier.fillMaxWidth())
            }
            Text(
                "Charge utile sur cette plateforme : $dragPayloadDescription",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NativeCostSection(dragDropCosts)
        }
    }
}

@Composable
private fun SourcePane(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Éléments à glisser")
        sampleItems.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .dragAndDropSource { _ -> plainTextTransferData(item) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⠿", color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                    Spacer(Modifier.width(12.dp))
                    Text(item, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun DropPane(
    dropped: List<DroppedText>,
    dragInProgress: Boolean,
    hovering: Boolean,
    lastMessage: String?,
    target: DragAndDropTarget,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        when {
            hovering -> scheme.primaryContainer
            dragInProgress -> scheme.surfaceVariant
            else -> Color.Transparent
        },
    )
    val borderColor = when {
        hovering -> scheme.primary
        dragInProgress -> scheme.primary.copy(alpha = 0.6f)
        else -> scheme.outline
    }
    val shape = RoundedCornerShape(16.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle("Zone de dépôt") }
            if (dropped.isNotEmpty()) TextButton(onClick = onClear) { Text("Vider") }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .background(background, shape)
                .border(if (hovering) 3.dp else 2.dp, borderColor, shape)
                .dragAndDropTarget(shouldStartDragAndDrop = { it.hasPlainText() }, target = target)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                when {
                    hovering -> "Relâchez pour déposer"
                    dragInProgress -> "Texte détecté : glissez jusqu'ici"
                    dropped.isEmpty() -> "Déposez un élément ou du texte ici"
                    else -> "${dropped.size} élément(s) déposé(s)"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (hovering) scheme.onPrimaryContainer else scheme.onSurface,
            )
            lastMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
            dropped.forEach { entry ->
                Row(
                    Modifier.fillMaxWidth().background(scheme.surface, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 3)
                    Spacer(Modifier.width(8.dp))
                    OriginTag(if (entry.fromThisApp) "cette app" else "autre app", highlighted = !entry.fromThisApp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun OriginTag(text: String, highlighted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (highlighted) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier
            .background(if (highlighted) scheme.tertiaryContainer else scheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
