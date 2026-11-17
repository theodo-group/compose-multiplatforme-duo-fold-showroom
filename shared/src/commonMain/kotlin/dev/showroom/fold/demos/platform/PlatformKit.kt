package dev.showroom.fold.demos.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The platform this binary runs on. Each demo uses it to highlight "its" native-cost card. */
enum class PlatformKind(val label: String) { Android("Android"), Ios("iOS") }

expect val currentPlatform: PlatformKind

/** Live state of one native capability, as reported by the platform (never simulated). */
enum class CapabilityLevel(val label: String) {
    Unsupported("Non supporté"),
    Unavailable("Indisponible"),
    Available("Disponible"),
    Active("Actif"),
    Unknown("Inconnu"),
}

/**
 * One capability row.
 *
 * @param reason why the capability has this level, in the platform's own words when possible.
 * @param startLabel label of the start action; null when there is nothing to start.
 * @param stopLabel label of the stop action.
 * @param canStart / canStop whether the platform currently allows the action (by default: Available / Active).
 */
@Immutable
data class Capability(
    val id: String,
    val title: String,
    val api: String,
    val level: CapabilityLevel,
    val reason: String,
    val startLabel: String? = null,
    val stopLabel: String? = null,
    val canStart: Boolean = startLabel != null && level == CapabilityLevel.Available,
    val canStop: Boolean = stopLabel != null && level == CapabilityLevel.Active,
)

/**
 * A platform capability source. Implementations back these properties with Compose state, so
 * reading them from a composable recomposes on every platform change.
 */
interface CapabilityController {
    val capabilities: List<Capability>
    val facts: List<Pair<String, String>>
    val events: List<String>

    /** A device-wide limitation worth a banner (no outer display, emulator…); null when none. */
    val notice: String?
    fun start(id: String)
    fun stop(id: String)
}

/** What one platform needed for a demo: the honest "native bill". */
@Immutable
data class NativeCost(
    val platform: PlatformKind,
    val apis: List<String>,
    val effort: String,
    val gotchas: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------------------------
// Shared building blocks used by the three platform demos.
// ---------------------------------------------------------------------------------------------

/** Scrollable page that caps the reading width and switches to two columns on wide windows. */
@Composable
internal fun PlatformDemoPage(
    intro: String,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { primary() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { secondary() }
                }
            } else {
                primary()
                secondary()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun LevelPill(level: CapabilityLevel, modifier: Modifier = Modifier) {
    val (bg, fg) = when (level) {
        CapabilityLevel.Active -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        CapabilityLevel.Available -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CapabilityLevel.Unavailable -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CapabilityLevel.Unsupported -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CapabilityLevel.Unknown -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            level.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

/** Capability card: live level, platform reason, and the actions when the capability exists. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CapabilityCard(
    capability: Capability,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(capability.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LevelPill(capability.level)
            }
            Text(
                capability.api,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(capability.reason, style = MaterialTheme.typography.bodySmall)
            val startLabel = capability.startLabel?.takeIf { capability.canStart }
            val stopLabel = capability.stopLabel?.takeIf { capability.canStop }
            if (startLabel != null || stopLabel != null) {
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (startLabel != null) Button(onClick = onStart) { Text(startLabel) }
                    if (stopLabel != null) OutlinedButton(onClick = onStop) { Text(stopLabel) }
                }
            }
        }
    }
}

/** Key/value facts read from the platform. */
@Composable
internal fun FactsCard(title: String, facts: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(title)
            facts.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        key,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
        }
    }
}

/** Last platform events (session started/ended, system refusals…), newest first. */
@Composable
internal fun EventLogCard(events: List<String>, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Journal de la plateforme")
            if (events.isEmpty()) {
                Text(
                    "Aucun événement pour l'instant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            events.take(8).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** "What it took": one card per platform, the running one highlighted. */
@Composable
internal fun NativeCostSection(costs: List<NativeCost>) {
    SectionTitle("Ce qu'il a fallu en natif")
    costs.forEach { cost ->
        val current = cost.platform == currentPlatform
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = if (current) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                CardDefaults.cardColors()
            },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cost.platform.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (current) {
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            "(cet appareil)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                cost.apis.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text(cost.effort, style = MaterialTheme.typography.bodySmall)
                cost.gotchas.forEach {
                    Text(
                        "⚠ $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Short banner for a whole-platform limitation (e.g. "this simulator has no outer display"). */
@Composable
internal fun NoticeBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

/** Visual for content meant for the outer display, shared by Android and iOS. */
@Composable
fun OuterDisplayContent(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(Color(0xFF0B2B25)).safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 480.dp)) {
            Text(
                "Fold Showroom",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Contenu Compose partagé, affiché sur l'écran externe",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
