# Fold Showroom

Showroom Kotlin Multiplatform + Compose Multiplatform pour téléphones pliables : **iPhone Duo** et **Pixel 9 Pro Fold**.
Il répond à deux questions, écran par écran :

1. À quel point est-il facile de faire les choses **sans code spécifique** ?
2. Dans quels cas **doit-on** écrire du code spécifique, et combien ?

Chaque démo porte un badge (« 100 % partagé », « Partagé + expect/actual », « Spécifique plateforme ») et un bouton ⓘ qui détaille ce qui est partagé, ce qui est natif et les pièges rencontrés.

## Lancer

| Cible | Commande |
|---|---|
| Android | `./gradlew :androidApp:installDebug` (émulateur ou appareil démarré) |
| iOS | ouvrir `iosApp/iosApp.xcodeproj` dans Xcode ≥ 27.1, choisir le simulateur **iPhone Duo**, Run |

- Le simulateur iPhone Duo demande le runtime dédié « iOS 27.2 – iPhone Duo » (Xcode › Settings › Components) : le runtime iOS générique ne le propose pas.
- Le projet Xcode est généré depuis `iosApp/project.yml` : `cd iosApp && xcodegen generate`.
- Côté Android, créer un émulateur avec le profil **Pixel 9 Pro Fold** (Device Manager d'Android Studio). `adb emu fold`, `adb emu unfold`, `adb emu sensor set hinge-angle0 <degrés>` et les contrôles de posture de l'émulateur pilotent la charnière.
- La posture et l'orientation viennent toujours de l'appareil ou du simulateur du système (plier / pivoter dans le simulateur iOS, contrôles de pliure de l'émulateur Android). Seul l'écran **Jauge de charnière** propose deux réglages simulés, limités à cet écran : charnière occultante et angle, borné à la plage de l'état réel.

Versions : Kotlin 2.4.21, Compose Multiplatform 1.12.2, material3 1.9.0, material3-adaptive 1.3.0, Navigation 3 1.1.2, AGP 9.5.1, Gradle 9.8.0, androidx.window 1.5.1.

## Les 13 démos

| Démo | Niveau | Ce qu'elle montre | Code natif |
|---|---|---|---|
| Inspecteur de fenêtre | 100 % partagé | taille, classes de taille, insets, posture, en direct | aucun |
| Navigation adaptative | 100 % partagé | `NavigationSuiteScaffold` : barre, rail ou tiroir selon la fenêtre | aucun |
| Grille adaptative | 100 % partagé | colonnes et cartes qui suivent l'espace disponible | aucun |
| Liste / détail | 100 % partagé | `ListDetailPaneScaffold` : 1 ou 2 panneaux | aucun |
| Panneau secondaire | 100 % partagé | `SupportingPaneScaffold` | aucun |
| Navigation 3 : scènes | 100 % partagé | une pile, une `Scene` à 1 ou 2 panneaux | aucun |
| Continuité d'état | 100 % partagé | ce qui survit au pli, au dépli, à la rotation, et à une recréation d'activité | aucun pour la continuité ; un bouton de démonstration appelle `Activity.recreate()` |
| Jauge de charnière | expect/actual | posture, angle, géométrie du pli, plus deux réglages simulés | la source du `FoldState` |
| Mode livre | expect/actual | deux pages de part et d'autre du pli (`FoldAwareTwoPane`) | la source du `FoldState` |
| Mode chevalet | expect/actual | vidéo au-dessus du pli, commandes en dessous | la source du `FoldState` |
| Glisser-déposer | expect/actual | glisser entre volets et entre apps | la charge utile du glisser (≈ 30 lignes Android, ≈ 65 iOS) |
| Écran externe et double écran | spécifique | afficher du contenu sur l'écran extérieur | Android ≈ 230 lignes ; iOS ≈ 210 lignes Kotlin + ≈ 140 Swift |
| Multi-fenêtre | spécifique | ouvrir une seconde instance : côte à côte sur Android, par-dessus sur iOS (l'utilisateur les arrange) | Android ≈ 125 lignes ; iOS ≈ 250 lignes |

## Bilan

### 1. Ce qui est facile sans rien de spécifique

Tout ce qui dépend de la **taille de la fenêtre** se fait en `commonMain`, à l'identique sur les deux plateformes :

- classes de taille (`currentWindowAdaptiveInfoV2()`), `LocalWindowInfo`, insets ;
- navigation adaptative, grilles, liste/détail, panneau secondaire, Navigation 3 ;
- retour arrière (retour système et prédictif Android, balayage de bord iOS) ;
- continuité au pli/dépli : avec `configChanges` déclaré dans le manifeste Android, l'activité n'est pas recréée et l'état Compose survit (même un simple `remember`), comme sur iOS. Sans cet attribut, Android recrée l'activité à chaque rotation ou pliage et seul `rememberSaveable` survit : l'écran « Continuité d'état » le montre avec son bouton « Recréer l'activité ».

Sur un pliable, déplier revient à agrandir la fenêtre : une app pensée « par fenêtre » est donc adaptée aux deux écrans sans une ligne dédiée. C'est 7 démos sur 13.

### 2. Quand il faut du spécifique

| Besoin | Android | iOS | Coût |
|---|---|---|---|
| Posture, angle, position du pli | Jetpack WindowManager (`FoldingFeature`) + capteur d'angle | `UIHingeInteraction` + région réservée « division » (iOS 27.1) | une seule source `expect/actual`, écrite une fois : ≈ 100 lignes Kotlin Android, ≈ 75 lignes Kotlin + ≈ 200 lignes Objective-C iOS. Toute l'UI au-dessus reste partagée. |
| Glisser-déposer entre apps | `ClipData` | `UIDragItem` / `NSItemProvider` | petit : les modificateurs Compose sont communs, seule la charge utile diffère. |
| Écran extérieur | `WindowAreaController` (mode écran arrière, double écran) | `CameraCaptureAccessory`, uniquement pendant une capture caméra ; aucune API pour y déplacer l'app | élevé, et les deux plateformes n'offrent pas la même chose. Côté iOS, passage obligé par Swift. |
| Multi-fenêtre | `Intent` adjacente, tâches multiples : le système place les deux instances côte à côte | scènes multiples (`UIApplicationSupportsMultipleScenes`) : l'app demande une fenêtre mais ne choisit pas sa position | moyen ; le système peut refuser. |
| Média réel, caméra | Media3 / CameraX | AVFoundation | non couvert ici (lecteur factice). |

Trois enseignements :

- **La bibliothèque material3-adaptive ne connaît pas le pli sur iOS.** Sa posture vient de WindowManager sur Android et reste « à plat » sur iOS. Pour réagir à la charnière sur les deux plateformes, il faut sa propre source (ce projet) ou une lib dédiée.
- **iOS ne laisse pas l'app placer ses fenêtres.** Aucune API publique ne permet d'ouvrir une fenêtre « à côté » sur iPhone Duo (seuls les placements « standard » et « prominent » existent ; un ingénieur Apple le confirme sur [le forum développeur](https://developer.apple.com/forums/thread/847859)). La nouvelle fenêtre s'ouvre par-dessus, et c'est l'utilisateur qui la glisse sur une moitié de l'écran. Android, lui, accepte `FLAG_ACTIVITY_LAUNCH_ADJACENT`.
- **Les API iOS 27.1 ne sont pas dans les bindings Kotlin/Native 2.4.21** (générés depuis le SDK iOS 26.4). Deux contournements sont utilisés ici : un pont Objective-C via cinterop qui atteint l'API par le runtime (charnière), ou du Swift dans `iosApp` branché sur une interface Kotlin (écran extérieur).

### En chiffres

| | Lignes (hors lignes vides) | Part |
|---|---|---|
| Code partagé (`commonMain`) | 4 347 | 74 % |
| Spécifique Android (`androidMain` + `androidApp`) | 532 | 9 % |
| Spécifique iOS (`iosMain` + Objective-C + Swift) | 958 | 16 % |

Le spécifique est concentré : la source de pliure (≈ 100 lignes Android, ≈ 280 iOS) sert trois démos, et les deux démos « spécifiques » pèsent à elles seules ≈ 370 lignes Android et ≈ 580 lignes iOS. (Pourcentages arrondis.)

## Structure

```
shared/src/commonMain/…/fold/
  App.kt                 point d'entrée unique (Android et iOS)
  core/                  catalogue, hôte des démos, thème, modèle Demo
  fold/                  FoldState, expect rememberPlatformFoldState()
  demos/layout|panes     démos 100 % partagées
  demos/hinge            démos sensibles au pli + FoldAwareTwoPane
  demos/platform         UI partagée des démos spécifiques
shared/src/androidMain   actual : WindowManager, WindowAreaController, multi-fenêtre, ClipData
shared/src/iosMain       actual : charnière, scènes, glisser-déposer
shared/src/nativeInterop pont Objective-C vers UIHingeInteraction (inerte avant iOS 27.1)
androidApp/              activité hôte
iosApp/                  app SwiftUI hôte + accessoire d'écran extérieur
```

## État de la vérification

Testé sur le simulateur **iPhone Duo** (iOS 27.2) et sur l'émulateur **Pixel 9 Pro Fold** (API 37).

| Démo | iPhone Duo | Pixel 9 Pro Fold |
|---|---|---|
| Catalogue, liste / détail | OK, 2 panneaux déplié | catalogue OK |
| Jauge de charnière | OK : « à plat » 180°, « chevalet », position du pli | OK : 100° « chevalet », pli séparant ; les deux réglages simulés fonctionnent et se bornent à l'état réel |
| Mode livre | OK à plat et en posture livre (2 pages autour du pli) | OK en posture livre (2 pages autour du pli) |
| Mode chevalet | non ouvert | OK : vidéo au-dessus du pli, commandes en dessous |
| Glisser-déposer | OK dans l'app | non testé |
| Multi-fenêtre | OK : seconde fenêtre ouverte par-dessus, 2 scènes connectées ; côte à côte après le geste de l'utilisateur, détecté par la démo | OK : 2 instances en écran partagé |
| Continuité d'état | non ouvert | OK : après « Recréer l'activité », `remember` repart de 0, `rememberSaveable`, la liste et le journal restent |
| Écran externe | bloqué par le simulateur : pas de caméra, donc iOS ne présente jamais l'accessoire | demande acceptée (l'appareil passe en double affichage), mais l'émulateur ne livre pas la session : rien de visible sur l'écran extérieur |
| Retour système | non testé | OK (retour au catalogue) |

- **Écran externe** : à valider sur de vrais appareils, des deux côtés. C'est la seule démo dont le résultat final n'a pas pu être vu.
- Compilées mais pas ouvertes une à une : inspecteur, navigation adaptative, grille, panneau secondaire, Navigation 3.
- Non vérifié : glisser-déposer entre deux apps, balayage de bord iOS pour le retour, mode « écran arrière » Android.
- Deux défauts trouvés et corrigés pendant ces tests : plantage Android à l'arrêt d'une présentation sans session (exception dans `WindowAreaInfo.getActiveSession`), et zone de dépôt restée en état « survol » après un dépôt sur iOS.

## Références

- [Crease](https://github.com/jantursky/crease) (MIT) : bibliothèque KMP qui expose la charnière en code commun. Le pont Objective-C de ce projet reprend son approche ; elle est une alternative prête à l'emploi à `fold/`.
- [android/compose-samples](https://github.com/android/compose-samples) (Jetcaster, JetNews) et [android/platform-samples](https://github.com/android/platform-samples) (WindowManager) pour les équivalents Android seuls.
- Apple : « Preparing your app for iPhone Duo » et Tech Talks 111461, 111463, 111464.
