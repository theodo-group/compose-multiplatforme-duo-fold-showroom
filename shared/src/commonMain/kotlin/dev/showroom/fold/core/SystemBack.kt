package dev.showroom.fold.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Returns an action that sends a back event through the same dispatcher as Android system back
 * and the iOS edge swipe, so the innermost enabled `NavigationBackHandler` (a demo's list/detail, a
 * Navigation 3 `NavDisplay`, then the host) handles it first. Use it for on-screen Back buttons.
 *
 * Without a dispatcher in the composition, [fallback] runs instead.
 */
@Composable
fun rememberSystemBack(fallback: () -> Unit): () -> Unit {
    val owner = LocalNavigationEventDispatcherOwner.current
    val currentFallback by rememberUpdatedState(fallback)
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(owner) {
        val dispatcher = owner?.navigationEventDispatcher
        dispatcher?.addInput(input)
        onDispose { dispatcher?.removeInput(input) }
    }
    return remember(owner) {
        if (owner == null) {
            { currentFallback() }
        } else {
            { input.backCompleted() }
        }
    }
}
