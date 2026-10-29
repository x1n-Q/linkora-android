package top.linkora.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LinkoraColorScheme = lightColorScheme(
    primary = Oxblood,
    onPrimary = WarmPaper,
    primaryContainer = ColorTokens.OxbloodSoft,
    onPrimaryContainer = OxbloodDark,
    secondary = DustyOlive,
    onSecondary = Charcoal,
    background = WarmPaper,
    onBackground = Charcoal,
    surface = PaperRaised,
    onSurface = Charcoal,
    surfaceVariant = ColorTokens.PaperMuted,
    onSurfaceVariant = MutedText,
    outline = StrongBorder,
    outlineVariant = PaleStone,
)

private object ColorTokens {
    val OxbloodSoft = androidx.compose.ui.graphics.Color(0xFFF1E5E5)
    val PaperMuted = androidx.compose.ui.graphics.Color(0xFFF8F6EF)
}

private val LinkoraShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun LinkoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LinkoraColorScheme,
        typography = LinkoraTypography,
        shapes = LinkoraShapes,
        content = content,
    )
}
