package dev.showroom.fold.demos.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIScene
import platform.UIKit.UISceneActivationStateBackground
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneActivationStateForegroundInactive
import platform.UIKit.UISceneActivationStateUnattached
import platform.UIKit.UISceneSessionActivationRequest
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneActivationRequestOptions
import platform.UIKit.UIWindowSceneStandardPlacement
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.roundToInt

private const val MULTI_SCENE = "multi-scene"
private const val NEW_WINDOW = "new-window"
private const val ADJACENT_REQUEST = "adjacent-request"
private const val SIDE_BY_SIDE = "side-by-side"

/** A window narrower than this share of its screen is considered "next to something". */
private const val SPLIT_THRESHOLD = 0.9

/**
 * The documented gesture, from Apple's Tech Talk "Prepare your app for iPhone Duo" (111461,
 * chapter "Respect safe areas"): drag the app by its home indicator to one side of the inner
 * display, a drop area appears.
 */
private const val SIDE_BY_SIDE_GESTURE =
    "Geste (documenté par Apple, Tech Talk « Prepare your app for iPhone Duo ») : sur l'écran interne, " +
        "faites glisser la fenêtre par l'indicateur d'accueil (la barre en bas) vers un bord de l'écran ; " +
        "une zone de dépôt apparaît, relâchez.\nObservé sur le simulateur iPhone Duo : l'autre moitié affiche alors " +
        "l'écran d'accueil ; touchez l'icône Fold Showroom pour y ramener l'autre fenêtre."

@Composable
internal actual fun rememberMultiWindowController(): CapabilityController {
    val controller = remember { IosMultiWindowController() }
    LaunchedEffect(controller) {
        // No single "scenes changed" callback reaches shared code: poll once a second.
        while (true) {
            controller.refresh()
            delay(1_000)
        }
    }
    return controller
}

/**
 * One connected scene of this app: role, activation state and, for window scenes, its size
 * against its screen. Polled, since no "scenes changed" callback reaches shared code.
 */
internal data class SceneSnapshot(
    val role: String,
    val state: String,
    val width: Double?,
    val height: Double?,
    val screenWidth: Double?,
) {
    val isWindow: Boolean get() = width != null
    val isForeground: Boolean get() = state.startsWith("foreground")
    val widthShare: Double get() = if (width != null && screenWidth != null && screenWidth > 0) width / screenWidth else 1.0
    val isNarrow: Boolean get() = isWindow && widthShare < SPLIT_THRESHOLD
    fun describe(): String = buildString {
        append(shortRole(role)).append(" · ").append(state)
        if (width != null && height != null) {
            append(" · ${width.roundToInt()}×${height.roundToInt()} pt (${(widthShare * 100).roundToInt()} % de la largeur)")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun readSceneSnapshots(): List<SceneSnapshot> =
    UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIScene }.map { scene ->
        val window = scene as? UIWindowScene
        val size = window?.coordinateSpace?.bounds?.useContents { size.width to size.height }
        SceneSnapshot(
            role = scene.session.role ?: "",
            state = when (scene.activationState) {
                UISceneActivationStateUnattached -> "unattached"
                UISceneActivationStateForegroundActive -> "foregroundActive"
                UISceneActivationStateForegroundInactive -> "foregroundInactive"
                UISceneActivationStateBackground -> "background"
                else -> "?(${scene.activationState})"
            },
            width = size?.first,
            height = size?.second,
            screenWidth = window?.screen?.bounds?.useContents { this.size.width },
        )
    }

private class IosMultiWindowController : CapabilityController {
    private val log = mutableStateListOf<String>()
    private var scenes by mutableStateOf<List<SceneSnapshot>>(emptyList())
    private var openSessions by mutableIntStateOf(0)
    private var supportsMultipleScenes by mutableStateOf(false)
    private var lastRefusal by mutableStateOf<String?>(null)
    private var wasSideBySide = false

    private val plistSupportsMultipleScenes: Boolean? =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("UIApplicationSceneManifest") as? Map<*, *>)
            ?.get("UIApplicationSupportsMultipleScenes") as? Boolean

    fun refresh() {
        val app = UIApplication.sharedApplication
        val previous = scenes.size
        val previousWindows = scenes.count { it.isWindow }
        scenes = readSceneSnapshots()
        openSessions = app.openSessions.size
        supportsMultipleScenes = app.supportsMultipleScenes
        if (previous != 0 && previous != scenes.size) {
            log.add(0, "connectedScenes : $previous → ${scenes.size}")
            if (scenes.count { it.isWindow } > previousWindows) {
                // Say where the system actually put the new window: it is its decision, not ours.
                val placement = if (scenes.any { it.isWindow && it.isForeground && !it.isNarrow }) {
                    "plein écran, par-dessus (placement choisi par le système)"
                } else {
                    "dans une partie de l'écran (placement choisi par le système)"
                }
                log.add(0, "Nouvelle fenêtre ouverte : $placement")
            }
        }
        val now = sideBySide
        if (now != wasSideBySide && previous != 0) {
            log.add(0, if (now) "Deux fenêtres de l'app côte à côte (disposées par l'utilisateur)" else "Fin du côte à côte")
        }
        wasSideBySide = now
    }

    private val windowScenes: List<SceneSnapshot>
        get() = scenes.filter { it.role.contains("Application") }

    /** Two foreground windows of this app, each narrower than the screen: the user arranged them. */
    private val sideBySide: Boolean
        get() = scenes.filter { it.isWindow && it.isForeground }.let { fg -> fg.size >= 2 && fg.all { it.isNarrow } }

    override val capabilities: List<Capability>
        get() {
            val multi = Capability(
                id = MULTI_SCENE,
                title = "Plusieurs scènes",
                api = "UIApplication.supportsMultipleScenes",
                level = when {
                    !supportsMultipleScenes -> CapabilityLevel.Unsupported
                    windowScenes.size > 1 -> CapabilityLevel.Active
                    else -> CapabilityLevel.Available
                },
                reason = when {
                    plistSupportsMultipleScenes != true ->
                        "UIApplicationSupportsMultipleScenes absent de l'Info.plist : une seule fenêtre possible."
                    !supportsMultipleScenes ->
                        "L'Info.plist l'autorise, mais ce modèle d'iPhone n'affiche qu'une fenêtre (multi-fenêtre : iPad et iPhone Duo)."
                    windowScenes.size > 1 -> "${windowScenes.size} fenêtres de l'app sont connectées."
                    else -> "Une seule fenêtre pour l'instant."
                },
            )
            val newWindow = Capability(
                id = NEW_WINDOW,
                title = "Nouvelle fenêtre",
                api = "activateSceneSessionForRequest + UIWindowSceneStandardPlacement",
                level = if (supportsMultipleScenes) CapabilityLevel.Available else CapabilityLevel.Unsupported,
                reason = buildString {
                    append(
                        if (supportsMultipleScenes) {
                            "Demande une nouvelle scène au système, avec le placement standard (le seul autre choix sur iPhone, " +
                                "« prominent », la met au premier plan). Sur iPhone Duo elle ne peut s'ouvrir que sur l'écran " +
                                "interne, et le système peut refuser."
                        } else {
                            "Indisponible : supportsMultipleScenes = false."
                        },
                    )
                    lastRefusal?.let { append("\nDernier refus : $it") }
                },
                startLabel = "Demander une nouvelle fenêtre",
            )
            val adjacentRequest = Capability(
                id = ADJACENT_REQUEST,
                title = "Demander « à côté »",
                api = "aucune API iOS (UIWindowScenePlacement : standard, prominent)",
                level = CapabilityLevel.Unsupported,
                reason = "Contrairement à FLAG_ACTIVITY_LAUNCH_ADJACENT sur Android, iOS 27 ne permet pas à l'app de demander " +
                    "qu'une fenêtre s'ouvre à côté d'une autre : le SDK n'a que les placements standard et prominent, " +
                    "WindowPlacement (SwiftUI) est indisponible sur iOS, et Apple confirme qu'une scène ne peut pas se " +
                    "positionner à gauche ou à droite. Le système ouvre la fenêtre, l'utilisateur l'arrange.",
            )
            val sideBySideCap = Capability(
                id = SIDE_BY_SIDE,
                title = "Côte à côte (geste de l'utilisateur)",
                api = "UIWindowScene.coordinateSpace.bounds vs screen.bounds",
                level = when {
                    !supportsMultipleScenes -> CapabilityLevel.Unsupported
                    sideBySide -> CapabilityLevel.Active
                    else -> CapabilityLevel.Available
                },
                reason = buildString {
                    append(
                        when {
                            !supportsMultipleScenes -> "Pas de multi-fenêtre sur ce modèle."
                            sideBySide -> "Détecté : deux fenêtres de l'app partagent l'écran."
                            scenes.any { it.isForeground && it.isNarrow } ->
                                "Cette fenêtre partage l'écran avec une autre app (ou l'écran d'accueil)."
                            else -> "Pas encore : la fenêtre visible occupe toute la largeur."
                        },
                    )
                    if (supportsMultipleScenes) append("\n").append(SIDE_BY_SIDE_GESTURE)
                },
            )
            return listOf(multi, newWindow, adjacentRequest, sideBySideCap)
        }

    override val facts: List<Pair<String, String>>
        get() = buildList {
            add("iOS" to iosVersionString)
            add("Info.plist UIApplicationSupportsMultipleScenes" to (plistSupportsMultipleScenes?.toString() ?: "absent"))
            add("supportsMultipleScenes" to supportsMultipleScenes.toString())
            add("connectedScenes" to scenes.size.toString())
            add("openSessions" to openSessions.toString())
            scenes.forEachIndexed { index, scene -> add("Scène $index" to scene.describe()) }
            add("Côte à côte détecté" to sideBySide.toString())
        }

    override val events: List<String> get() = log

    override val notice: String?
        get() = if (supportsMultipleScenes) {
            "iOS : l'app peut demander une nouvelle fenêtre, pas sa position. Elle s'ouvre par-dessus la première ; " +
                "c'est l'utilisateur qui les met côte à côte (geste ci-dessous)."
        } else {
            null
        }

    override fun start(id: String) {
        if (id != NEW_WINDOW) return
        val request = UISceneSessionActivationRequest.request()
        request.options = UIWindowSceneActivationRequestOptions().apply {
            placement = UIWindowSceneStandardPlacement.standardPlacement()
        }
        log.add(0, "activateSceneSessionForRequest envoyé (placement standard)")
        UIApplication.sharedApplication.activateSceneSessionForRequest(request) { error: NSError? ->
            val message = error?.let { "${it.localizedDescription} (${it.domain} ${it.code})" }
                ?: "refus sans détail"
            dispatch_async(dispatch_get_main_queue()) {
                lastRefusal = message
                log.add(0, "Refus du système : $message")
            }
        }
    }

    override fun stop(id: String) = Unit
}
