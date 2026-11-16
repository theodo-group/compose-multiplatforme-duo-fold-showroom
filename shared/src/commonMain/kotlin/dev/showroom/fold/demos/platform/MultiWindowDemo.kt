package dev.showroom.fold.demos.platform

import androidx.compose.runtime.Composable
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing

/** Platform source of the multi-window state and of the "new window" action. */
@Composable
internal expect fun rememberMultiWindowController(): CapabilityController

private val multiWindowCosts = listOf(
    NativeCost(
        platform = PlatformKind.Android,
        apis = listOf(
            "Activity.isInMultiWindowMode + OnMultiWindowModeChangedProvider",
            "WindowMetricsCalculator (bornes fenêtre / maximum)",
            "Intent FLAG_ACTIVITY_LAUNCH_ADJACENT | NEW_TASK | MULTIPLE_TASK",
            "ActivityManager.appTasks",
        ),
        effort = "≈ 140 lignes Kotlin androidMain + 1 propriété dans le manifeste " +
            "(PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, pour que le lanceur propose aussi « Nouvelle fenêtre »).",
        gotchas = listOf(
            "LAUNCH_ADJACENT n'est qu'un souhait : sur un téléphone non pliable la seconde instance s'ouvre en plein écran.",
            "Avec configChanges, l'activité n'est pas recréée en multi-fenêtre : il faut écouter le changement de mode.",
        ),
    ),
    NativeCost(
        platform = PlatformKind.Ios,
        apis = listOf(
            "UIApplication.supportsMultipleScenes / connectedScenes",
            "activateSceneSessionForRequest(UISceneSessionActivationRequest)",
            "UIWindowSceneActivationRequestOptions.placement = UIWindowSceneStandardPlacement",
            "UIWindowScene.coordinateSpace / screen (détection du côte à côte)",
            "Info.plist UIApplicationSceneManifest.UIApplicationSupportsMultipleScenes",
        ),
        effort = "≈ 260 lignes Kotlin iosMain, appel direct à UIKit (API iOS 17 présentes dans les bindings), " +
            "1 clé Info.plist, aucune ligne Swift : chaque scène SwiftUI héberge sa propre instance Compose.",
        gotchas = listOf(
            "Sur iPhone Duo, une nouvelle fenêtre ne peut naître que sur l'écran interne ; le système peut refuser, l'erreur est affichée ici.",
            "Pas d'équivalent à LAUNCH_ADJACENT : iOS 27 n'offre que les placements standard et prominent sur iPhone " +
                "(WindowPlacement SwiftUI est indisponible sur iOS). La fenêtre s'ouvre par-dessus ; l'utilisateur la glisse " +
                "par l'indicateur d'accueil vers un bord pour la mettre côte à côte.",
            "Sur iPhone classique supportsMultipleScenes reste false même avec la clé Info.plist.",
        ),
    ),
)

val multiWindowDemo = Demo(
    id = "multi-window",
    title = "Multi-fenêtre",
    summary = "Lire l'état multi-fenêtre réel et ouvrir une seconde instance de l'app : à côté de la première sur Android, " +
        "par-dessus sur iPhone Duo où seul l'utilisateur peut les mettre côte à côte.",
    sharing = Sharing.PlatformSpecific,
    notes = listOf(
        "Partagé : l'écran, le modèle de statut et l'interface de chaque fenêtre (chaque instance exécute le même App() Compose).",
        "Android : isInMultiWindowMode, WindowMetricsCalculator, ActivityManager.appTasks et une Intent FLAG_ACTIVITY_LAUNCH_ADJACENT | NEW_TASK | MULTIPLE_TASK. ≈ 140 lignes + une propriété de manifeste.",
        "iOS : UIApplication.supportsMultipleScenes, connectedScenes et activateSceneSessionForRequest (iOS 17), appelés directement depuis Kotlin. ≈ 260 lignes, plus UIApplicationSupportsMultipleScenes dans Info.plist.",
        "Différence Android / iOS : Android peut demander « à côté » (LAUNCH_ADJACENT) et le système l'accorde sur un pliable ouvert. " +
            "iOS 27 n'a aucune API pour cela (placements standard ou prominent seulement) : la fenêtre s'ouvre par-dessus, " +
            "et c'est l'utilisateur qui la glisse par l'indicateur d'accueil vers un bord de l'écran interne, puis touche l'icône de l'app " +
            "dans l'autre moitié pour y ramener la première. La démo détecte ensuite le côte à côte.",
        "Le système a le dernier mot : refus iOS (erreur UISceneErrorDomain) et ouverture plein écran sur Android sont affichés tels quels.",
        "L'état Compose n'est pas partagé entre fenêtres : chaque instance a son propre écran, comme deux lancements de l'app.",
    ),
    content = {
        val controller = rememberMultiWindowController()
        PlatformDemoPage(
            intro = "Chaque fenêtre est une instance complète de l'app. Ouvrez-en une seconde, puis revenez dans cette démo " +
                "dans l'une ou l'autre : les valeurs viennent de ${currentPlatform.label}.",
            primary = {
                controller.notice?.let { NoticeBanner(it) }
                SectionTitle("État multi-fenêtre")
                controller.capabilities.forEach { capability ->
                    CapabilityCard(
                        capability = capability,
                        onStart = { controller.start(capability.id) },
                        onStop = { controller.stop(capability.id) },
                    )
                }
                FactsCard("Valeurs lues en direct", controller.facts)
            },
            secondary = {
                EventLogCard(controller.events)
                NativeCostSection(multiWindowCosts)
            },
        )
    },
)
