package dev.showroom.fold

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.showroom.fold.core.CatalogScreen
import dev.showroom.fold.core.DemoScreen
import dev.showroom.fold.core.ShowroomTheme
import dev.showroom.fold.core.demoCatalog
import dev.showroom.fold.fold.ProvideFoldState

/** The whole app. Called as-is from the Android activity and from the iOS view controller. */
@Composable
fun App() {
    ShowroomTheme {
        ProvideFoldState {
            var currentId by rememberSaveable { mutableStateOf<String?>(null) }
            // Hoisted so the catalog keeps its scroll position while a demo is open.
            val catalogGridState = rememberLazyGridState()
            val demo = currentId?.let { id -> demoCatalog.firstOrNull { it.id == id } }
            if (demo == null) {
                CatalogScreen(demos = demoCatalog, gridState = catalogGridState, onOpen = { currentId = it.id })
            } else {
                DemoScreen(demo = demo, onBack = { currentId = null })
            }
        }
    }
}
