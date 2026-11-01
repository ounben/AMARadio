package com.ounben.amaradio.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.ounben.amaradio.R
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.launch

import com.ounben.amaradio.recorder.RecordingsTab

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
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val uiState by localStationsViewModel.uiState.collectAsState()
    val tracks = trackHistoryViewModel.allHistoryPaged.collectAsLazyPagingItems()
    var trackWithOptions by remember { mutableStateOf<TrackHistoryEntry?>(null) }
    var stationToDelete by remember { mutableStateOf<DataRadioStation?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        val safeSelectedIndex = remember(pagerState.currentPage) {
            pagerState.currentPage.coerceIn(0, 2)
        }

        SecondaryScrollableTabRow(
            selectedTabIndex = safeSelectedIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            indicator = {
                if (safeSelectedIndex in 0..2) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(safeSelectedIndex),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                text = { Text(text = stringResource(R.string.tab_recordings)) }
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
                when (pageIndex) {
                    0 -> {
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
                                stationToDelete = station
                            }
                        )
                    }
                    1 -> {
                        TrackList(
                            tracks = tracks,
                            onTrackClick = onTrackClick,
                            onTrackLongClick = { trackWithOptions = it }
                        )
                    }
                    2 -> {
                        RecordingsTab()
                    }
                }
            }
        }
    }

    stationToDelete?.let { station ->
        val isDark = isSystemInDarkTheme()
        val deleteRedColor = if (isDark) SolidRedError else SolidRedErrorLight

        AlertDialog(
            onDismissRequest = { stationToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.action_delete),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = station.Name,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        app.historyManager.remove(station.StationUuid)
                        stationToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = deleteRedColor)
                ) {
                    Text(
                        text = stringResource(R.string.yes),
                        fontWeight = FontWeight.Bold,
                        color = deleteRedColor
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { stationToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(
                        text = stringResource(R.string.no),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    trackWithOptions?.let { track ->
        TrackOptionsDialog(
            track = track,
            onDismiss = { trackWithOptions = null }
        )
    }
}
