package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.station.DataRadioStation

@Composable
fun StationsScreen(
    viewModel: StationsViewModel,
    url: String?,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(url) {
        url?.let { viewModel.loadStations(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { url?.let { viewModel.loadStations(it, forceUpdate = true) } }) {
                    Text("Retry")
                }
            }
        } else {
            StationList(
                stations = uiState.filteredStations,
                isGrid = uiState.isGrid,
                onStationClick = onStationClick,
                onFavoriteClick = onFavoriteClick,
                isFavorite = isFavorite
            )
        }
    }
}
