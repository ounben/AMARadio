package com.ounben.amaradio

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.interfaces.IFragmentRefreshable
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.station.StationsFilter
import com.ounben.amaradio.ui.*
import kotlinx.coroutines.launch

class FragmentTabs : Fragment(), IFragmentRefreshable, IFragmentSearchable {
    private val itsAdressWWWLocal = "json/stations/bycountryexact/internet?order=clickcount&reverse=true"
    private val itsAdressWWWTopClick = "json/stations/topclick/100"
    private val itsAdressWWWTopVote = "json/stations/topvote/100"
    private val itsAdressWWWChangedLately = "json/stations/lastchange/100"
    private val itsAdressWWWCurrentlyHeard = "json/stations/lastclick/100"
    private val itsAdressWWWTags = "json/tags"
    private val itsAdressWWWCountries = "json/countrycodes"
    private val itsAdressWWWLanguages = "json/languages"

    private var queuedSearchQuery: String? = null
    private var queuedSearchStyle: StationsFilter.SearchStyle? = null

    private val addresses = arrayOf(
        itsAdressWWWLocal,
        itsAdressWWWTopClick,
        itsAdressWWWTopVote,
        itsAdressWWWChangedLately,
        itsAdressWWWCurrentlyHeard,
        itsAdressWWWTags,
        itsAdressWWWCountries,
        itsAdressWWWLanguages,
        "",
        "",
        ""
    )

    private data class TabData(val id: Int, val titleRes: Int)

    private var pagerStateRef: PagerState? = null
    private var activeTabsList by mutableStateOf<List<TabData>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (savedInstanceState != null) {
            val styleIdx = savedInstanceState.getInt("queuedSearchStyle", -1)
            if (styleIdx != -1) {
                queuedSearchStyle = StationsFilter.SearchStyle.entries[styleIdx]
            }
            queuedSearchQuery = savedInstanceState.getString("queuedSearchQuery")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pagerStateRef?.let { outState.putInt("activeTabPosition", it.currentPage) }
        queuedSearchStyle?.let { outState.putInt("queuedSearchStyle", it.ordinal) }
        outState.putString("queuedSearchQuery", queuedSearchQuery)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val initialTab = savedInstanceState?.getInt("activeTabPosition", 0) ?: 0
        
        return ComposeView(requireContext()).apply {
            setContent {
                AMARadioTheme {
                    TabsScreen(initialTab)
                }
            }
        }
    }

    @Composable
    private fun TabsScreen(initialTab: Int) {
        val countryCode = remember { getCountryCode() }
        val activeTabs = remember(countryCode) {
            mutableListOf<TabData>().apply {
                if (countryCode != null) add(TabData(IDX_LOCAL, R.string.action_local))
                add(TabData(IDX_FILTER, R.string.action_filter))
                add(TabData(IDX_TOP_CLICK, R.string.action_top_click))
                add(TabData(IDX_CURRENTLY_HEARD, R.string.action_currently_playing))
                add(TabData(IDX_COUNTRIES, R.string.action_countries))
                add(TabData(IDX_SEARCH, R.string.action_search))
            }
        }.also { activeTabsList = it }

        val pagerState = rememberPagerState(
            initialPage = initialTab.coerceIn(0, activeTabs.size - 1),
            pageCount = { activeTabs.size }
        )
        val coroutineScope = rememberCoroutineScope()
        
        pagerStateRef = pagerState

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
                }
            ) {
                activeTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(text = androidx.compose.ui.res.stringResource(id = tab.titleRes)) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val tab = activeTabs[pageIndex]
                TabContent(tab = tab)
            }
        }

        LaunchedEffect(queuedSearchQuery) {
            queuedSearchQuery?.let { query ->
                val searchIndex = activeTabs.indexOfFirst { it.id == IDX_SEARCH }
                if (searchIndex != -1) {
                    if (pagerState.currentPage != searchIndex) {
                        pagerState.scrollToPage(searchIndex)
                    }
                    search(queuedSearchStyle ?: StationsFilter.SearchStyle.ByName, query)
                    queuedSearchQuery = null
                }
            }
        }
    }

    @Composable
    private fun TabContent(tab: TabData) {
        val app = requireActivity().application as AMARadioApp
        val context = requireContext()
        val countryCode = remember { getCountryCode() }
        
        when (tab.id) {
            IDX_FILTER -> {
                val filterViewModel: FilterViewModel = viewModel(viewModelStoreOwner = this)
                FilterScreen(
                    viewModel = filterViewModel,
                    onStationClick = { station -> Utils.showPlaySelection(app, station, childFragmentManager) },
                    onFavoriteClick = { station ->
                        if (app.favouriteManager.has(station.StationUuid)) {
                            StationActions.removeFromFavourites(context, null, station)
                        } else {
                            StationActions.markAsFavourite(context, station)
                        }
                    },
                    isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                )
            }
            IDX_LOCAL, IDX_TOP_CLICK, IDX_CURRENTLY_HEARD, IDX_SEARCH -> {
                val stationsViewModel: StationsViewModel = viewModel(key = "tab_${tab.id}", viewModelStoreOwner = this)
                val url = if (tab.id == IDX_LOCAL && countryCode != null) {
                    "json/stations/bycountrycodeexact/$countryCode?order=clickcount&reverse=true"
                } else addresses[tab.id]
                
                StationsScreen(
                    viewModel = stationsViewModel,
                    url = url,
                    onStationClick = { station -> Utils.showPlaySelection(app, station, childFragmentManager) },
                    onFavoriteClick = { station ->
                        if (app.favouriteManager.has(station.StationUuid)) {
                            StationActions.removeFromFavourites(context, null, station)
                        } else {
                            StationActions.markAsFavourite(context, station)
                        }
                    },
                    isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                )
            }
            IDX_COUNTRIES -> {
                val categoriesViewModel: CategoriesViewModel = viewModel(key = "tab_${tab.id}", viewModelStoreOwner = this)
                CategoriesScreen(
                    viewModel = categoriesViewModel,
                    url = addresses[tab.id],
                    searchStyle = StationsFilter.SearchStyle.ByCountryCodeExact,
                    singleUseFilter = false,
                    onCategoryClick = { category ->
                        (activity as? ActivityMain)?.search(StationsFilter.SearchStyle.ByCountryCodeExact, category.Name)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh grid mode for all active viewmodels
        activeTabsList.forEach { tab ->
            when (tab.id) {
                IDX_LOCAL, IDX_TOP_CLICK, IDX_CURRENTLY_HEARD, IDX_SEARCH -> {
                    val vm: StationsViewModel = androidx.lifecycle.ViewModelProvider(this).get("tab_${tab.id}", StationsViewModel::class.java)
                    vm.refreshGridMode()
                }
                IDX_COUNTRIES -> {
                    val vm: CategoriesViewModel = androidx.lifecycle.ViewModelProvider(this).get("tab_${tab.id}", CategoriesViewModel::class.java)
                    vm.refreshGridMode()
                }
                IDX_FILTER -> {
                    val vm: FilterViewModel = androidx.lifecycle.ViewModelProvider(this).get(FilterViewModel::class.java)
                    vm.refreshGridMode()
                }
            }
        }
    }

    private fun getCountryCode(): String? {
        val ctx = context ?: return null
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return tm?.networkCountryIso?.takeIf { it.length == 2 }
            ?: tm?.simCountryIso?.takeIf { it.length == 2 }
            ?: ctx.resources.configuration.locales[0].country.takeIf { it.length == 2 }
    }

    override fun search(searchStyle: StationsFilter.SearchStyle, query: String) {
        val searchIndex = activeTabsList.indexOfFirst { it.id == IDX_SEARCH }
        if (searchIndex != -1) {
            val pagerState = pagerStateRef
            if (pagerState != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (pagerState.currentPage != searchIndex) {
                        pagerState.scrollToPage(searchIndex)
                    }
                    val searchViewModel: StationsViewModel = androidx.lifecycle.ViewModelProvider(this@FragmentTabs).get("tab_$IDX_SEARCH", StationsViewModel::class.java)
                    searchViewModel.search(searchStyle, query)
                }
            }
        } else {
            queuedSearchQuery = query
            queuedSearchStyle = searchStyle
        }
    }

    override fun refresh() {
        // Implementation for Compose version
    }

    fun openFilterTab() {
        val filterIndex = activeTabsList.indexOfFirst { it.id == IDX_FILTER }
        if (filterIndex != -1) {
            viewLifecycleOwner.lifecycleScope.launch {
                pagerStateRef?.scrollToPage(filterIndex)
            }
        }
    }

    companion object {
        private const val IDX_LOCAL = 0
        private const val IDX_TOP_CLICK = 1
        private const val IDX_CURRENTLY_HEARD = 4
        private const val IDX_COUNTRIES = 6
        private const val IDX_SEARCH = 8
        private const val IDX_FILTER = 9
    }
}
