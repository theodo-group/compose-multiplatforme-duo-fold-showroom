package dev.showroom.fold.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/** Narrowest iPhone Duo bar, used when the system reserves less than that on the bar edge. */
private val SystemBarMinWidth = 64.dp

/** Space between the toolbar group and the tab group of a vertical bar. */
private val TabGroupGap = 24.dp

/**
 * A screen with a title, a navigation control (Back), actions and optional tabs, placed where the
 * platform guideline wants them (see [LocalBarPlacement]):
 *
 * - [BarPlacement.Top]: a Material [TopAppBar], unchanged. [tabs] are not drawn: the screen draws
 *   its own tab / navigation bar in that case.
 * - [BarPlacement.Side]: a vertical bar on the given physical edge. [navigationIcon] first at the
 *   top, then [actions] in order of priority, then [tabs] as a separate group lower down. On iPhone
 *   Duo it fills the safe area the system reserves next to the status bar and starts below it. The
 *   [title] moves to a compact header at the top of the content area. Use icon buttons (see
 *   [BarItemButton]) with a content description: they fit both orientations.
 *
 * Like [Scaffold], [content] receives the padding that keeps it clear of the bars and of the
 * remaining safe area; apply it (and consume it) or pass it to a list as content padding. The safe
 * area already covered by the bars is consumed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveBarScaffold(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    tabs: List<BarItem> = emptyList(),
    content: @Composable (PaddingValues) -> Unit,
) {
    when (val placement = LocalBarPlacement.current) {
        BarPlacement.Top -> Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = title,
                    navigationIcon = { navigationIcon?.invoke() },
                    actions = { actions?.invoke() },
                )
            },
            content = content,
        )

        is BarPlacement.Side -> SideBarLayout(placement, title, navigationIcon, actions, tabs, modifier, content)
    }
}

/** An icon button for a [BarItem], usable in a top bar or a vertical bar. */
@Composable
fun BarItemButton(item: BarItem) {
    IconButton(onClick = item.onClick, enabled = item.enabled) {
        Icon(item.icon, contentDescription = item.label)
    }
}

@Composable
private fun SideBarLayout(
    placement: BarPlacement.Side,
    title: @Composable () -> Unit,
    navigationIcon: (@Composable () -> Unit)?,
    actions: (@Composable () -> Unit)?,
    tabs: List<BarItem>,
    modifier: Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val left = with(density) { safe.getLeft(this, direction).toDp() }
    val right = with(density) { safe.getRight(this, direction).toDp() }
    val top = with(density) { safe.getTop(this).toDp() }
    val bottom = with(density) { safe.getBottom(this).toDp() }

    val barOnLeft = placement.edge == BarEdge.Left
    val barSide = if (barOnLeft) WindowInsetsSides.Left else WindowInsetsSides.Right
    // Safe area the bar covers (consumed here); every other edge keeps its own safe area (rounded
    // corners, cutout, the other Split View window).
    val farLeft = if (barOnLeft) 0.dp else left
    val farRight = if (!barOnLeft) 0.dp else right
    // The system reserves the bar edge's safe area for its vertical bar, status bar on top: the
    // app's controls fill that strip, below the status bar. Drawn even when empty.
    val bar = @Composable {
        NavigationRail(
            modifier = Modifier.width(max(if (barOnLeft) left else right, SystemBarMinWidth)),
            containerColor = MaterialTheme.colorScheme.background,
            header = { navigationIcon?.invoke(); actions?.invoke() },
            windowInsets = WindowInsets(top = max(top, placement.itemsTop), bottom = bottom),
        ) { BarTabs(tabs) }
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            if (barOnLeft) bar()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(barSide + WindowInsetsSides.Top)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = top + 12.dp, bottom = 8.dp)
                        .padding(PaddingValues.Absolute(left = farLeft + 16.dp, right = farRight + 16.dp)),
                ) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content(PaddingValues.Absolute(left = farLeft, right = farRight, bottom = bottom))
                }
            }
            if (!barOnLeft) bar()
        }
    }
}

/** The tab group, lower in the bar and apart from the toolbar group. */
@Composable
private fun BarTabs(tabs: List<BarItem>) {
    if (tabs.isEmpty()) return
    Spacer(Modifier.height(TabGroupGap))
    tabs.forEach { tab ->
        NavigationRailItem(
            selected = tab.selected,
            onClick = tab.onClick,
            enabled = tab.enabled,
            icon = { Icon(tab.icon, contentDescription = null) },
            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}
