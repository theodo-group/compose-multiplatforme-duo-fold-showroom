package dev.showroom.fold.demos.platform

import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.util.Consumer
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.window.layout.WindowMetricsCalculator

private const val MULTI_WINDOW = "multi-window-mode"
private const val NEW_WINDOW = "new-window"

@Composable
internal actual fun rememberMultiWindowController(): CapabilityController {
    val activity = LocalActivity.current
    val controller = remember(activity) { AndroidMultiWindowController(activity) }

    // Window size or configuration changed (the activity is not recreated: see configChanges).
    val configuration = LocalConfiguration.current
    val containerSize = LocalWindowInfo.current.containerSize
    LaunchedEffect(controller, configuration, containerSize) { controller.refresh() }

    DisposableEffect(activity) {
        val listener = Consumer<MultiWindowModeChangedInfo> { info ->
            controller.log.add(0, "onMultiWindowModeChanged : ${info.isInMultiWindowMode}")
            controller.refresh()
        }
        (activity as? ComponentActivity)?.addOnMultiWindowModeChangedListener(listener)
        onDispose { (activity as? ComponentActivity)?.removeOnMultiWindowModeChangedListener(listener) }
    }
    return controller
}

private class AndroidMultiWindowController(private val activity: Activity?) : CapabilityController {
    val log = mutableStateListOf<String>()
    // Window metrics and the multi-window flag are not observable: bump this to re-read them.
    private var revision by mutableIntStateOf(0)
    private val inMultiWindow get() = activity?.isInMultiWindowMode == true

    fun refresh() {
        revision++
    }

    override val capabilities: List<Capability>
        get() {
            revision
            val multi = Capability(
                id = MULTI_WINDOW,
                title = "Mode multi-fenêtre",
                api = "Activity.isInMultiWindowMode",
                level = when {
                    activity == null -> CapabilityLevel.Unknown
                    inMultiWindow -> CapabilityLevel.Active
                    else -> CapabilityLevel.Available
                },
                reason = when {
                    activity == null -> "Pas d'activité hôte."
                    inMultiWindow -> "L'app partage l'écran (écran partagé, fenêtre libre ou bulle)."
                    else -> "Plein écran. android:resizeableActivity=\"true\" : l'app accepte l'écran partagé."
                },
            )
            val newWindow = Capability(
                id = NEW_WINDOW,
                title = "Nouvelle fenêtre à côté",
                api = "FLAG_ACTIVITY_LAUNCH_ADJACENT | NEW_TASK | MULTIPLE_TASK",
                level = if (activity == null) CapabilityLevel.Unknown else CapabilityLevel.Available,
                reason = "Lance une seconde tâche de la même activité ; le système la place à côté si l'écran le permet " +
                    "(pliable ouvert, tablette), sinon en plein écran.",
                startLabel = "Ouvrir une 2ᵉ instance",
            )
            return listOf(multi, newWindow)
        }

    override val facts: List<Pair<String, String>>
        get() {
            revision
            val host = activity ?: return emptyList()
            val calculator = WindowMetricsCalculator.getOrCreate()
            val current = calculator.computeCurrentWindowMetrics(host).bounds
            val maximum = calculator.computeMaximumWindowMetrics(host).bounds
            val tasks = runCatching {
                host.getSystemService(ActivityManager::class.java)?.appTasks?.size
            }.getOrNull()
            val share = if (maximum.width() * maximum.height() > 0) {
                (100L * current.width() * current.height() / (maximum.width().toLong() * maximum.height())).toInt()
            } else {
                0
            }
            return listOf(
                "isInMultiWindowMode" to inMultiWindow.toString(),
                "isInPictureInPictureMode" to host.isInPictureInPictureMode.toString(),
                "Fenêtre (px)" to "${current.width()}×${current.height()} @ ${current.left},${current.top}",
                "Maximum (px)" to "${maximum.width()}×${maximum.height()}",
                "Part de l'écran" to "$share %",
                "taskId de cette fenêtre" to host.taskId.toString(),
                "Tâches de l'app (appTasks)" to (tasks?.toString() ?: "?"),
            )
        }

    override val events: List<String> get() = log
    override val notice: String? get() = null

    override fun start(id: String) {
        val host = activity ?: return
        if (id != NEW_WINDOW) return
        val intent = Intent(host, host.javaClass).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
            )
        }
        try {
            host.startActivity(intent)
            log.add(0, "startActivity(LAUNCH_ADJACENT) envoyé depuis la tâche ${host.taskId}")
        } catch (e: ActivityNotFoundException) {
            log.add(0, "Refusé : ${e.message}")
        } catch (e: SecurityException) {
            log.add(0, "Refusé : ${e.message}")
        }
    }

    override fun stop(id: String) = Unit
}
