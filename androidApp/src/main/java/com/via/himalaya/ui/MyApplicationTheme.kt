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
import com.via.himalaya.presentation.components.getInterFontFamily

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {

    val colors = if (darkTheme) {
        // Dark theme using ViaHimalaya colors with adjustments
        darkColorScheme(
            primary = Color(0xFF466638),        // (Same green, more vibrant on dark)
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF689F38), // A more saturated green
            onPrimaryContainer = Color(0xFF1A1F1A), // Dark version of onPrimary

            secondary = Color(0xFF8A5C1A),      // (Same brown)
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFBCAAA4), // Muted dark tan
            onSecondaryContainer = Color(0xFF1A1F1A),

            tertiary = Color(0xFF9C4032),       // (Same red)
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFCCBC), // Muted peach
            onTertiaryContainer = Color(0xFF1A1F1A),

            error = Color(0xFFFFB4AB),          // Muted semantic red for dark
            onError = Color(0xFF1A1F1A),
            errorContainer = Color(0xFFB00020), // Standard semantic error background
            onErrorContainer = Color(0xFFFFDAD6),

            background = Color(0xFF1A1F1A),     // (Existing dark BG)
            onBackground = Color(0xFFF5F4EE),   // (Existing text)
            surface = Color(0xFF2A2F2A),        // (Existing surface)
            onSurface = Color(0xFFF5F4EE),
            surfaceVariant = Color(0xFF3A3F3A), // (Existing variant)
            onSurfaceVariant = Color(0xFFAAA89E), // TEXT_3
            outline = Color(0xFF7A7A6E),         // TEXT_2
            outlineVariant = Color(0xFF8D6E63)     // Accent outline for brown elements
        )
    } else {
        // Light theme using ViaHimalaya colors from Figma
        lightColorScheme(
            primary = Color(0xFF466638),        // PIN_START green (Main accent)
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDCE8D2), // EASY_BG
            onPrimaryContainer = Color(0xFF1A1F14), // PRIMARY sage

            secondary = Color(0xFF8A5C1A),      // MOD_FG brown (Secondary accent)
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFD180), // Harmonized light tan
            onSecondaryContainer = Color(0xFF795548), // Darker brown text

            tertiary = Color(0xFF9C4032),       // PIN_END red (Accent/Alert)
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD6CB), // Harmonized light peach
            onTertiaryContainer = Color(0xFFB00020), // Standard semantic error text

            error = Color(0xFFB00020),          // Standard semantic error
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFCD8D8), // Lighter red
            onErrorContainer = Color(0xFF3F0000),

            background = Color(0xFFF5F4EE),     // BG cream background
            onBackground = Color(0xFF1A1F1A),   // TEXT dark text
            surface = Color(0xFFFFFFFF),        // WHITE
            onSurface = Color(0xFF1A1F1A),      // TEXT dark text
            surfaceVariant = Color(0xFFF0EEE6), // CHIP_BG/SECONDARY_BG
            onSurfaceVariant = Color(0xFF7A7A6E), // TEXT_2 secondary text
            outline = Color(0xFFEBE9E2),        // DIVIDER
            outlineVariant = Color(0xFFBCAAA4)    // Accent outline for brown elements
        )
    }
    val typography = Typography(
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        )
    )
//    val typography2 = Typography(
//        bodyMedium = TextStyle(
//            fontFamily = getInterFontFamily(),
//            fontWeight = FontWeight.Normal,
//            fontSize = 16.sp
//        )
//    )
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
