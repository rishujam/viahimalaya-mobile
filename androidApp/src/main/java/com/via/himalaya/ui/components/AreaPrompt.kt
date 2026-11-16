package com.via.himalaya.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val SHOW_FOR_MS = 3_000L
private const val FADE_MS = 300

/**
 * "You are not in the trekking area", shown once each time [show] turns true -
 * the view model sets it on entry when the fix is outside the area and on Start
 * Hike pressed outside it. It fades out after a few seconds, or can be swiped
 * away either side; either way [onGone] tells the view model to clear [show], so
 * the next press can set it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaPrompt(show: Boolean, onGone: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    // Bumped per showing, so each one gets a fresh swipe state below.
    var showing by remember { mutableIntStateOf(0) }

    // Keyed on show, and onGone is only called at the end: clearing the flag
    // straight away would restart this effect and cancel the timer.
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        showing++
        visible = true
        delay(SHOW_FOR_MS)
        visible = false
        onGone()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS)),
        modifier = modifier
    ) {
        // A fresh swipe state per showing, so a prompt swiped away last time
        // does not come back already off screen.
        key(showing) {
            val dismissState = rememberSwipeToDismissBoxState()
            // Keyed on currentValue, as in RequestTrekBanner: it only moves once
            // the slide-off has played, so the prompt slides away instead of
            // blinking out mid-gesture.
            LaunchedEffect(dismissState.currentValue) {
                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                    visible = false
                    onGone()
                }
            }
            SwipeToDismissBox(state = dismissState, backgroundContent = {}) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF9800))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "You are not in the trekking area",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
