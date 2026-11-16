package dev.showroom.fold.demos.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import dev.showroom.fold.core.ShowroomTheme
import dev.showroom.fold.fold.FoldSource
import dev.showroom.fold.fold.LocalFoldState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import platform.Foundation.NSClassFromString
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIScene
import platform.UIKit.UIViewController

/**
 * Implemented in Swift (`iosApp/iosApp/OuterDisplayAccessory.swift`) and registered at startup.
 *
 * Why Swift: `UISceneAccessory` / SwiftUI `CameraCaptureAccessory` ship in the iOS 27.1 SDK, but the
 * `platform.UIKit` bindings bundled with Kotlin/Native 2.4.21 are generated from the iOS 26.4 SDK,
 * so these symbols cannot be called from Kotlin.
 */
interface OuterDisplayHost {
    /** Enables the camera-capture scene accessory and starts the camera session it requires (or stops both). */
    fun setOuterDisplayActive(active: Boolean)
}

/** Kotlin side of the bridge. Swift registers its host and reports what the system says. */
object OuterDisplayBridge {
    internal var host by mutableStateOf<OuterDisplayHost?>(null)
        private set
    internal var enabled by mutableStateOf(false)
        private set
    internal var accessoryAvailable by mutableStateOf<Boolean?>(null)
        private set
    internal var cameraRunning by mutableStateOf(false)
        private set
    internal var cameraError by mutableStateOf<String?>(null)
        private set
    internal val log = mutableStateListOf<String>()

    fun register(host: OuterDisplayHost) {
        this.host = host
    }

    /** From `CameraCaptureAccessory.onAvailabilityChange`. */
    fun reportAccessoryAvailability(available: Boolean) {
        accessoryAvailable = available
        log.add(0, "onAvailabilityChange : ${if (available) "disponible" else "indisponible"}")
    }

    fun reportEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun reportCamera(running: Boolean, error: String?) {
        cameraRunning = running
        cameraError = error
        log.add(0, error?.let { "Caméra : $it" } ?: "Session caméra ${if (running) "démarrée" else "arrêtée"}")
    }
}

/** Content of the accessory scene, called from Swift: the same composable as on Android. */
@Suppress("FunctionName", "unused")
fun OuterDisplayViewController(): UIViewController = ComposeUIViewController {
    ShowroomTheme { OuterDisplayContent(OuterDisplayMessage.text) }
}

private const val CAMERA_ACCESSORY = "camera-accessory"
private const val MOVE_APP = "move-app"

/** Snapshot of `UIApplication.connectedScenes`, refreshed by polling (shared with the multi-window demo). */
internal data class SceneInfo(val role: String, val state: String, val title: String?)

internal fun readConnectedScenes(): List<SceneInfo> =
    UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIScene }.map { scene ->
        SceneInfo(
            role = scene.session.role ?: "",
            // UISceneActivationState raw values: unattached -1, foregroundActive 0, foregroundInactive 1, background 2.
            state = when (scene.activationState) {
                -1L -> "unattached"
                0L -> "foregroundActive"
                1L -> "foregroundInactive"
                2L -> "background"
                else -> "?(${scene.activationState})"
            },
            title = scene.title,
        )
    }

internal fun shortRole(role: String): String =
    role.removePrefix("UIWindowSceneSessionRole").removePrefix("UISceneSessionRole").ifEmpty { role }

@OptIn(ExperimentalForeignApi::class)
internal val iosVersionString: String
    get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        "$majorVersion.$minorVersion.$patchVersion"
    }

@OptIn(ExperimentalForeignApi::class)
private val isIos271OrLater: Boolean
    get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        majorVersion > 27 || (majorVersion == 27L && minorVersion >= 1)
    }

@Composable
internal actual fun rememberDisplayModesController(): CapabilityController {
    val fold = LocalFoldState.current
    val deviceHasHinge = fold.hasHinge && fold.source == FoldSource.Platform
    val controller = remember { IosDisplayModesController() }
    SideEffect { controller.deviceHasHinge = deviceHasHinge }
    LaunchedEffect(controller) {
        while (true) {
            controller.scenes = readConnectedScenes()
            delay(1_000)
        }
    }
    return controller
}

@OptIn(BetaInteropApi::class)
private class IosDisplayModesController : CapabilityController {
    var deviceHasHinge by mutableStateOf(false)
    var scenes by mutableStateOf<List<SceneInfo>>(emptyList())

    // Runtime lookup: works whatever the Kotlin/Native bindings know about.
    private val sceneAccessoryClassExists = NSClassFromString("UISceneAccessory") != null
    private val bridge = OuterDisplayBridge

    private val accessorySceneConnected: Boolean
        get() = scenes.any { it.role.contains("CameraCaptureAccessory") }

    override val capabilities: List<Capability>
        get() = listOf(cameraAccessory(), moveApp())

    private fun cameraAccessory(): Capability {
        val base = Capability(
            id = CAMERA_ACCESSORY,
            title = "Accessoire « capture caméra »",
            api = "CameraCaptureAccessory (SwiftUI, iOS 27.1)",
            level = CapabilityLevel.Unknown,
            reason = "",
            startLabel = "Activer (démarre la caméra)",
            stopLabel = "Désactiver",
        )
        return when {
            !isIos271OrLater || !sceneAccessoryClassExists -> base.copy(
                level = CapabilityLevel.Unsupported,
                reason = "iOS $iosVersionString : les accessoires de scène caméra arrivent avec iOS 27.1.",
                canStart = false, canStop = false,
            )
            bridge.host == null -> base.copy(
                level = CapabilityLevel.Unsupported,
                reason = "Le pont Swift (OuterDisplayHost) n'est pas enregistré : l'accessoire ne peut être déclaré qu'en Swift.",
                canStart = false, canStop = false,
            )
            !bridge.enabled -> base.copy(
                level = CapabilityLevel.Unknown,
                reason = "iOS ne publie la disponibilité qu'une fois l'accessoire enregistré et une capture caméra active. " +
                    "Activez pour la lire" + if (deviceHasHinge) "." else " (aucune charnière détectée : il restera sûrement indisponible).",
                canStart = true, canStop = false,
            )
            bridge.cameraError != null -> base.copy(
                level = CapabilityLevel.Unavailable,
                reason = "Capture caméra impossible : ${bridge.cameraError}. Sans capture active, iOS n'affiche pas l'accessoire.",
                canStart = false, canStop = true,
            )
            bridge.accessoryAvailable == true -> base.copy(
                level = CapabilityLevel.Active,
                reason = if (accessorySceneConnected) {
                    "Scène accessoire connectée : le contenu partagé est affiché sur l'écran externe."
                } else {
                    "Le système déclare l'accessoire disponible ; il choisit seul quand le présenter."
                },
                canStart = false, canStop = true,
            )
            else -> base.copy(
                level = CapabilityLevel.Unavailable,
                reason = if (bridge.cameraRunning) {
                    "Caméra active mais le système ne propose pas l'accessoire (pas d'écran externe sur cet appareil, ou position non compatible)."
                } else {
                    "En attente du démarrage de la caméra…"
                },
                canStart = false, canStop = true,
            )
        }
    }

    private fun moveApp() = Capability(
        id = MOVE_APP,
        title = "Mode écran arrière (déplacer l'app)",
        api = "—",
        level = CapabilityLevel.Unsupported,
        reason = "Aucune API publique iOS 27.1 ne déplace une scène vers l'écran externe ; " +
            "ExternalNonInteractiveAccessory (iOS 27.0) vise les écrans externes branchés, pas l'écran extérieur de l'iPhone Duo.",
    )

    override val facts: List<Pair<String, String>>
        get() = buildList {
            add("iOS" to iosVersionString)
            add("NSClassFromString(\"UISceneAccessory\")" to if (sceneAccessoryClassExists) "trouvée" else "absente")
            add("Pont Swift enregistré" to if (bridge.host != null) "oui" else "non")
            add("Charnière (UIHingeInteraction)" to if (deviceHasHinge) "oui" else "non")
            add("Accessoire activé" to if (bridge.enabled) "oui" else "non")
            add("onAvailabilityChange" to (bridge.accessoryAvailable?.let { if (it) "true" else "false" } ?: "—"))
            add("Session caméra" to if (bridge.cameraRunning) "active" else "arrêtée")
            add("Scènes connectées" to scenes.joinToString { shortRole(it.role) }.ifEmpty { "—" })
        }

    override val events: List<String> get() = bridge.log

    override val notice: String?
        get() = when {
            !isIos271OrLater -> "iOS $iosVersionString : l'écran externe de l'iPhone Duo n'est accessible qu'à partir d'iOS 27.1."
            !deviceHasHinge ->
                "Aucune charnière détectée : cet appareil n'a pas d'écran extérieur. Les statuts ci-dessous sont ceux que renvoie réellement le système."
            else -> null
        }

    override fun start(id: String) {
        if (id == CAMERA_ACCESSORY) bridge.host?.setOuterDisplayActive(true)
    }

    override fun stop(id: String) {
        if (id == CAMERA_ACCESSORY) bridge.host?.setOuterDisplayActive(false)
    }
}
