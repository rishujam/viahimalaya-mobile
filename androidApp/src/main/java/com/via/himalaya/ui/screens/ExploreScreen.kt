package com.via.himalaya.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable
fun ExploreScreenRoot(
    viewModel: ExploreViewModel,
    onTrekClicked: (Trek) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(state.errorState) {
        state.errorState?.let { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                withDismissAction = true
            )
            viewModel.onEvent(ExploreScreenUIEvent.ClearErrorMessage)
        }
    }
    
    ExploreScreen(
        state = state,
        onTrekClicked = onTrekClicked,
        onLoadMore = { viewModel.onEvent(ExploreScreenUIEvent.OnLoadMore) },
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun ExploreScreen(
    state: ExploreScreenUIState,
    onTrekClicked: (Trek) -> Unit,
    onLoadMore: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredTreks = remember(state.treks, searchQuery) {
        if (searchQuery.isBlank()) {
            state.treks
        } else {
            val q = searchQuery.trim()
            state.treks.filter { trek ->
                trek.name.contains(q, ignoreCase = true) ||
                    trek.location.contains(q, ignoreCase = true)
            }
        }
    }

    // Detect when user reaches the end of the list and trigger pagination
    LaunchedEffect(listState, state.isLoading, state.hasNextPage, searchQuery) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount
            
            lastVisibleItem != null &&
            lastVisibleItem.index >= totalItems - 3 && // Load when 3 items from the end
            !state.isLoading &&
            state.hasNextPage &&
            searchQuery.isBlank() // Only paginate when not searching
        }
            .distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore) {
                    onLoadMore()
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
                itemsIndexed(filteredTreks) { index, trek ->
                    TrekCard(
                        trek = trek,
                        onClick = { onTrekClicked(trek) }
                    )
                }
                
                // Loading indicator at the bottom when paginating
                if (state.isLoading && filteredTreks.isNotEmpty()) {
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

