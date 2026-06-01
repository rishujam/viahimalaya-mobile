package com.via.himalaya.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        // Dark theme using ViaHimalaya colors with adjustments
        darkColorScheme(
            primary = Color(0xFF466638),        // PIN_START green
            secondary = Color(0xFF8A5C1A),      // MOD_FG brown
            tertiary = Color(0xFF9C4032),       // PIN_END/HARD_FG red
            background = Color(0xFF1A1F1A),     // Dark version of TEXT
            surface = Color(0xFF2A2F2A),        // Darker surface
            onBackground = Color(0xFFF5F4EE),   // BG as text on dark
            onSurface = Color(0xFFF5F4EE),      // BG as text on dark
            onPrimary = Color(0xFFFFFFFF),      // WHITE
            onSecondary = Color(0xFFFFFFFF),    // WHITE
            surfaceVariant = Color(0xFF3A3F3A), // Darker variant
            onSurfaceVariant = Color(0xFFAAA89E), // TEXT_3
            outline = Color(0xFF7A7A6E)         // TEXT_2
        )
    } else {
        // Light theme using ViaHimalaya colors from Figma
        lightColorScheme(
            primary = Color(0xFF1A1F14),        // PRIMARY - dark sage
            secondary = Color(0xFF466638),      // PIN_START green
            tertiary = Color(0xFF9C4032),       // PIN_END/HARD_FG red
            background = Color(0xFFF5F4EE),     // BG - cream background
            surface = Color(0xFFFFFFFF),        // WHITE
            onBackground = Color(0xFF1A1F1A),   // TEXT - dark text
            onSurface = Color(0xFF1A1F1A),      // TEXT - dark text
            onPrimary = Color(0xFFFFFFFF),      // WHITE
            onSecondary = Color(0xFFFFFFFF),    // WHITE
            surfaceVariant = Color(0xFFF0EEE6), // CHIP_BG/SECONDARY_BG
            onSurfaceVariant = Color(0xFF7A7A6E), // TEXT_2 - secondary text
            outline = Color(0xFFEBE9E2),        // DIVIDER
            primaryContainer = Color(0xFFDCE8D2), // EASY_BG
            onPrimaryContainer = Color(0xFF466638) // EASY_FG
        )
    }
    val typography = Typography(
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        )
    )
    val shapes = Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(0.dp)
    )

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content
    )
}
