package com.ounben.amaradio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val app = context.applicationContext as com.ounben.amaradio.AMARadioApp
    
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
            isFavorite = isFavorite,
            onDeleteClick = { station ->
                app.favouriteManager.remove(station.StationUuid)
            }
        )
    }
}
