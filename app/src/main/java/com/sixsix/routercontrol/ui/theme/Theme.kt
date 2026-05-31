package com.sixsix.routercontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 666 Assets palette — deep ink, warm cream, gold accent
val Ink        = Color(0xFF0A0E13)
val InkElev    = Color(0xFF0E131A)
val Surface1   = Color(0xFF11161D)
val Surface2   = Color(0xFF161C25)
val SurfaceHov = Color(0xFF1A2230)
val BorderCol  = Color(0xFF1F2630)
val BorderStrong = Color(0xFF2A3340)
val TextMain   = Color(0xFFF5F1EA)
val Text2      = Color(0xFF8B95A3)
val Text3      = Color(0xFF5A6371)
val Gold       = Color(0xFFC89968)
val GoldBright = Color(0xFFD4A574)
val Success    = Color(0xFF4ADE80)
val Warn       = Color(0xFFFBBF24)
val Danger     = Color(0xFFF87171)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = GoldBright,
    background = Ink,
    onBackground = TextMain,
    surface = Surface1,
    onSurface = TextMain,
    surfaceVariant = Surface2,
    onSurfaceVariant = Text2,
    error = Danger,
    outline = BorderCol
)

@Composable
fun RouterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
