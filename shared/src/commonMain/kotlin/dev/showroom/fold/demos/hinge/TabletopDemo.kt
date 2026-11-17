package dev.showroom.fold.demos.hinge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.showroom.fold.core.Demo
import dev.showroom.fold.core.Sharing
import dev.showroom.fold.fold.LocalFoldState
import dev.showroom.fold.fold.Posture
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

val tabletopDemo = Demo(
    id = "tabletop",
    title = "Mode chevalet",
    summary = "Un lecteur vidéo factice qui place l'image au-dessus du pli et les commandes en dessous quand l'appareil est posé en chevalet.",
    sharing = Sharing.ExpectActual,
    notes = listOf(
        "Partagé : le lecteur, la « vidéo » dessinée sur Canvas et la mise en page via FoldAwareTwoPane, réutilisé tel quel depuis le mode livre.",
        "Spécifique : seule la source du FoldState. La posture chevalet suppose une charnière horizontale : Android l'obtient de FoldingFeature (HALF_OPENED + HORIZONTAL), iOS 27.1 de l'état de UIHingeInteraction et de la forme de la région « division ».",
        "Posture livre : vidéo d'un côté, playlist de l'autre. À plat ou fermé : mise en page classique avec commandes superposées.",
        "Aucun média réel : un vrai lecteur demanderait ExoPlayer/Media3 sur Android et AVPlayer sur iOS, donc du code spécifique supplémentaire.",
        "La charnière est convertie dans le repère du lecteur : la barre d'état et le bandeau d'aide de la démo ne décalent pas la séparation.",
        "Le pli est toujours celui de l'appareil (ou de l'émulateur / du simulateur iOS) : sans posture chevalet réelle, le lecteur garde sa mise en page classique.",
    ),
    content = { TabletopPlayerScreen() },
)

@Immutable
private data class Track(
    val title: String,
    val subtitle: String,
    val durationSec: Float,
    val skyTop: Color,
    val skyBottom: Color,
    val sun: Color,
    val land: Color,
)

private val TRACKS = listOf(
    Track("Lever sur la baie", "Plan fixe · 0:42", 42f, Color(0xFF233A6B), Color(0xFFF2A65A), Color(0xFFFFE08A), Color(0xFF1C4F5E)),
    Track("Marée haute", "Travelling · 0:55", 55f, Color(0xFF1B5E8C), Color(0xFF9ED8F0), Color(0xFFFFFFFF), Color(0xFF0D3B57)),
    Track("Dunes à midi", "Drone · 0:38", 38f, Color(0xFF4A90C2), Color(0xFFF7E3B5), Color(0xFFFFF4C2), Color(0xFFC98E4F)),
    Track("Crépuscule", "Plan fixe · 1:04", 64f, Color(0xFF2E1A47), Color(0xFFE8697B), Color(0xFFFFB36B), Color(0xFF3B2340)),
    Track("Nuit claire", "Accéléré · 0:47", 47f, Color(0xFF070B1F), Color(0xFF243A6B), Color(0xFFE9ECF5), Color(0xFF0F1A33)),
)

@Stable
private class PlayerState {
    var index by mutableIntStateOf(0)
    var position by mutableFloatStateOf(0f)
    var playing by mutableStateOf(true)
    val track: Track get() = TRACKS[index]

    fun select(i: Int) {
        index = (i + TRACKS.size) % TRACKS.size
        position = 0f
    }

    fun next() = select(index + 1)

    /** Restarts the track after a few seconds, like most players; goes to the previous one otherwise. */
    fun previous() {
        if (position > 3f) position = 0f else select(index - 1)
    }

    fun advance(seconds: Float) {
        position += seconds
        if (position >= track.durationSec) next()
    }
}

@Composable
private fun TabletopPlayerScreen() {
    val fold = LocalFoldState.current
    val player = remember { PlayerState() }
    val paneState = rememberFoldAwareTwoPaneState()

    // Playback clock: advances the fake video while playing.
    LaunchedEffect(player.playing) {
        if (!player.playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                player.advance((now - last) / 1_000_000_000f)
                last = now
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val arrangement = paneState.arrangement
        LayoutStatusBar(
            title = fold.postureSummary(),
            detail = arrangement?.let { playerLayoutText(it, fold.posture) } ?: "Mesure de la mise en page…",
        )
        if (!fold.hasHinge) {
            FoldHelpCard(
                title = "Aucune charnière détectée",
                message = "Cet appareil ne signale pas de pli : le lecteur garde sa mise en page classique, commandes superposées. La séparation vidéo / commandes demande une vraie posture chevalet : charnière horizontale, appareil entrouvert (~90°).",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else if (arrangement != null && arrangement.mode != TwoPaneMode.FoldTopBottom) {
            Text(
                text = "Posture actuelle : ${fold.posture.label.lowercase()}. Posez l'appareil en chevalet (charnière horizontale, ~90°) pour séparer vidéo et commandes (iPhone Duo : tournez-le ; sur émulateur ou simulateur : commandes de pliage et de rotation).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        FoldAwareTwoPane(
            state = paneState,
            modifier = Modifier.weight(1f),
            singlePane = { SinglePanePlayer(player) },
            first = { a ->
                if (a.mode == TwoPaneMode.FoldTopBottom) {
                    // Tabletop: the upper half is the screen; nothing to touch there.
                    Letterbox { MockVideo(player, Modifier.aspectRatio(16f / 9f)) }
                } else {
                    VideoWithOverlay(player, Modifier.fillMaxSize())
                }
            },
            second = { a ->
                if (a.mode == TwoPaneMode.FoldTopBottom) {
                    TabletopControls(player)
                } else {
                    Playlist(player, header = "À suivre", modifier = Modifier.fillMaxSize())
                }
            },
        )
    }
}

private fun playerLayoutText(a: TwoPaneArrangement, posture: Posture): String = when (a.mode) {
    TwoPaneMode.FoldTopBottom ->
        "Charnière horizontale séparante → vidéo au-dessus du pli, commandes et playlist en dessous" + gapText(a)
    TwoPaneMode.FoldStartEnd ->
        "Charnière verticale séparante → vidéo d'un côté du pli, playlist de l'autre" + gapText(a)
    TwoPaneMode.Balanced ->
        "${a.reason.label} · ${a.widthDp} dp ≥ 600 dp → vidéo (commandes superposées) + playlist"
    TwoPaneMode.Single -> {
        val why = if (posture == Posture.Closed) "Écran fermé" else a.reason.label
        "$why · ${a.widthDp} dp → vidéo avec commandes superposées"
    }
}

@Composable
private fun SinglePanePlayer(player: PlayerState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight > maxWidth * 0.9f) {
            // Portrait: 16:9 video on top, the rest of the height for the playlist.
            Column(Modifier.fillMaxSize()) {
                VideoWithOverlay(player, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Playlist(player, header = "À suivre", modifier = Modifier.weight(1f))
            }
        } else {
            VideoWithOverlay(player, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Letterbox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { content() }
}

/** Video with controls drawn over it; a tap shows or hides them, they fade out while playing. */
@Composable
private fun VideoWithOverlay(player: PlayerState, modifier: Modifier) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var lastInteraction by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, player.playing, lastInteraction) {
        if (controlsVisible && player.playing) {
            delay(3500)
            controlsVisible = false
        }
    }
    Box(
        modifier
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controlsVisible = !controlsVisible
            },
        contentAlignment = Alignment.Center,
    ) {
        MockVideo(player, Modifier.aspectRatio(16f / 9f))
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 8.dp),
            ) {
                Text(
                    text = player.track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Scrubber(player, onInteract = { lastInteraction++ }, onDark = true)
                TransportButtons(player, onInteract = { lastInteraction++ }, onDark = true, large = false)
            }
        }
    }
}

/** Tabletop lower half: everything the hand needs, the video stays untouched above the fold. */
@Composable
private fun TabletopControls(player: PlayerState) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(player.track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = player.track.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TransportButtons(player, onInteract = {}, onDark = false, large = true)
            }
            Scrubber(player, onInteract = {}, onDark = false)
            Playlist(player, header = null, modifier = Modifier.weight(1f), compact = true)
        }
    }
}

@Composable
private fun Scrubber(player: PlayerState, onInteract: () -> Unit, onDark: Boolean) {
    val textColor = if (onDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(player.position), color = textColor, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
        Slider(
            value = player.position.coerceIn(0f, player.track.durationSec),
            onValueChange = {
                player.position = it
                onInteract()
            },
            valueRange = 0f..player.track.durationSec,
            colors = if (onDark) {
                SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f))
            } else {
                SliderDefaults.colors()
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(formatTime(player.track.durationSec), color = textColor, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TransportButtons(player: PlayerState, onInteract: () -> Unit, onDark: Boolean, large: Boolean) {
    val tint = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { player.previous(); onInteract() }) {
            Icon(SkipPreviousIcon, contentDescription = "Précédent", tint = tint)
        }
        FilledIconButton(
            onClick = { player.playing = !player.playing; onInteract() },
            modifier = if (large) Modifier.size(56.dp) else Modifier,
            colors = if (onDark) {
                IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
            } else {
                IconButtonDefaults.filledIconButtonColors()
            },
        ) {
            Icon(
                imageVector = if (player.playing) PauseIcon else Icons.Filled.PlayArrow,
                contentDescription = if (player.playing) "Pause" else "Lecture",
                modifier = if (large) Modifier.size(32.dp) else Modifier,
            )
        }
        IconButton(onClick = { player.next(); onInteract() }) {
            Icon(SkipNextIcon, contentDescription = "Suivant", tint = tint)
        }
    }
}

@Composable
private fun Playlist(player: PlayerState, header: String?, modifier: Modifier, compact: Boolean = false) {
    LazyColumn(modifier) {
        if (header != null) {
            item {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
        }
        itemsIndexed(TRACKS) { i, track ->
            val current = i == player.index
            ListItem(
                headlineContent = { Text(track.title, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal) },
                supportingContent = if (compact) null else ({ Text(track.subtitle) }),
                leadingContent = { Thumbnail(track, current) },
                trailingContent = { Text(formatTime(track.durationSec), style = MaterialTheme.typography.labelMedium) },
                colors = ListItemDefaults.colors(
                    containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ),
                modifier = Modifier.clickable {
                    player.select(i)
                    player.playing = true
                },
            )
        }
    }
}

@Composable
private fun Thumbnail(track: Track, current: Boolean) {
    Box(
        Modifier
            .width(56.dp)
            .height(32.dp)
            .background(Brush.verticalGradient(listOf(track.skyTop, track.skyBottom)), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (current) Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/**
 * The fake "video": a landscape whose sun follows the playback position and whose waves move with
 * time. Driven by the position only, so pausing freezes the frame and scrubbing seeks.
 */
@Composable
private fun MockVideo(player: PlayerState, modifier: Modifier) {
    val track = player.track
    val t = player.position
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(track.skyTop, track.skyBottom)))

            val progress = (t / track.durationSec).coerceIn(0f, 1f)
            val sunX = w * (0.12f + 0.76f * progress)
            val sunY = h * (0.62f - 0.34f * sin(progress * PI.toFloat()))
            drawCircle(track.sun.copy(alpha = 0.25f), radius = h * 0.16f, center = Offset(sunX, sunY))
            drawCircle(track.sun, radius = h * 0.09f, center = Offset(sunX, sunY))

            // Three parallax layers, back to front.
            for (layer in 0 until 3) {
                val base = h * (0.62f + layer * 0.12f)
                val amplitude = h * (0.035f - layer * 0.006f)
                val frequency = (2.2f + layer * 1.3f) * 2f * PI.toFloat() / w
                val speed = 0.6f + layer * 0.9f
                val path = Path().apply {
                    moveTo(0f, h)
                    var x = 0f
                    val step = w / 48f
                    while (x <= w + step) {
                        lineTo(x, base + amplitude * sin(x * frequency + t * speed))
                        x += step
                    }
                    lineTo(w, h)
                    close()
                }
                val shade = 0.55f + layer * 0.22f
                drawPath(path, track.land.copy(alpha = shade))
            }
        }
        Text(
            text = "${formatTime(t)} · ${track.title}",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}

private fun formatTime(seconds: Float): String {
    val s = seconds.roundToInt().coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

// Media icons missing from material-icons-core, drawn from the Material paths.
private val PauseIcon: ImageVector = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 19f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(6f); close()
        moveTo(14f, 5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(5f); close()
    }
}.build()

private val SkipNextIcon: ImageVector = ImageVector.Builder("SkipNext", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 18f); lineTo(14.5f, 12f); lineTo(6f, 6f); close()
        moveTo(16f, 6f); verticalLineTo(18f); horizontalLineTo(18f); verticalLineTo(6f); close()
    }
}.build()

private val SkipPreviousIcon: ImageVector = ImageVector.Builder("SkipPrevious", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 6f); horizontalLineTo(8f); verticalLineTo(18f); horizontalLineTo(6f); close()
        moveTo(9.5f, 12f); lineTo(18f, 18f); verticalLineTo(6f); close()
    }
}.build()
