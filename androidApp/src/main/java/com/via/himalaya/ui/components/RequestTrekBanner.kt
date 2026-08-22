package com.via.himalaya.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.via.himalaya.domain.model.AppBanner
import com.via.himalaya.domain.model.BannerAction

/**
 * "Request a trek" prompt, shown above the Explore carousel.
 *
 * Deliberately *not* an item in the LazyColumn. That list is a full-screen
 * snapping carousel whose focus/blur is driven by comparing a trek's position
 * in `state.treks` against a LazyColumn slot index; those two only coincide
 * while treks are the sole items in the list. A banner occupying slot 0 would
 * shift every trek down one and focus the wrong card — the same off-by-one that
 * picking SnapPosition.Start was meant to rule out. Sitting outside the list
 * keeps that invariant intact.
 *
 * It also costs nothing in layout: the carousel sizes its cards from the height
 * BoxWithConstraints reports, which is already measured after this banner has
 * taken its space, so the cards shrink on their own rather than needing a
 * reserved dp value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestTrekBanner(
    banner: AppBanner,
    onClick: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // A swipe either way dismisses. Keyed on currentValue rather than
    // targetValue, and not on confirmValueChange: both of those fire the moment
    // the gesture crosses the threshold, which pulls the banner out of
    // composition mid-swipe so it blinks out of existence instead of sliding
    // away. currentValue only moves once the drag has settled on the new
    // anchor, by which point the slide-off has actually played.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onHide()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        // Nothing is revealed underneath. The swipe removes the banner rather
        // than acting on it, and a coloured action background would advertise a
        // second outcome that does not exist.
        backgroundContent = {},
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                // primaryContainer rather than surfaceVariant: in this theme's
                // light palette surfaceVariant (0xFFF0EEE6) is a hair off the
                // background (0xFFF5F4EE), so the banner would barely read as a
                // separate surface. This pair carries onPrimaryContainer at
                // 13.7:1 light and 5.5:1 dark.
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = banner.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        // Hierarchy comes from size and weight, not from an
                        // alpha on the text colour: fading onPrimaryContainer to
                        // 0.75 drops it to 3.5:1 against the dark-theme
                        // container, under the 4.5:1 floor for body text.
                        text = banner.description,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onHide) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hide this banner",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun RequestTrekBannerPreview() {
    RequestTrekBanner(
        banner = AppBanner(
            title = "Request a trek",
            description = "This is an early release. Tell us which trek you want on the app next.",
            action = BannerAction.REQUEST_TREK_DIALOG
        ),
        onClick = {},
        onHide = {}
    )
}
