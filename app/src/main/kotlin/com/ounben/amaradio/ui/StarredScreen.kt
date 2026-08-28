package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.launch

@Composable
fun StarredScreen(
    viewModel: LocalStationsViewModel,
    customViewModel: CustomStationsViewModel = viewModel(),
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as com.ounben.amaradio.AMARadioApp
    
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        val safeSelectedIndex = remember(pagerState.currentPage) {
            pagerState.currentPage.coerceIn(0, 1)
        }

        SecondaryScrollableTabRow(
            selectedTabIndex = safeSelectedIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(safeSelectedIndex, matchContentSize = false),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {}
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(text = stringResource(R.string.nav_item_starred)) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(text = stringResource(R.string.tab_custom_stations)) }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            verticalAlignment = Alignment.Top,
            key = { page -> page }
        ) { pageIndex ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (pageIndex == 0) {
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
                } else {
                    CustomStationsTab(
                        viewModel = customViewModel,
                        onStationClick = onStationClick,
                        onFavoriteClick = onFavoriteClick,
                        isFavorite = isFavorite
                    )
                }
            }
        }
    }
}
