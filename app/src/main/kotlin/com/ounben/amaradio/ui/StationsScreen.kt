package com.ounben.amaradio.ui

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.ounben.amaradio.R
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

    StationListTemplate(
        stations = uiState.filteredStations,
        isGrid = uiState.isGrid,
        isLoading = uiState.isLoading,
        error = uiState.error,
        emptyMessage = stringResource(R.string.searchpreference_no_results),
        onRetry = { url?.let { viewModel.loadStations(it, forceUpdate = true) } },
        onRefresh = { url?.let { viewModel.loadStations(it, forceUpdate = true) } },
        onStationClick = onStationClick,
        onFavoriteClick = onFavoriteClick,
        isFavorite = isFavorite
    )
}
