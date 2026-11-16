package dev.showroom.fold.demos.platform

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.window.WindowSdkExtensions
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import dev.showroom.fold.core.ShowroomTheme
import java.util.concurrent.Executor

private const val REAR_DISPLAY = "rear-display"
private const val REAR_PRESENTATION = "rear-presentation"

@Composable
internal actual fun rememberDisplayModesController(): CapabilityController {
    val activity = LocalContext.current.findHostActivity()
    val controller = remember(activity) { AndroidDisplayModesController(activity) }
    if (activity != null) {
        LaunchedEffect(controller) { controller.observe() }
    }
    DisposableEffect(controller) {
        // The presentation lives in another window bound to this screen: end it with the demo.
        // Rear-display mode is left alone (the activity may be recreated while it moves display);
        // it is closed by the user or by the system when the device is unfolded.
        onDispose { controller.closePresentation() }
    }
    return controller
}

@OptIn(ExperimentalWindowApi::class)
private class AndroidDisplayModesController(private val activity: Activity?) : CapabilityController {
    private val mainExecutor = Executor { Handler(Looper.getMainLooper()).post(it) }
    private val windowAreaController: WindowAreaController? =
        runCatching { WindowAreaController.getOrCreate() }.getOrNull()

    // WindowAreaInfo is mutable and may be re-emitted as the same instance: always notify.
    private var areas by mutableStateOf<List<WindowAreaInfo>?>(null, neverEqualPolicy())
    private var presenter: WindowAreaSessionPresenter? = null
    private var rearDisplaySession: WindowAreaSession? = null
    private var presentationVisible by mutableStateOf<Boolean?>(null)
    private val log = mutableStateListOf<String>()
    private val extensionVersion: Int = runCatching { WindowSdkExtensions.getInstance().extensionVersion }.getOrDefault(0)

    suspend fun observe() {
        val controller = windowAreaController ?: return
        controller.windowAreaInfos.collect { infos ->
            if (areas == null || areas?.size != infos.size) {
                log.add(0, "windowAreaInfos : ${infos.size} zone(s)")
            }
            areas = infos
        }
    }

    private val rearArea: WindowAreaInfo?
        get() = areas?.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }

    override val capabilities: List<Capability>
        get() = listOf(
            capabilityFor(
                id = REAR_DISPLAY,
                title = "Mode écran arrière",
                api = "transferActivityToWindowArea(…)",
                operation = WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA,
                availableText = "Toute l'activité passe sur l'écran externe (par exemple pour cadrer un selfie avec la caméra principale). " +
                    "Retournez l'appareil après le démarrage.",
                startLabel = "Passer sur l'écran arrière",
                stopLabel = "Revenir à l'écran interne",
            ),
            capabilityFor(
                id = REAR_PRESENTATION,
                title = "Double écran (présentation)",
                api = "presentContentOnWindowArea(…)",
                operation = WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA,
                availableText = "Une vue supplémentaire s'affiche sur l'écran externe pendant que l'écran interne reste actif.",
                startLabel = "Présenter sur l'écran externe",
                stopLabel = "Arrêter la présentation",
            ),
        )

    private fun capabilityFor(
        id: String,
        title: String,
        api: String,
        operation: WindowAreaCapability.Operation,
        availableText: String,
        startLabel: String,
        stopLabel: String,
    ): Capability {
        val area = rearArea
        val status = area?.getCapability(operation)?.status
        val level = when {
            activity == null -> CapabilityLevel.Unknown
            areas == null -> CapabilityLevel.Unknown
            area == null -> CapabilityLevel.Unsupported
            status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE -> CapabilityLevel.Active
            status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE -> CapabilityLevel.Available
            status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> CapabilityLevel.Unavailable
            status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED -> CapabilityLevel.Unsupported
            else -> CapabilityLevel.Unknown
        }
        val reason = when (level) {
            CapabilityLevel.Unknown -> if (activity == null) {
                "Pas d'activité hôte : impossible d'interroger WindowManager."
            } else {
                "En attente du premier état de WindowAreaController…"
            }
            CapabilityLevel.Unsupported -> if (area == null) {
                "Aucune zone d'affichage arrière déclarée par l'appareil (téléphone non pliable, émulateur, " +
                    "ou extensions WindowManager v$extensionVersion trop anciennes)."
            } else {
                "Statut UNSUPPORTED : l'appareil a un écran arrière mais ne prend pas en charge cette opération."
            }
            CapabilityLevel.Unavailable ->
                "Statut UNAVAILABLE : supporté, mais pas maintenant. Il faut l'appareil déplié, et la zone ne doit pas être occupée " +
                    "par une autre session."
            CapabilityLevel.Available -> availableText
            CapabilityLevel.Active -> if (id == REAR_PRESENTATION && presentationVisible == false) {
                "Statut ACTIVE, mais l'écran externe est éteint (onContainerVisibilityChanged = false)."
            } else {
                "Statut ACTIVE : une session est en cours."
            }
        }
        return Capability(id, title, api, level, reason, startLabel, stopLabel)
    }

    override val facts: List<Pair<String, String>>
        get() = buildList {
            add("Android" to "API ${Build.VERSION.SDK_INT}")
            add("Extensions WindowManager" to if (extensionVersion > 0) "v$extensionVersion" else "absentes")
            add("Zones d'affichage" to (areas?.size?.toString() ?: "…"))
            areas.orEmpty().forEachIndexed { index, info ->
                val bounds = info.metrics.bounds
                add("Zone $index" to "${info.type} ${bounds.width()}×${bounds.height()} px")
            }
            presentationVisible?.let { add("Écran externe visible" to if (it) "oui" else "non") }
        }

    override val events: List<String> get() = log

    override val notice: String?
        get() = when {
            windowAreaController == null -> "WindowAreaController indisponible sur cet appareil."
            areas?.isEmpty() == true ->
                "Cet appareil ne déclare aucun écran externe utilisable : c'est le résultat normal sur émulateur ou " +
                    "téléphone classique. Sur Pixel Fold / Pixel 9 Pro Fold (Android 14+), les deux modes apparaissent ici."
            else -> null
        }

    override fun start(id: String) {
        val host = activity ?: return
        val controller = windowAreaController ?: return
        val token = rearArea?.token ?: return
        when (id) {
            REAR_DISPLAY -> controller.transferActivityToWindowArea(
                token, host, mainExecutor,
                object : WindowAreaSessionCallback {
                    override fun onSessionStarted(session: WindowAreaSession) {
                        rearDisplaySession = session
                        log.add(0, "Mode écran arrière démarré : retournez l'appareil")
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        rearDisplaySession = null
                        log.add(0, "Mode écran arrière terminé" + (t?.let { " : ${it.message ?: it}" } ?: ""))
                    }
                },
            )

            REAR_PRESENTATION -> controller.presentContentOnWindowArea(
                token, host, mainExecutor,
                object : WindowAreaPresentationSessionCallback {
                    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                        presenter = session
                        session.setContentView(outerComposeView(host, session))
                        log.add(0, "Présentation démarrée sur l'écran externe")
                    }

                    override fun onContainerVisibilityChanged(isVisible: Boolean) {
                        presentationVisible = isVisible
                        log.add(0, "Écran externe ${if (isVisible) "visible" else "masqué"}")
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        presenter = null
                        presentationVisible = null
                        log.add(0, "Présentation terminée" + (t?.let { " : ${it.message ?: it}" } ?: ""))
                    }
                },
            )
        }
    }

    override fun stop(id: String) {
        when (id) {
            REAR_DISPLAY -> {
                close(rearDisplaySession ?: activeSession(WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA))
                rearDisplaySession = null
            }

            REAR_PRESENTATION -> closePresentation()
        }
    }

    fun closePresentation() {
        close(presenter ?: activeSession(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA))
        presenter = null
    }

    /**
     * The session we did not get through a callback (status ACTIVE but `onSessionStarted` never
     * fired, as on the emulator). `getActiveSession` throws inside the library when the platform
     * has no session to hand back, so a failure here means "nothing to close".
     */
    private fun activeSession(operation: WindowAreaCapability.Operation): WindowAreaSession? =
        runCatching { rearArea?.getActiveSession(operation) }
            .onFailure { log.add(0, "Aucune session à fermer (${it::class.simpleName})") }
            .getOrNull()

    private fun close(session: WindowAreaSession?) {
        runCatching { session?.close() }
            .onFailure { log.add(0, "Fermeture impossible : ${it.message ?: it::class.simpleName}") }
    }
}

/**
 * The outer-display window has no lifecycle owner of its own: borrow the activity's, otherwise
 * ComposeView refuses to compose.
 */
@OptIn(ExperimentalWindowApi::class)
private fun outerComposeView(activity: Activity, presenter: WindowAreaSessionPresenter): ComposeView =
    ComposeView(presenter.context).apply {
        if (activity is ComponentActivity) {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
        }
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            ShowroomTheme { OuterDisplayContent(OuterDisplayMessage.text) }
        }
    }
