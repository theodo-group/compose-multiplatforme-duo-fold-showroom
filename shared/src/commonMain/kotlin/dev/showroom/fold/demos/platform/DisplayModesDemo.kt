package dev.showroom.fold.demos.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing

/**
 * Text shown by [OuterDisplayContent] on the outer display. Plain snapshot state: the inner screen
 * and the outer-display composition live in the same process, so changing it here recomposes there.
 */
object OuterDisplayMessage {
    val choices = listOf("Souriez !", "Regardez l'objectif", "3… 2… 1…", "Merci !")
    var text by mutableStateOf(choices.first())
}

/** Platform source of the outer/rear display capabilities. */
@Composable
internal expect fun rememberDisplayModesController(): CapabilityController

private val displayModesCosts = listOf(
    NativeCost(
        platform = PlatformKind.Android,
        apis = listOf(
            "androidx.window.area.WindowAreaController (@ExperimentalWindowApi)",
            "transferActivityToWindowArea / presentContentOnWindowArea",
            "WindowAreaSessionPresenter.setContentView(ComposeView)",
        ),
        effort = "≈ 250 lignes Kotlin dans androidMain, aucune modification du manifeste. " +
            "Le contenu de l'écran externe est le même composable partagé, posé dans une ComposeView.",
        gotchas = listOf(
            "Liste vide sur émulateur et sur téléphone non pliable : l'extension WindowManager du constructeur ne déclare aucune zone.",
            "La ComposeView de l'écran externe n'a pas de propriétaire de cycle de vie : il faut lui donner celui de l'activité.",
        ),
    ),
    NativeCost(
        platform = PlatformKind.Ios,
        apis = listOf(
            "SwiftUI .sceneAccessory { CameraCaptureAccessory { … } } (iOS 27.1)",
            "onAvailabilityChange, AVCaptureSession + NSCameraUsageDescription",
            "ComposeUIViewController dans l'accessoire",
        ),
        effort = "≈ 140 lignes Swift (iosApp/OuterDisplayAccessory.swift) + ≈ 230 lignes Kotlin iosMain (pont, statuts, scènes). " +
            "Les symboles iOS 27 n'existent pas dans les bindings Kotlin/Native (SDK 26.4) : la partie accessoire est en Swift.",
        gotchas = listOf(
            "iOS n'ouvre l'écran externe que pendant une capture caméra active : pas d'équivalent du « mode écran arrière » d'Android.",
            "Le système décide seul s'il affiche l'accessoire ; l'app ne peut que l'activer et observer sa disponibilité.",
        ),
    ),
)

val displayModesDemo = Demo(
    id = "display-modes",
    title = "Écran externe et double écran",
    summary = "Afficher du contenu sur l'écran extérieur d'un pliable : WindowAreaController sur Android, accessoire de scène caméra sur iPhone Duo.",
    sharing = Sharing.PlatformSpecific,
    notes = listOf(
        "Partagé : tout l'écran de la démo, le modèle d'état (Capability) et le contenu affiché sur l'écran externe (un composable commun).",
        "Android : androidx.window.area.WindowAreaController (expérimental) liste les zones d'affichage et leur statut en direct ; « mode écran arrière » déplace l'activité, « double écran » présente une vue sur l'écran externe pendant que l'écran interne reste actif. ≈ 250 lignes androidMain.",
        "iOS : aucune API pour déplacer l'app sur l'écran externe. Le seul accès est CameraCaptureAccessory (iOS 27.1), présenté par le système uniquement pendant une capture caméra active. ≈ 140 lignes Swift + ≈ 230 lignes Kotlin, plus NSCameraUsageDescription.",
        "Les symboles UISceneAccessory (iOS 27) sont absents des bindings platform.UIKit de Kotlin/Native 2.4.21 (SDK iOS 26.4) : passage obligé par Swift, branché via une interface Kotlin enregistrée au démarrage.",
        "Sur émulateur Android, simulateur iOS ou téléphone non pliable, les statuts réels (non supporté / indisponible) sont affichés tels quels : rien n'est simulé.",
    ),
    content = { DisplayModesContent() },
)

/**
 * Says up front what this demo is and is not about: the outer display "just works" when the device is
 * closed; only using it while the device is open needs native code — and neither OS simulator can show it.
 */
@Composable
private fun ScopeCard() {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("De quoi parle cette démo")
            Text(
                "Appareil fermé : l'app s'affiche d'elle-même sur l'écran extérieur, sans une ligne de code. " +
                    "Ce n'est pas le sujet ici.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Appareil ouvert : afficher en plus quelque chose sur l'écran extérieur (un aperçu pour la " +
                    "personne photographiée, par exemple). C'est cette capacité, entièrement native, que l'écran teste.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                when (currentPlatform) {
                    PlatformKind.Ios ->
                        "Sur iOS, le système ne l'autorise que pendant une capture caméra. Un simulateur n'a pas de " +
                            "caméra : le résultat n'est visible que sur un vrai iPhone Duo."

                    PlatformKind.Android ->
                        "Sur un émulateur, Android accepte la demande mais l'écran extérieur reste éteint : le " +
                            "résultat n'est visible que sur un vrai pliable."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayModesContent() {
    val controller = rememberDisplayModesController()
    val anyActive = controller.capabilities.any { it.level == CapabilityLevel.Active }

    PlatformDemoPage(
        intro = "Statuts lus en direct depuis ${currentPlatform.label}. Les boutons n'apparaissent que si la plateforme " +
            "déclare la capacité disponible.",
        primary = {
            ScopeCard()
            controller.notice?.let { NoticeBanner(it) }
            SectionTitle("Capacités de l'écran externe")
            controller.capabilities.forEach { capability ->
                CapabilityCard(
                    capability = capability,
                    onStart = { controller.start(capability.id) },
                    onStop = { controller.stop(capability.id) },
                )
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Contenu de l'écran externe")
                    Text(
                        if (anyActive) {
                            "Une session est active : changez le texte, l'écran externe se met à jour."
                        } else {
                            "Aperçu du composable partagé qui sera affiché sur l'écran externe."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OuterDisplayMessage.choices.forEach { choice ->
                            FilterChip(
                                selected = OuterDisplayMessage.text == choice,
                                onClick = { OuterDisplayMessage.text = choice },
                                label = { Text(choice) },
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1.6f),
                    ) {
                        OuterDisplayContent(OuterDisplayMessage.text)
                    }
                }
            }
        },
        secondary = {
            FactsCard("Ce que dit la plateforme", controller.facts)
            EventLogCard(controller.events)
            NativeCostSection(displayModesCosts)
        },
    )
}
