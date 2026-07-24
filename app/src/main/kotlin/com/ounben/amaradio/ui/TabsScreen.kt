package com.ounben.amaradio.ui

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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

    // Build Dynamic Tab List (Removed Search Tab)
    val tabs = remember(countryCode, filterState.tabs) {
        mutableListOf<MainTab>().apply {
            if (countryCode != null) add(MainTab.Local)
            filterState.tabs.forEachIndexed { index, tab ->
                add(MainTab.Filter(index, tab.id, tab.label))
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, tabs.size - 1),
        pageCount = { tabs.size }
    )

    // Sync Pager -> ViewModel
    LaunchedEffect(pagerState.currentPage, tabs) {
        val currentTab = tabs.getOrNull(pagerState.currentPage)
        if (currentTab is MainTab.Filter) {
            filterViewModel.selectTab(currentTab.index)
        }
    }

    // Auto-scroll to new tab when added
    var lastTabCount by remember { mutableIntStateOf(tabs.size) }
    LaunchedEffect(tabs.size) {
        if (tabs.size > lastTabCount) {
            pagerState.animateScrollToPage(tabs.size - 1)
        }
        lastTabCount = tabs.size
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondary),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val safeSelectedIndex = remember(pagerState.currentPage, tabs.size) {
                pagerState.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
            }

            SecondaryScrollableTabRow(
                selectedTabIndex = safeSelectedIndex,
                modifier = Modifier.weight(1f),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(safeSelectedIndex),
                        color = MaterialTheme.colorScheme.primary
                    )
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

            if (filterState.tabs.size < 5) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.accessibility_add_filter),
                    tint = AmaradioAmber,
                    modifier = Modifier
                        .clickable { filterViewModel.addTab() }
                        .padding(16.dp)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 0,
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
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
                        }
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
                        }
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
