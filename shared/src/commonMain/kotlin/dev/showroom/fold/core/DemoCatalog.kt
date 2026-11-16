package dev.showroom.fold.core

import dev.showroom.fold.demos.hinge.bookModeDemo
import dev.showroom.fold.demos.hinge.hingeGaugeDemo
import dev.showroom.fold.demos.hinge.tabletopDemo
import dev.showroom.fold.demos.layout.adaptiveGridDemo
import dev.showroom.fold.demos.layout.continuityDemo
import dev.showroom.fold.demos.layout.navigationSuiteDemo
import dev.showroom.fold.demos.layout.windowInspectorDemo
import dev.showroom.fold.demos.panes.listDetailDemo
import dev.showroom.fold.demos.panes.nav3SceneDemo
import dev.showroom.fold.demos.panes.supportingPaneDemo
import dev.showroom.fold.demos.platform.displayModesDemo
import dev.showroom.fold.demos.platform.dragDropDemo
import dev.showroom.fold.demos.platform.multiWindowDemo

/** Every demo of the showroom. The catalog screen groups them by [Sharing]. */
val demoCatalog: List<Demo> = listOf(
    // 100 % shared
    windowInspectorDemo,
    navigationSuiteDemo,
    adaptiveGridDemo,
    listDetailDemo,
    supportingPaneDemo,
    nav3SceneDemo,
    continuityDemo,
    // Shared UI + expect/actual
    hingeGaugeDemo,
    bookModeDemo,
    tabletopDemo,
    dragDropDemo,
    // Platform-specific
    displayModesDemo,
    multiWindowDemo,
)
