package dev.showroom.fold.core

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6B5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635D),
    secondaryContainer = Color(0xFFCCE8E0),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF7A4E9B),
    tertiaryContainer = Color(0xFFF1DBFF),
    onTertiaryContainer = Color(0xFF2E0A4D),
    background = Color(0xFFF6FAF8),
    surface = Color(0xFFF6FAF8),
    surfaceVariant = Color(0xFFDBE5E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD6C3),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFFA6F2DF),
    secondary = Color(0xFFB1CCC4),
    secondaryContainer = Color(0xFF334B45),
    onSecondaryContainer = Color(0xFFCCE8E0),
    tertiary = Color(0xFFDDB8FF),
    tertiaryContainer = Color(0xFF603581),
    onTertiaryContainer = Color(0xFFF1DBFF),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF3F4946),
)

@Composable
fun ShowroomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** Badge colours for each [Sharing] level: (container, content). */
@Composable
fun Sharing.colors(): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return when (this) {
        Sharing.Common -> if (dark) Color(0xFF1E4D2B) to Color(0xFFB7F0C2) else Color(0xFFCDF2D3) to Color(0xFF0C3B18)
        Sharing.ExpectActual -> if (dark) Color(0xFF5A4300) to Color(0xFFFFDF9B) else Color(0xFFFFE8B0) to Color(0xFF4A3600)
        Sharing.PlatformSpecific -> if (dark) Color(0xFF6B2A22) to Color(0xFFFFDAD4) else Color(0xFFFFDAD4) to Color(0xFF5C1A12)
    }
}

@Composable
fun SharingBadge(sharing: Sharing, modifier: Modifier = Modifier) {
    val (container, content) = sharing.colors()
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = sharing.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Temporary content for a demo that is not implemented yet. */
@Composable
fun DemoPlaceholder(text: String = "Démo en cours de construction") {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
