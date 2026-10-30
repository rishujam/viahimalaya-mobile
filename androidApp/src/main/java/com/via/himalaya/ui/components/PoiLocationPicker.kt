package com.via.himalaya.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.via.himalaya.R
import com.via.himalaya.domain.model.TrekArea

/** Height of ic_pick_pin. Its tip is the bottom edge, so half of this lifts the tip onto the centre. */
private val PIN_HEIGHT = 52.dp

/**
 * "Move the map so the pin is on the place."
 *
 * The pin does not move: it is fixed over the middle of the map, and the user
 * pans and zooms the map underneath it. Select reads whatever is under the tip.
 * Dragging a pin by hand was the alternative, and it is worse here - a finger
 * hides the pin and the spot it is on, and on the tilted camera the trek screen
 * uses, where a dragged pin lands is hard to predict.
 *
 * [mapBottomPadding] must equal the padding the screen gives the map, so that
 * the pin sits over the map's own centre rather than the screen's.
 */
@Composable
fun PoiLocationPicker(
    mapViewportState: MapViewportState,
    boundingBox: List<Double>,
    mapBottomPadding: Dp,
    onConfirm: (lat: Double, lon: Double) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Back means "never mind the pin", not "leave the trek".
    BackHandler(onBack = onCancel)

    // cameraState is Compose state, so both of these follow the map as it moves.
    val center by remember { derivedStateOf { mapViewportState.cameraState?.center } }
    val isInside by remember(boundingBox) {
        derivedStateOf {
            center?.let { TrekArea.contains(it.latitude(), it.longitude(), boundingBox) } ?: false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = mapBottomPadding),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_pick_pin),
                contentDescription = "Pin",
                modifier = Modifier.offset(y = -(PIN_HEIGHT / 2)),
                // Greyed when the pin is somewhere a place cannot be added.
                alpha = if (isInside) 1f else 0.55f,
                colorFilter = if (isInside) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topEnd = 14.dp, topStart = 14.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                text = "Move the map so the pin is on the place",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val current = center
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = when {
                    current == null -> " "
                    isInside -> formatCoordinates(current.latitude(), current.longitude())
                    else -> "This spot is outside the trekking area"
                },
                fontSize = 13.sp,
                color = if (current != null && !isInside) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "Select",
                    onClick = {
                        center?.let { onConfirm(it.latitude(), it.longitude()) }
                    },
                    enabled = isInside,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
