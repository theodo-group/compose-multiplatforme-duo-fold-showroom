package dev.showroom.fold.fold

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * Android source of the fold: Jetpack WindowManager gives the fold geometry and state for the
 * current window, the hinge-angle sensor (API 30+) gives the continuous angle.
 */
@Composable
actual fun rememberPlatformFoldState(): State<FoldState> {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var feature by remember { mutableStateOf<FoldingFeature?>(null) }
    var angle by remember { mutableStateOf<Float?>(null) }
    val hingeSensor = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        } else {
            null
        }
    }

    if (activity != null) {
        LaunchedEffect(activity) {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
                feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            }
        }
    }

    DisposableEffect(hingeSensor) {
        val manager = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                angle = event.values.firstOrNull()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (hingeSensor != null) {
            manager?.registerListener(listener, hingeSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    return rememberUpdatedState(foldStateOf(feature, angle, hasHingeSensor = hingeSensor != null))
}

private fun foldStateOf(feature: FoldingFeature?, angle: Float?, hasHingeSensor: Boolean): FoldState {
    if (feature == null) {
        // No fold crosses the window: either a slab phone, or a foldable running on its outer display.
        if (!hasHingeSensor) return FoldState.None
        return FoldState(
            hasHinge = true,
            posture = if (angle != null && angle < 30f) Posture.Closed else Posture.None,
            hingeAngleDegrees = angle,
            source = FoldSource.Platform,
        )
    }
    val orientation = when (feature.orientation) {
        FoldingFeature.Orientation.VERTICAL -> HingeOrientation.Vertical
        else -> HingeOrientation.Horizontal
    }
    val posture = when {
        feature.state == FoldingFeature.State.HALF_OPENED && orientation == HingeOrientation.Vertical -> Posture.Book
        feature.state == FoldingFeature.State.HALF_OPENED -> Posture.Tabletop
        else -> Posture.Flat
    }
    val bounds = feature.bounds
    return FoldState(
        hasHinge = true,
        posture = posture,
        hingeAngleDegrees = angle,
        hingeBounds = Rect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
        ),
        orientation = orientation,
        isSeparating = feature.isSeparating,
        source = FoldSource.Platform,
    )
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
