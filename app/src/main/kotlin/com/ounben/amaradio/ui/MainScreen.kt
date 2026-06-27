package com.ounben.amaradio.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.history.TrackHistoryViewModel

sealed class Screen(val route: String, val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Stations : Screen("stations", R.string.nav_item_stations, Icons.Default.Radio)
    object Favourites : Screen("starred", R.string.nav_item_starred, Icons.Default.Star)
    object History : Screen("history", R.string.nav_item_history, Icons.Default.History)
    object Settings : Screen("settings", R.string.nav_item_settings, Icons.Default.Settings)
    object About : Screen("about", R.string.settings_about, Icons.Default.Info)
    object Statistics : Screen("statistics", R.string.settings_statistics, Icons.Default.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSaveM3U: () -> Unit,
    onLoadM3U: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as AMARadioApp
    
    val mainViewModel: MainViewModel = viewModel()
    val playerViewModel: PlayerViewModel = viewModel()
    val trackHistoryViewModel: TrackHistoryViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()
    
    val mainUiState by mainViewModel.uiState.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()
    
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showPlayerSelectorDialog by remember { mutableStateOf<DataRadioStation?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Int?>(null) } 

    var isPlayerExpanded by remember { mutableStateOf(false) }
    
    val density = androidx.compose.ui.platform.LocalDensity.current
    val miniPlayerHeight = remember(density.fontScale) {
        if (density.fontScale > 1.2f) 140.dp else 104.dp
    }

    BackHandler(enabled = isPlayerExpanded) { isPlayerExpanded = false }

    BackHandler(enabled = mainUiState.isSearching) {
        mainViewModel.setSearchActive(false)
        searchViewModel.clearResults()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            topBar = {
                MainTopBar(
                    isSearching = mainUiState.isSearching,
                    searchQuery = mainUiState.searchQuery,
                    isLoading = mainUiState.isLoading || searchUiState.isSearching,
                    onSearchQueryChange = { query -> 
                        mainViewModel.setSearchQuery(query)
                        searchViewModel.search(query)
                    },
                    onSearchToggle = { active -> 
                        if (active) isPlayerExpanded = false
                        mainViewModel.setSearchActive(active)
                        if (!active) searchViewModel.clearResults()
                    },
                    onFilterClick = { 
                        isPlayerExpanded = false
                        mainViewModel.setStationsInitialTab(1)
                        navController.navigate(Screen.Stations.route)
                    },
                    onSleepTimerClick = { showSleepTimerDialog = true },
                    onSaveClick = onSaveM3U,
                    onLoadClick = onLoadM3U,
                    onDeleteClick = { 
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        showDeleteConfirmDialog = if (currentRoute == Screen.Favourites.route) R.string.alert_delete_favorites else R.string.alert_delete_history 
                    },
                    onViewToggleClick = { mainViewModel.toggleGridView() },
                    isGridView = mainUiState.isGridView,
                    isDeleteVisible = true,
                    deleteTitleRes = R.string.action_delete
                )
            },
            bottomBar = {
                if (!mainUiState.isSearching) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp
                    ) {
                        val items = listOf(Screen.Stations, Screen.Favourites, Screen.History, Screen.Settings)
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    isPlayerExpanded = false
                                    if (screen == Screen.Stations) mainViewModel.setStationsInitialTab(0)
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AmaradioAmber,
                                    selectedTextColor = AmaradioAmber,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val availableHeight = this.maxHeight
                
                if (mainUiState.isSearching) {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(bottom = if (playerUiState.currentStation != null) miniPlayerHeight else 0.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (searchUiState.isSearching && searchUiState.results.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AmaradioAmber)
                            }
                        } else if (searchUiState.results.isEmpty() && mainUiState.searchQuery.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                                Text(stringResource(R.string.searchpreference_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            StationListTemplate(
                                stations = searchUiState.results,
                                isGrid = mainUiState.isGridView,
                                isLoading = searchUiState.isSearching,
                                error = searchUiState.error,
                                emptyMessage = stringResource(R.string.searchpreference_no_results),
                                onStationClick = { station ->
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else PlayerServiceUtil.play(station)
                                    mainViewModel.setSearchActive(false)
                                    searchViewModel.clearResults()
                                },
                                onFavoriteClick = { station ->
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                isFavorite = { uuid -> searchUiState.favoriteIds.contains(uuid) }
                            )
                        }
                    }
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Stations.route,
                        modifier = Modifier.fillMaxSize().padding(bottom = miniPlayerHeight)
                    ) {
                        composable(Screen.Stations.route) {
                            TabsScreen(
                                initialTab = mainUiState.stationsInitialTab,
                                onStationClick = { station -> 
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else PlayerServiceUtil.play(station)
                                },
                                onCategoryClick = { }
                            )
                        }
                        composable(Screen.Favourites.route) {
                            val viewModel: LocalStationsViewModel = viewModel(key = "starred", factory = LocalStationsViewModelFactory(app, false))
                            StarredScreen(
                                viewModel = viewModel,
                                onStationClick = { station -> 
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else PlayerServiceUtil.play(station)
                                },
                                onFavoriteClick = { station -> app.favouriteManager.remove(station.StationUuid) },
                                isFavorite = { true }
                            )
                        }
                        composable(Screen.History.route) {
                            val localViewModel: LocalStationsViewModel = viewModel(key = "history", factory = LocalStationsViewModelFactory(app, true))
                            HistoryScreen(
                                localStationsViewModel = localViewModel,
                                trackHistoryViewModel = trackHistoryViewModel,
                                onStationClick = { station -> 
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else PlayerServiceUtil.play(station)
                                },
                                onFavoriteClick = { station -> 
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                onTrackClick = { },
                                isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                            )
                        }
                        composable(Screen.Settings.route) {
                            val viewModel: SettingsViewModel = viewModel()
                            SettingsScreen(
                                viewModel = viewModel,
                                onOpenProxy = { showProxyDialog = true },
                                onOpenAbout = { 
                                    isPlayerExpanded = false
                                    navController.navigate(Screen.About.route) 
                                },
                                onOpenStatistics = { 
                                    isPlayerExpanded = false
                                    navController.navigate(Screen.Statistics.route) 
                                },
                                onOpenEqualizer = { 
                                    val intent = Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL")
                                    intent.putExtra("android.media.extra.PACKAGE_NAME", context.packageName)
                                    intent.putExtra("android.media.extra.AUDIO_SESSION", 0)
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                },
                                onBatteryOptimize = {
                                    val intent = Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                },
                                batterySummary = ""
                            )
                        }
                        composable(Screen.About.route) { AboutScreen() }
                        composable(Screen.Statistics.route) { 
                            val viewModel: ServerInfoViewModel = viewModel()
                            ServerInfoScreen(viewModel = viewModel)
                        }
                    }
                }

                if (playerUiState.currentStation != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isPlayerExpanded) availableHeight else miniPlayerHeight)
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { /* ignore delta */ },
                                onDragStopped = { velocity ->
                                    if (velocity < -500f) isPlayerExpanded = true
                                    if (velocity > 500f) isPlayerExpanded = false
                                }
                            )
                    ) {
                        if (isPlayerExpanded) {
                            Column {
                                Box(modifier = Modifier.fillMaxWidth().height(32.dp).clickable { isPlayerExpanded = false }, contentAlignment = Alignment.Center) {
                                    Box(modifier = Modifier.size(40.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)))
                                }
                                FullPlayer(
                                    playerViewModel = playerViewModel,
                                    trackHistoryViewModel = trackHistoryViewModel,
                                    onTrackClick = { }
                                )
                            }
                        } else {
                            MiniPlayer(
                                viewModel = playerViewModel,
                                isHeaderRole = false,
                                onToggleBottomSheet = { isPlayerExpanded = true },
                                onMoreClick = { }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSleepTimerDialog) SleepTimerDialog(onDismiss = { showSleepTimerDialog = false })
    if (showProxyDialog) ProxySettingsDialogCompose(onDismiss = { showProxyDialog = false })
    showPlayerSelectorDialog?.let { station -> PlayerSelectorDialogCompose(station = station, onDismiss = { showPlayerSelectorDialog = null }) }
    showDeleteConfirmDialog?.let { msgRes ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.action_delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(msgRes), color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(
                    onClick = {
                        if (msgRes == R.string.alert_delete_favorites) app.favouriteManager.clear()
                        else app.historyManager.clear()
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB71C1C), 
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { 
                TextButton(
                    onClick = { showDeleteConfirmDialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text(stringResource(R.string.no)) } 
            }
        )
    }
}

private fun sharedPrefHasExternalPlayer(context: Context): Boolean {
    return try {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("play_external", false)
    } catch (_: Exception) {
        false
    }
}
