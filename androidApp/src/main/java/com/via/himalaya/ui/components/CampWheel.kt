package com.via.himalaya.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.via.himalaya.data.models.PoiCategory
import com.via.himalaya.data.models.TrekPoi
import kotlin.math.abs

private val ITEM_HEIGHT = 44.dp
private const val VISIBLE_ITEMS = 3

/**
 * A scroll wheel over the campsites, in trail order.
 *
 * The centred row is the *focused* one, and focus is a preview rather than a
 * commitment: the caller lights that camp up on the map and opens its card, so
 * scrolling the wheel is a way of touring the options rather than a menu you
 * commit to blind. Nothing is chosen until the caller says so.
 *
 * Camps already used by an earlier day, or lying behind the last one chosen, are
 * shown dimmed rather than removed - seeing where you have already been is the
 * context that makes the next choice make sense - but [isSelectable] tells the
 * caller when the focused row cannot actually be taken.
 */
@Composable
fun CampWheel(
    camps: List<TrekPoi>,
    isSelectable: (TrekPoi) -> Boolean,
    onFocusedChange: (TrekPoi?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (camps.isEmpty()) return

    val listState = rememberLazyListState()
    val snapProvider = remember(listState) {
        SnapLayoutInfoProvider(listState, SnapPosition.Center)
    }

    // Same trick as the Explore carousel: derive focus from the layout rather
    // than storing it, so it cannot drift out of step with what is on screen.
    val focusedIndex by remember(listState, camps.size) {
        derivedStateOf {
            val info = listState.layoutInfo
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - centre)
            }?.index ?: listState.firstVisibleItemIndex
        }
    }

    LaunchedEffect(listState, camps) {
        snapshotFlow { focusedIndex }
            .collect { onFocusedChange(camps.getOrNull(it)) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT * VISIBLE_ITEMS)
    ) {
        // The selection band, drawn under the rows so the centred camp reads as
        // "the one in the window" without needing a highlight on the row itself.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ITEM_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
        )

        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(snapProvider),
            // Half a wheel of padding at each end so the first and last camps can
            // still reach the centre band.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = ITEM_HEIGHT * (VISIBLE_ITEMS - 1) / 2
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(camps) { index, camp ->
                val focused = index == focusedIndex
                val selectable = isSelectable(camp)

                // Unfocused rows are indented, so the centred one reads as
                // stepping forward out of the list rather than just being a
                // slightly darker row among identical ones.
                val startPad by animateDpAsState(
                    targetValue = if (focused) 14.dp else 30.dp,
                    label = "CampRowIndent"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT)
                        .padding(start = startPad, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Name and distance only. Height matters when reviewing a
                    // plan, not when scanning for where to stop - and a third
                    // number on every row makes the wheel harder to read at the
                    // speed it is meant to be scrolled.
                    Text(
                        modifier = Modifier.weight(1f),
                        text = camp.name
                            ?: "Unnamed ${PoiCategory.label(camp.category).lowercase()}",
                        fontSize = if (focused) 15.sp else 14.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        color = rowColor(focused, selectable)
                    )
                    Text(
                        text = "%.1f km".format(camp.distAlongKm),
                        fontSize = 12.sp,
                        color = rowColor(focused, selectable)
                    )
                }
            }
        }
    }
}

@Composable
private fun rowColor(focused: Boolean, selectable: Boolean): Color = when {
    !selectable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    focused -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
}
