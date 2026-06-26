package com.via.himalaya.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.via.himalaya.data.models.Trek
import com.via.himalaya.presentation.downloads.DownloadedTrekUIState
import com.via.himalaya.presentation.downloads.DownloadedTrekViewModel
import com.via.himalaya.ui.components.TrekCard

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

@Composable
fun DownloadedTrekScreen(
    state: DownloadedTrekUIState,
    onTrekClicked: (Trek) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {

        Column {
            Spacer(modifier = Modifier.fillMaxWidth().height(20.dp))
            Text("Downloaded Treks")
            Spacer(modifier = Modifier.fillMaxWidth().height(20.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(state.treks) { index, trek ->
                    TrekCard(
                        trek = trek,
                        onClick = { onTrekClicked(trek) }
                    )
                }
            }
        }


    }
}