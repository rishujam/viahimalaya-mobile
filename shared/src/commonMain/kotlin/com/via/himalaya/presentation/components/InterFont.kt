package com.via.himalaya.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import viahimalaya_mobile.shared.generated.resources.Res
import viahimalaya_mobile.shared.generated.resources.inter_font

@Composable
fun getInterFontFamily(): FontFamily {
    return FontFamily(Font(Res.font.inter_font))
}