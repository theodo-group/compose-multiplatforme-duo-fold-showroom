package dev.showroom.fold.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

/**
 * Material 3 keeps Back and screen actions in the top app bar at every window size. A navigation rail
 * only holds top-level destinations (plus an optional menu / FAB), so the host chrome never moves to
 * the side on Android: screens with destinations draw their own rail or bottom bar
 * (NavigationSuiteScaffold: bottom bar in compact width or tabletop, rail on the leading edge
 * otherwise).
 */
@Composable
actual fun rememberPlatformBarPlacement(): State<BarPlacement> =
    remember { mutableStateOf<BarPlacement>(BarPlacement.Top) }
