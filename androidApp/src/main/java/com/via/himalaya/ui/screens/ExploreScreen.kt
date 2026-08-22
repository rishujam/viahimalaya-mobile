package com.via.himalaya.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.via.himalaya.data.models.Trek
import com.via.himalaya.domain.model.BannerAction
import com.via.himalaya.presentation.explore.ExploreScreenUIEvent
import com.via.himalaya.presentation.explore.ExploreScreenUIState
import com.via.himalaya.presentation.explore.ExploreViewModel
import com.via.himalaya.ui.components.CarouselTrekCard
import com.via.himalaya.ui.components.RequestTrekBanner
import com.via.himalaya.ui.components.RequestTrekDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private const val PAGING_THRESHOLD = 3

@Composable
fun ExploreScreenRoot(
    viewModel: ExploreViewModel,
    onTrekClicked: (Trek) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorToast) {
        state.errorToast?.let { errorMessage ->
            snackBarHostState.showSnackbar(
                message = errorMessage,
                withDismissAction = true
            )
            viewModel.onEvent(ExploreScreenUIEvent.ClearErrorToast)
        }
    }

    LaunchedEffect(state.messageDisplay) {
        state.messageDisplay?.let { message ->
            snackBarHostState.showSnackbar(
                message = message,
                withDismissAction = true
            )
            viewModel.onEvent(ExploreScreenUIEvent.ClearMessageDisplay)
        }
    }

    ExploreScreen(
        state = state,
        onTrekClicked = onTrekClicked,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackBarHostState
    )
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ExploreScreen(
    state: ExploreScreenUIState,
    onTrekClicked: (Trek) -> Unit,
    onEvent: (ExploreScreenUIEvent) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var searchQuery by remember { mutableStateOf("") }

    // Saveable so a rotation with the dialog open keeps both the dialog and
    // whatever has been typed into it.
    var showRequestDialog by rememberSaveable { mutableStateOf(false) }
    var requestText by rememberSaveable { mutableStateOf("") }

    // Discard the draft only once it has actually landed. messageDisplay is set
    // by nothing but a successful feedback submit today — if it ever carries
    // another message, this needs its own signal rather than borrowing that one.
    LaunchedEffect(state.messageDisplay) {
        if (state.messageDisplay != null) {
            requestText = ""
        }
    }

    val listState = rememberLazyListState()
    val snapLayoutInfoProvider = remember(listState) {
        SnapLayoutInfoProvider(listState, SnapPosition.Start)
    }

    // Focus detection: the card closest to viewport center. Derived from the list
    // instead of held in its own state so it survives this screen leaving
    // composition — listState is saveable, so navigating to a trek and back
    // restores the scroll position and the focused card follows it.
    val focusedIndex by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val center = layoutInfo.viewportEndOffset / 2
            val closest = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - center)
            }
            // layoutInfo is empty until the first measure pass but the restored
            // scroll position isn't, so fall back to it rather than blurring
            // every card for a frame on the way back from the detail screen.
            closest?.index ?: listState.firstVisibleItemIndex
        }
    }

    // Search swaps the whole result set out, so send the list back to the top and
    // let focus follow. Keyed on the query the ViewModel applied rather than on
    // treks.size, so paginating in another page no longer yanks focus to index 0.
    var lastAppliedQuery by remember { mutableStateOf(state.searchQuery) }
    LaunchedEffect(state.searchQuery) {
        if (state.searchQuery != lastAppliedQuery) {
            lastAppliedQuery = state.searchQuery
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(searchQuery, state.isSearching) {
        if (searchQuery.isBlank()) {
            onEvent(ExploreScreenUIEvent.OnClearSearch)
        } else if (!state.isSearching) {
            delay(1000)
            onEvent(ExploreScreenUIEvent.OnSearchTrek(searchQuery.trim()))
        }
    }

    // Pagination logic (existing)
    LaunchedEffect(listState, state.isLoading, state.hasNextPage, state.isSearching) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount

            lastVisibleItem != null &&
                    lastVisibleItem.index >= totalItems - PAGING_THRESHOLD &&
                    !state.isLoading &&
                    state.hasNextPage &&
                    !state.isSearching
        }
            .distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore) {
                    onEvent(ExploreScreenUIEvent.OnLoadMore)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            // Header
            Text(
                text = "Explore",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp
            )

            Text(
                text = "Find your next adventure",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 28.dp),
                fontSize = 14.sp
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search treks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,    // White background
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface   // White background
                )
            )

            // Above the carousel rather than inside it. The LazyColumn's
            // focus/blur compares a trek's index in state.treks against a
            // LazyColumn slot index, and those agree only while treks are the
            // list's only items — a banner in slot 0 would focus the wrong card.
            val banner = state.banner
            if (banner != null && !state.isBannerHidden) {
                RequestTrekBanner(
                    banner = banner,
                    onClick = {
                        // Exhaustive on purpose: a new BannerAction must be
                        // given behaviour here before it will compile, rather
                        // than falling through to a tap that does nothing.
                        when (banner.action) {
                            BannerAction.REQUEST_TREK_DIALOG -> showRequestDialog = true
                        }
                    },
                    onHide = { onEvent(ExploreScreenUIEvent.OnHideBanner) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                // Available height for LazyColumn (between search bar and bottom nav)
                val availableHeight = maxHeight
                // Card height = available height - peek space for next card
                val cardHeight = availableHeight - 60.dp

                LazyColumn(
                    state = listState,
                    // One card at a time: releasing the drag settles on whichever
                    // card is showing more of itself, so the list never rests
                    // between two. SnapPosition.Start keeps the settled card at
                    // offset 0, which means firstVisibleItemIndex, the snap
                    // target and the focused card are always the same item — so
                    // the position saved on the way to a trek restores to a card
                    // that is unambiguously in focus on the way back.
                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
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

                    // Loading indicator at the bottom when paginating
                    if (state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showRequestDialog) {
            RequestTrekDialog(
                text = requestText,
                onTextChange = { requestText = it },
                onSubmit = {
                    onEvent(ExploreScreenUIEvent.OnRequestTrek(requestText.trim()))
                    // Dialog closes straight away so the send feels instant, but
                    // the draft is deliberately kept — the request may still
                    // fail, and this app's users are the ones on bad
                    // connections. Reopening the banner restores what they
                    // wrote instead of asking them to type it again.
                    showRequestDialog = false
                },
                onDismiss = { showRequestDialog = false }
            )
        }

        // Snackbar for error messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(16.dp)
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Preview
@Composable
fun ExploreScreenPreview() {
    ExploreScreen(
        state = ExploreScreenUIState(),
        onTrekClicked = {},
        onEvent = {},
        snackbarHostState = remember { SnackbarHostState() }
    )
}