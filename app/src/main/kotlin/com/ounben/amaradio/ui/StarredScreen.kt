package com.ounben.amaradio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation

@Composable
fun StarredScreen(
    viewModel: LocalStationsViewModel,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    
    SingleTabContainer(titleRes = R.string.nav_item_starred) {
        StationListTemplate(
            stations = uiState.filteredStations,
            isGrid = uiState.isGrid,
            isLoading = false,
            error = null,
            emptyMessage = stringResource(R.string.searchpreference_no_results),
            onRefresh = { /* Local data, already reactive */ },
            onStationClick = onStationClick,
            onFavoriteClick = onFavoriteClick,
            isFavorite = isFavorite
        )
    }
}
