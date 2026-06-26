package com.ounben.amaradio.ui

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.station.SearchStyle
import kotlinx.coroutines.launch

private data class TabData(val id: Int, val titleRes: Int)

private const val IDX_LOCAL = 0
private const val IDX_FILTER = 1
private const val IDX_TOP_CLICK = 2
private const val IDX_CURRENTLY_HEARD = 3
private const val IDX_COUNTRIES = 4
private const val IDX_SEARCH = 5

private val addresses = arrayOf(
    "json/stations/bycountryexact/internet?order=clickcount&reverse=true",
    "", // Filter handled via FilterScreen
    "json/stations/topclick/100",
    "json/stations/lastclick/100",
    "json/countrycodes",
    ""  // Search handled via StationsViewModel.search
)

@Composable
fun TabsScreen(
    initialTab: Int = 0,
    onStationClick: (DataRadioStation) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AMARadioApp
    val countryCode = remember { getCountryCode(context) }
    
    val activeTabs = remember(countryCode) {
        mutableListOf<TabData>().apply {
            if (countryCode != null) add(TabData(IDX_LOCAL, R.string.action_local))
            add(TabData(IDX_FILTER, R.string.action_filter))
            add(TabData(IDX_TOP_CLICK, R.string.action_top_click))
            add(TabData(IDX_CURRENTLY_HEARD, R.string.action_currently_playing))
            add(TabData(IDX_COUNTRIES, R.string.action_countries))
            add(TabData(IDX_SEARCH, R.string.action_search))
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, activeTabs.size - 1),
        pageCount = { activeTabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialTab) {
        val targetPage = initialTab.coerceIn(0, activeTabs.size - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    val sharedPref = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var filterTabName by remember { mutableStateOf(sharedPref.getString("filter_tab_name", "") ?: "") }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "filter_tab_name") {
                filterTabName = sharedPref.getString("filter_tab_name", "") ?: ""
            }
        }
        sharedPref.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPref.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
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
            activeTabs.forEachIndexed { index, tab ->
                val title = if (tab.id == IDX_FILTER && filterTabName.isNotEmpty()) filterTabName 
                            else stringResource(id = tab.titleRes)
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 0,
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            val tab = activeTabs[pageIndex]
            
            when (tab.id) {
                IDX_FILTER -> {
                    val filterViewModel: FilterViewModel = viewModel()
                    FilterScreen(
                        viewModel = filterViewModel,
                        onStationClick = onStationClick,
                        onFavoriteClick = { station ->
                            if (app.favouriteManager.has(station.StationUuid)) {
                                app.favouriteManager.remove(station.StationUuid)
                            } else {
                                app.favouriteManager.add(station)
                            }
                        },
                        isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                    )
                }
                IDX_LOCAL, IDX_TOP_CLICK, IDX_CURRENTLY_HEARD, IDX_SEARCH -> {
                    val stationsViewModel: StationsViewModel = viewModel(key = "tab_${tab.id}")
                    val url = if (tab.id == IDX_LOCAL && countryCode != null) {
                        "json/stations/bycountrycodeexact/$countryCode?order=clickcount&reverse=true"
                    } else addresses[tab.id]
                    
                    StationsScreen(
                        viewModel = stationsViewModel,
                        url = url,
                        onStationClick = onStationClick,
                        onFavoriteClick = { station ->
                            if (app.favouriteManager.has(station.StationUuid)) {
                                app.favouriteManager.remove(station.StationUuid)
                            } else {
                                app.favouriteManager.add(station)
                            }
                        },
                        isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                    )
                }
                IDX_COUNTRIES -> {
                    val categoriesViewModel: CategoriesViewModel = viewModel(key = "tab_${tab.id}")
                    CategoriesScreen(
                        viewModel = categoriesViewModel,
                        url = addresses[tab.id],
                        searchStyle = SearchStyle.ByCountryCodeExact,
                        singleUseFilter = false,
                        onCategoryClick = { category ->
                            onCategoryClick(category.Name)
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
