package com.via.himalaya.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.via.himalaya.ui.MyApplicationTheme

/**
 * Reusable action buttons for ViaHimalaya, mirroring the Hike Detail screen design.
 *
 *  - [PrimaryButton]   solid fill, white centered label — the dominant call-to-action (e.g. "Start Hike").
 *  - [SecondaryButton] white fill with an outline, dark label + optional leading icon (e.g. "Preview Hike").
 *  - [TertiaryButton]  soft tinted fill, dark label + optional leading icon + optional trailing meta
 *                      (e.g. "Download for Offline" … "38 MB").
 *
 * All three are full-width by default, pill-shaped, and pull their colors from [MaterialTheme.colorScheme]
 * so they stay in sync with light/dark theming.
 */

/** Primary — solid fill, white centered label. The main call-to-action. */


/** Secondary — white fill, outlined border, dark label + optional leading icon. */


/** Tertiary — soft tinted fill, dark label, optional leading icon + optional trailing meta text. */


@Preview(showBackground = true, backgroundColor = 0xFFF5F4EE)
@Composable
private fun ViaHimalayaButtonsPreview() {
    MyApplicationTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrimaryButton(text = "Start Hike", onClick = {})
            SecondaryButton(text = "Preview Hike", onClick = {})
            TertiaryButton(
                text = "Download for Offline",
                onClick = {},
                trailingText = "38 MB"
            )
        }
    }
}
