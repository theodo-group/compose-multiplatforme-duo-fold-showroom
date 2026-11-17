package dev.showroom.fold.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One control a screen hands to the host bar. Always give a [label]: it is the accessibility label
 * of the icon, and the text under it for tabs.
 */
@Immutable
data class BarItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * What the current screen contributes to the host bar, on top of the host's own Back and info
 * buttons. Filled by [HostBarItems], read by the host (see DemoScreen).
 */
@Stable
class BarItemsRegistry {
    /** Toolbar actions, shown after the host's own actions (top bar, or vertical bar). */
    var actions: List<BarItem> by mutableStateOf(emptyList())
        private set

    /**
     * Tab-bar destinations. Only drawn by the host in [BarPlacement.Side], as a separate group lower
     * in the vertical bar; in [BarPlacement.Top] the screen draws its own tab / navigation bar.
     */
    var tabs: List<BarItem> by mutableStateOf(emptyList())
        private set

    private var owner: Any? = null

    internal fun set(by: Any, actions: List<BarItem>, tabs: List<BarItem>) {
        owner = by
        this.actions = actions
        this.tabs = tabs
    }

    internal fun clear(by: Any) {
        if (owner !== by) return
        owner = null
        actions = emptyList()
        tabs = emptyList()
    }
}

/** The host bar of the current screen, or null outside a host that accepts items. */
val LocalBarItems = staticCompositionLocalOf<BarItemsRegistry?> { null }

/**
 * Puts [actions] and [tabs] in the host bar for as long as this call stays in the composition.
 * The latest call wins; items go away when it leaves. Read [LocalBarPlacement] to know whether the
 * host shows the [tabs] (vertical bar) or the screen must draw its own tab bar (top bar).
 *
 * ```
 * HostBarItems(
 *     actions = listOf(BarItem("add", Icons.Filled.Add, "Ajouter") { add() }),
 *     tabs = destinations.map { BarItem(it.key, it.icon, it.label, it == current) { current = it } },
 * )
 * ```
 */
@Composable
fun HostBarItems(actions: List<BarItem> = emptyList(), tabs: List<BarItem> = emptyList()) {
    val registry = LocalBarItems.current ?: return
    val token = remember { Any() }
    SideEffect { registry.set(token, actions, tabs) }
    DisposableEffect(registry) { onDispose { registry.clear(token) } }
}
