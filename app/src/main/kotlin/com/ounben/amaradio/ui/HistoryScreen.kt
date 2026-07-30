package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.ounben.amaradio.R
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    localStationsViewModel: LocalStationsViewModel,
    trackHistoryViewModel: TrackHistoryViewModel,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    onTrackClick: (TrackHistoryEntry) -> Unit,
    isFavorite: (String) -> Boolean
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.ounben.amaradio.AMARadioApp
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val uiState by localStationsViewModel.uiState.collectAsState()
    val tracks = trackHistoryViewModel.allHistoryPaged.collectAsLazyPagingItems()
    var trackWithOptions by remember { mutableStateOf<TrackHistoryEntry?>(null) }

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
                    Modifier.tabIndicatorOffset(safeSelectedIndex),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {}
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(text = stringResource(R.string.nav_item_history)) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(text = stringResource(R.string.tab_player_history)) }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 0,
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
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
                        app.historyManager.remove(station.StationUuid)
                    }
                )
            } else {
                TrackList(
                    tracks = tracks,
                    onTrackClick = onTrackClick,
                    onTrackLongClick = { trackWithOptions = it }
                )
            }
        }
    }

    trackWithOptions?.let { track ->
        TrackOptionsDialog(
            track = track,
            onDismiss = { trackWithOptions = null }
        )
    }
}

