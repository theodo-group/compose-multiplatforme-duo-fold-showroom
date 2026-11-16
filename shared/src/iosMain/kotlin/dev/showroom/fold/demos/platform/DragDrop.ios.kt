package dev.showroom.fold.demos.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.Foundation.NSItemProviderWritingProtocol
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.UIDragItem
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val PLAIN_TEXT_UTI = "public.plain-text"

@OptIn(ExperimentalComposeUiApi::class, BetaInteropApi::class)
internal actual fun plainTextTransferData(text: String): DragAndDropTransferData {
    val string = NSString.create(string = text)
    // NSString adopts NSItemProviderWriting in an Objective-C category, which the Kotlin/Native
    // bindings do not model: the cast is checked at runtime. Fallback: register it as UTF-8 text.
    val provider = (string as Any as? NSItemProviderWritingProtocol)?.let { NSItemProvider(`object` = it) }
        ?: NSItemProvider(item = string, typeIdentifier = "public.utf8-plain-text")
    val item = UIDragItem(itemProvider = provider).apply {
        // In-app fast path: drops inside this app read the Kotlin string directly.
        localObject = text
    }
    return DragAndDropTransferData(listOf(item))
}

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun DragAndDropEvent.hasPlainText(): Boolean =
    items.any { it.localObject is String || it.itemProvider.hasItemConformingToTypeIdentifier(PLAIN_TEXT_UTI) }

@OptIn(ExperimentalComposeUiApi::class, BetaInteropApi::class)
internal actual fun DragAndDropEvent.readPlainText(onResult: (DroppedText?) -> Unit) {
    val dragItems = items
    val local = dragItems.mapNotNull { it.localObject as? String }
    if (local.isNotEmpty()) {
        onResult(DroppedText(local.joinToString("\n"), fromThisApp = true))
        return
    }
    val provider = dragItems.firstOrNull { it.itemProvider.hasItemConformingToTypeIdentifier(PLAIN_TEXT_UTI) }?.itemProvider
    if (provider == null) {
        onResult(null)
        return
    }
    // Another app: the text is loaded asynchronously, off the main thread.
    // (loadObjectOfClass(NSString) would be nicer, but its Class<NSItemProviderReading> parameter is
    // awkward from Kotlin/Native; the type-identifier API returns NSString or NSData.)
    provider.loadItemForTypeIdentifier(PLAIN_TEXT_UTI, options = null) { value, _ ->
        // Typed as NSSecureCoding by the bindings; at runtime it is an NSString, NSData or NSURL.
        val raw: Any? = value
        val text = when (raw) {
            is String -> raw
            is NSData -> NSString.create(data = raw, encoding = NSUTF8StringEncoding)?.toString()
            is NSURL -> raw.absoluteString
            else -> null
        }
        dispatch_async(dispatch_get_main_queue()) {
            onResult(text?.let { DroppedText(it, fromThisApp = false) })
        }
    }
}

internal actual val dragPayloadDescription: String =
    "UIDragItem + NSItemProvider(NSString) « public.plain-text », localObject pour le chemin interne."
