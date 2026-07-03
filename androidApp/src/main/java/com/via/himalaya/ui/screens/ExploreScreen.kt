package com.via.himalaya.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.via.himalaya.data.models.Trek
import com.via.himalaya.presentation.explore.ExploreScreenUIEvent
import com.via.himalaya.presentation.explore.ExploreScreenUIState
import com.via.himalaya.presentation.explore.ExploreViewModel
import com.via.himalaya.ui.components.TrekCard
import kotlinx.coroutines.flow.distinctUntilChanged

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
    
    ExploreScreen(
        state = state,
        onTrekClicked = onTrekClicked,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackBarHostState
    )
}

@Composable
fun ExploreScreen(
    state: ExploreScreenUIState,
    onTrekClicked: (Trek) -> Unit,
    onEvent: (ExploreScreenUIEvent) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(searchQuery, state.isSearching) {
        if (searchQuery.isBlank()) {
            onEvent(ExploreScreenUIEvent.OnClearSearch)
        } else if (!state.isSearching) {
            delay(1000)
            onEvent(ExploreScreenUIEvent.OnSearchTrek(searchQuery.trim()))
        }
    }

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
                .padding(16.dp)
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
            fontSize = 12.sp
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Search treks, valleys, peaks…",
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
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = Color(0xFFFFFFFF),    // White background
                unfocusedContainerColor = Color(0xFFFFFFFF)   // White background
            )
        )

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(state.treks) { index, trek ->
                    TrekCard(
                        trek = trek,
                        onClick = { onTrekClicked(trek) }
                    )
                }
                
                // Loading indicator at the bottom when paginating
                if (state.isLoading && state.treks.isNotEmpty()) {
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

