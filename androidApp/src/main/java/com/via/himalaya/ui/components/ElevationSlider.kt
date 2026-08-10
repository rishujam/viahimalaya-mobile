package com.via.himalaya.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.via.himalaya.data.models.TrekElevationPoint
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf

private val SLIDER_WIDTH = 56.dp
private val THUMB_SIZE = 26.dp

/**
 * Vertical elevation profile you can scrub.
 *
 * The shape *is* the data: distance along the trail runs bottom (trailhead) to
 * top (far end), and each sample sticks out to the left in proportion to how
 * high the ground is. So the silhouette is the trek's climb, read bottom-up, and
 * the pass is the widest bulge. A plain track would have told you nothing about
 * the terrain, which is the whole reason this control exists rather than a
 * number in the detail sheet.
 *
 * [onEngagedChange] brackets the whole press, drag and release, so the caller
 * can swap the map between browsing POIs and following the trekker.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ElevationSlider(
    points: List<TrekElevationPoint>,
    index: Int,
    isEngaged: Boolean,
    onIndexChange: (Int) -> Unit,
    onEngagedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val density = LocalDensity.current
    val lowest = remember(points) { points.minOf { it.elevationM } }
    val highest = remember(points) { points.maxOf { it.elevationM } }

    val profileColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val walkedColor = MaterialTheme.colorScheme.primary

    // Spans the caller's full width, but only the right-hand column draws or
    // listens. That gives the readout the rest of the row to lay out in, so it
    // is sized by its own text on every screen instead of being nudged into
    // place with a hardcoded offset.
    BoxWithConstraints(modifier = modifier) {
        val thumbHalfPx = with(density) { (THUMB_SIZE / 2).toPx() }
        val trackHeightPx = with(density) { (maxHeight - THUMB_SIZE).toPx() }

        // Held in pixels rather than recomputed from `index` so the thumb tracks
        // the finger exactly instead of snapping between samples - on a 253
        // sample trek those steps would be visible stutter.
        var thumbY by remember { mutableFloatStateOf(Float.NaN) }
        val resolvedY = if (thumbY.isNaN()) {
            (1f - index.toFloat() / (points.size - 1)) * trackHeightPx
        } else {
            thumbY
        }

        fun publish(rawY: Float) {
            val y = rawY.coerceIn(0f, trackHeightPx)
            thumbY = y
            val fromBottom = 1f - y / trackHeightPx
            onIndexChange(
                (fromBottom * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
            )
        }

        // The profile column: the only part that draws the terrain and the only
        // part that takes touches, so the readout beside it never eats a map
        // gesture.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(SLIDER_WIDTH)
                .fillMaxHeight()
                .pointerInput(points.size, trackHeightPx) {
                    // One gesture loop for press, drag and release. Two separate
                    // detectors (tap + drag) fight each other: the tap detector's
                    // release fires as soon as a drag begins, which flipped the
                    // map back to POIs half a second into every scrub.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        onEngagedChange(true)
                        publish(down.position.y - thumbHalfPx)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            change.consume()
                            if (!change.pressed) break
                            publish(change.position.y - thumbHalfPx)
                        }
                        onEngagedChange(false)
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = THUMB_SIZE / 2)
            ) {
                drawProfile(points, lowest, highest, points.size - 1, profileColor)
                // The walked portion is drawn over the full silhouette, so the
                // fill doubles as a progress indicator without a second control.
                drawProfile(points, lowest, highest, index, walkedColor)
            }
        }

        // Readout and thumb, packed against the right edge of the full width.
        // Ordinary layout, so the label is as wide as its own text on any screen
        // and no offset has to guess at it. Neither of these takes pointer input,
        // so touches fall through to the profile column beneath and the thumb
        // stays draggable.
        // Height is measured, never fixed. Pinning this row to THUMB_SIZE capped
        // the label at 26dp, and 8dp of padding either side of a 12sp line needs
        // about 30 - so the bottom of the text was being clipped off. Letting it
        // wrap and then centring it on the thumb position keeps the label whole
        // at any font scale.
        var rowHeightPx by remember { mutableIntStateOf(0) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (resolvedY + thumbHalfPx - rowHeightPx / 2f).toInt()) }
                .onSizeChanged { rowHeightPx = it.height }
                // Centres the thumb over the profile column, derived from the two
                // widths rather than eyeballed.
                .padding(end = (SLIDER_WIDTH - THUMB_SIZE) / 2),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isEngaged) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${points[index.coerceIn(points.indices)].elevationM} m",
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        // Equal padding alone still reads as bottom-heavy: font
                        // metrics reserve space above and below the glyphs that
                        // nothing draws into. Trimming it makes the 8.dp land
                        // evenly on all four sides.
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            )
                        ),
                        color = if (isEngaged) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(THUMB_SIZE)
                        .shadow(6.dp, RoundedCornerShape(THUMB_SIZE / 2))
                        .clip(RoundedCornerShape(THUMB_SIZE / 2))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(THUMB_SIZE / 2))
                        .background(if (isEngaged) walkedColor else profileColor.copy(alpha = 1f))
                )
        }
    }
}

/**
 * Fills the profile silhouette from the trailhead up to [upTo].
 *
 * Anchored on the right edge because the control lives against the screen edge -
 * the peaks reach inwards, towards the map, where there is room for them.
 */
private fun DrawScope.drawProfile(
    points: List<TrekElevationPoint>,
    lowest: Int,
    highest: Int,
    upTo: Int,
    color: Color
) {
    if (upTo <= 0) return

    val span = (highest - lowest).coerceAtLeast(1).toFloat()
    val lastIndex = points.size - 1

    // Never let the flattest stretch collapse to a hairline: a sliver of width
    // still has to read as "you are somewhere on this trek".
    val minWidth = size.width * 0.18f
    val range = size.width - minWidth

    fun xFor(p: TrekElevationPoint) =
        size.width - (minWidth + ((p.elevationM - lowest) / span) * range)

    fun yFor(i: Int) = (1f - i.toFloat() / lastIndex) * size.height

    val path = Path().apply {
        moveTo(size.width, yFor(0))
        for (i in 0..upTo) {
            lineTo(xFor(points[i]), yFor(i))
        }
        lineTo(size.width, yFor(upTo))
        close()
    }
    drawPath(path, color)
}
