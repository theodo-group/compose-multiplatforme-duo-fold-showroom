package dev.showroom.fold.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/** How much of a demo lives in shared code. This is the whole point of the showroom. */
enum class Sharing(val label: String, val description: String) {
    Common(
        label = "100 % partagé",
        description = "Aucune ligne spécifique : tout est dans commonMain.",
    ),
    ExpectActual(
        label = "Partagé + expect/actual",
        description = "UI partagée ; seule une petite source de données change par plateforme.",
    ),
    PlatformSpecific(
        label = "Spécifique plateforme",
        description = "API propre à un OS : du code natif est obligatoire.",
    ),
}

/**
 * One entry of the showroom.
 *
 * @param notes short bullet points shown in the info sheet: what is shared, what is specific and why.
 * @param content the demo itself. It is placed under the showroom top bar and fills the remaining space;
 *   window insets are already consumed by the host.
 */
@Immutable
class Demo(
    val id: String,
    val title: String,
    val summary: String,
    val sharing: Sharing,
    val notes: List<String>,
    val content: @Composable () -> Unit,
)
