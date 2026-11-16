package dev.showroom.fold.demos.platform

import android.content.ClipData
import android.content.ClipDescription
import android.view.View
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent

/** Marks drags started by this process: other apps never see a local state. */
private object ShowroomDragMarker

internal actual fun plainTextTransferData(text: String): DragAndDropTransferData =
    DragAndDropTransferData(
        clipData = ClipData.newPlainText("Fold Showroom", text),
        localState = ShowroomDragMarker,
        // Without this flag the drag never leaves the app.
        flags = View.DRAG_FLAG_GLOBAL,
    )

internal actual fun DragAndDropEvent.hasPlainText(): Boolean =
    mimeTypes().any { ClipDescription.compareMimeTypes(it, "text/*") }

internal actual fun DragAndDropEvent.readPlainText(onResult: (DroppedText?) -> Unit) {
    val dragEvent = toAndroidDragEvent()
    val clip = dragEvent.clipData
    val text = clip?.let { data ->
        (0 until data.itemCount).mapNotNull { data.getItemAt(it).text?.toString() }.joinToString("\n")
    }
    onResult(text?.let { DroppedText(it, fromThisApp = dragEvent.localState === ShowroomDragMarker) })
}

internal actual val dragPayloadDescription: String =
    "ClipData « text/plain » + View.DRAG_FLAG_GLOBAL (lisible par les autres apps)."
