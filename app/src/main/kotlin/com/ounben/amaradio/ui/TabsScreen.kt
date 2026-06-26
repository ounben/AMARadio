package com.ounben.amaradio.ui

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.launch

private sealed class MainTab {
    object Local : MainTab()
    data class Filter(val index: Int, val id: String, val label: String) : MainTab()
    object Search : MainTab()
}

@Composable
fun TabsScreen(
    initialTab: Int = 0,
    onStationClick: (DataRadioStation) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AMARadioApp
    val countryCode = remember { getCountryCode(context) }
    val filterViewModel: FilterViewModel = viewModel()
    val filterState by filterViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 1. Build Dynamic Tab List
    val tabs = remember(countryCode, filterState.tabs) {
        mutableListOf<MainTab>().apply {
            if (countryCode != null) add(MainTab.Local)
            filterState.tabs.forEachIndexed { index, tab ->
                add(MainTab.Filter(index, tab.id, tab.label))
            }
            add(MainTab.Search)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, tabs.size - 1),
        pageCount = { tabs.size }
    )

    // Sync Pager -> ViewModel (so FilterScreen knows which tab is active)
    LaunchedEffect(pagerState.currentPage, tabs) {
        val currentTab = tabs.getOrNull(pagerState.currentPage)
        if (currentTab is MainTab.Filter) {
            filterViewModel.selectTab(currentTab.index)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.weight(1f),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { 
                            val title = when (tab) {
                                is MainTab.Local -> stringResource(R.string.action_local)
                                is MainTab.Filter -> tab.label.ifBlank { "..." }
                                is MainTab.Search -> stringResource(R.string.action_search)
                            }
                            
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }

            // Global Add Filter Button next to tabs
            if (filterState.tabs.size < 5) {
                IconButton(
                    onClick = { 
                        filterViewModel.addTab()
                        coroutineScope.launch {
                            // Find the index of the last filter tab (which is the new one)
                            val lastFilterIndex = tabs.indexOfLast { it is MainTab.Filter }
                            if (lastFilterIndex != -1) {
                                pagerState.animateScrollToPage(lastFilterIndex + 1)
                            }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSecondary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Filter")
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 0,
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            // Safety check for dynamic tabs
            if (pageIndex >= tabs.size) return@HorizontalPager

            when (val tab = tabs[pageIndex]) {
                is MainTab.Local -> {
                    val stationsViewModel: StationsViewModel = viewModel(key = "local_stations")
                    StationsScreen(
                        viewModel = stationsViewModel,
                        url = "json/stations/bycountrycodeexact/$countryCode?order=clickcount&reverse=true",
                        onStationClick = onStationClick,
                        onFavoriteClick = { station ->
                            if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                            else app.favouriteManager.add(station)
                        },
                        isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                    )
                }
                is MainTab.Filter -> {
                    FilterScreen(
                        viewModel = filterViewModel,
                        tabIndex = tab.index,
                        onStationClick = onStationClick,
                        onFavoriteClick = { station ->
                            if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                            else app.favouriteManager.add(station)
                        },
                        isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                    )
                }
                is MainTab.Search -> {
                    val stationsViewModel: StationsViewModel = viewModel(key = "search_stations")
                    StationsScreen(
                        viewModel = stationsViewModel,
                        url = "",
                        onStationClick = onStationClick,
                        onFavoriteClick = { station ->
                            if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                            else app.favouriteManager.add(station)
                        },
                        isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                    )
                }
            }
        }
    }
}

private fun getCountryCode(context: Context): String? {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    return tm?.networkCountryIso?.takeIf { it.length == 2 }
        ?: tm?.simCountryIso?.takeIf { it.length == 2 }
        ?: context.resources.configuration.locales[0].country.takeIf { it.length == 2 }
}
