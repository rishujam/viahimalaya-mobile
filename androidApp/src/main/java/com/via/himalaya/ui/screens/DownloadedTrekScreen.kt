package com.via.himalaya.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.via.himalaya.data.models.Trek
import com.via.himalaya.presentation.downloads.DownloadedTrekUIState
import com.via.himalaya.presentation.downloads.DownloadedTrekViewModel
import com.via.himalaya.ui.components.CarouselTrekCard
import kotlin.math.abs

@Composable
fun DownloadedTrekScreenRoot(
    viewModel: DownloadedTrekViewModel,
    onTrekClicked: (Trek) -> Unit
) {
    val state by viewModel.state.collectAsState()
    DownloadedTrekScreen(
        state = state,
        onTrekClicked = onTrekClicked
    )
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun DownloadedTrekScreen(
    state: DownloadedTrekUIState,
    onTrekClicked: (Trek) -> Unit,
) {
    val listState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(0) }

    // Focus detection: Track which card is closest to viewport center
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty()) {
                    val viewportHeight = listState.layoutInfo.viewportEndOffset
                    val center = viewportHeight / 2
                    val focused = visibleItems.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        abs(itemCenter - center)
                    }?.index
                    if (focused != null && focused != focusedIndex) {
                        focusedIndex = focused
                    }
                }
            }
    }

    // Reset focus when treks change
    LaunchedEffect(state.treks.size) {
        focusedIndex = 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            // Header
            Text(
                text = "Downloads",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp
            )

            Text(
                text = "Your offline treks",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 28.dp),
                fontSize = 14.sp
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                // Available height for LazyColumn (between header and bottom nav)
                val availableHeight = maxHeight
                // Card height = available height - peek space for next card
                val cardHeight = availableHeight - 120.dp

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(state.treks) { index, trek ->
                        val isFocused = index == focusedIndex
                        CarouselTrekCard(
                            trek = trek,
                            isFocused = isFocused,
                            onClick = { onTrekClicked(trek) },
                            cardHeight = cardHeight
                        )
                    }
                }
            }
        }
    }
}