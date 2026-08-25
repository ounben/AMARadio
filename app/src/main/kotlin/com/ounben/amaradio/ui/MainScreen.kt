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
    val serverInfoViewModel: ServerInfoViewModel = viewModel()
    
    val mainUiState by mainViewModel.uiState.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()
    val playerWarning by playerViewModel.warningMessage.collectAsState()
    
    // Observe player warnings
    LaunchedEffect(playerWarning) {
        playerWarning?.let { msgRes ->
            android.widget.Toast.makeText(context, msgRes, android.widget.Toast.LENGTH_LONG).show()
            playerViewModel.clearWarning()
        }
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showPlayerSelectorDialog by remember { mutableStateOf<DataRadioStation?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Int?>(null) } 

    var isPlayerExpanded by remember { mutableStateOf(false) }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Fix: Identify if we are in Settings sub-screens
    val isSettingsSubScreen = currentRoute == Screen.About.route || currentRoute == Screen.Statistics.route
    val isSettingsActive = currentRoute == Screen.Settings.route || isSettingsSubScreen

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
                        showDeleteConfirmDialog = if (currentRoute == Screen.Favourites.route) R.string.alert_delete_favorites else R.string.alert_delete_history 
                    },
                    onViewToggleClick = { mainViewModel.toggleGridView() },
                    isGridView = mainUiState.isGridView,
                    isDeleteVisible = currentRoute == Screen.Favourites.route || currentRoute == Screen.History.route,
                    deleteTitleRes = R.string.action_delete
                )
            },
            bottomBar = {
                if (!mainUiState.isSearching) {
                    NavigationBar(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(64.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        val items = listOf(Screen.Stations, Screen.Favourites, Screen.History, Screen.Settings)
                        
                        items.forEach { screen ->
                            val isSelected = if (screen == Screen.Settings) isSettingsActive 
                                           else currentDestination?.hierarchy?.any { it.route == screen.route } == true

                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                                selected = isSelected,
                                onClick = {
                                    if (screen == Screen.Settings && isSettingsSubScreen) {
                                        // Jump back to main settings if on sub-screen
                                        navController.popBackStack(Screen.Settings.route, false)
                                    } else {
                                        isPlayerExpanded = false
                                        if (screen == Screen.Stations) mainViewModel.setStationsInitialTab(0)
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
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
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // Fixed bottom padding to avoid measurement loops during scrolling
                val contentBottomPadding = 110.dp 
                
                if (mainUiState.isSearching) {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(bottom = if (playerUiState.currentStation != null) contentBottomPadding else 0.dp),
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
                                    else playerViewModel.play(station)
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
                        modifier = Modifier.fillMaxSize().padding(bottom = contentBottomPadding)
                    ) {
                        composable(Screen.Stations.route) {
                            TabsScreen(
                                initialTab = mainUiState.stationsInitialTab,
                                onStationClick = { station -> 
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                },
                                onCategoryClick = { }
                            )
                        }
                        composable(Screen.Favourites.route) {
                            val viewModel: LocalStationsViewModel = viewModel(key = "starred", factory = LocalStationsViewModelFactory(app, LocalManagerType.FAVOURITES))
                            StarredScreen(
                                viewModel = viewModel,
                                onStationClick = { station -> 
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                },
                                onFavoriteClick = { station -> app.favouriteManager.remove(station.StationUuid) },
                                isFavorite = { true }
                            )
                        }
                        composable(Screen.History.route) {
                            val localViewModel: LocalStationsViewModel = viewModel(key = "history", factory = LocalStationsViewModelFactory(app, LocalManagerType.HISTORY))
                            HistoryScreen(
                                localStationsViewModel = localViewModel,
                                trackHistoryViewModel = trackHistoryViewModel,
                                onStationClick = { station -> 
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                },
                                onFavoriteClick = { station -> 
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                onTrackClick = { },
                                isFavorite = { uuid -> mainUiState.favoriteIds.contains(uuid) }
                            )
                        }
                        composable(Screen.Settings.route) {
                            val viewModel: SettingsViewModel = viewModel()
                            SettingsScreen(
                                viewModel = viewModel,
                                serverInfoViewModel = serverInfoViewModel,
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
                                onRateApp = {
                                    (context as? android.app.Activity)?.let {
                                        app.reviewManager.launchReviewFlow(it)
                                    }
                                },
                                batterySummary = ""
                            )
                        }
                        composable(Screen.About.route) { AboutScreen { navController.popBackStack() } }
                        composable(Screen.Statistics.route) { 
                            ServerInfoScreen(
                                viewModel = serverInfoViewModel,
                                onStationClick = { station ->
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                },
                                onFavoriteClick = { station ->
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

                // MiniPlayer / FullPlayer Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isPlayerExpanded && playerUiState.currentStation != null) 
                                Modifier.fillMaxHeight()
                            else 
                                Modifier.wrapContentHeight()
                        )
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { /* ignore delta */ },
                            onDragStopped = { velocity ->
                                if (velocity < -500f && playerUiState.currentStation != null) isPlayerExpanded = true
                                if (velocity > 500f) isPlayerExpanded = false
                            }
                        )
                ) {
                    if (isPlayerExpanded && playerUiState.currentStation != null) {
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
                            onToggleBottomSheet = { 
                                if (playerUiState.currentStation != null) isPlayerExpanded = true 
                            },
                            onMoreClick = { }
                        )
                    }
                }
            }
        }
    }

    if (showSleepTimerDialog) SleepTimerDialog(onDismiss = { showSleepTimerDialog = false })
    if (showProxyDialog) ProxySettingsDialogCompose(onDismiss = { showProxyDialog = false })
    showPlayerSelectorDialog?.let { station -> 
        PlayerSelectorDialogCompose(
            station = station, 
            playerViewModel = playerViewModel,
            onDismiss = { showPlayerSelectorDialog = null }
        ) 
    }
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
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(stringResource(R.string.yes), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { 
                Button(
                    onClick = { showDeleteConfirmDialog = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(stringResource(R.string.no), fontWeight = FontWeight.Bold) }
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
