package com.ounben.amaradio.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.ounben.amaradio.Utils
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.recorder.RecordManager
import com.ounben.amaradio.recorder.RecordingStatusBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    onLoadM3U: () -> Unit,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as AMARadioApp
    
    val mainViewModel: MainViewModel = viewModel()
    val playerViewModel: PlayerViewModel = viewModel()
    val trackHistoryViewModel: TrackHistoryViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()
    val serverInfoViewModel: ServerInfoViewModel = viewModel()
    
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val playerWarning by playerViewModel.warningMessage.collectAsStateWithLifecycle()
    
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
    val scrollToTopController = remember { ScrollToTopController() }
    val coroutineScope = rememberCoroutineScope()
    
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // 1. Determine Initial Route (Startup Action)
    val startRoute = remember(settingsUiState.startupAction) {
        val action = settingsUiState.startupAction
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val lastRoute = sharedPref.getString("last_view_route", Screen.Stations.route) ?: Screen.Stations.route
        
        when (action) {
            "history" -> Screen.History.route
            "favorites" -> Screen.Favourites.route
            "stations" -> Screen.Stations.route
            "last" -> lastRoute
            else -> Screen.Stations.route
        }
    }

    // 2. Save Last View Route
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && 
            currentRoute != Screen.About.route && 
            currentRoute != Screen.Statistics.route && 
            currentRoute != Screen.Settings.route) {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            sharedPref.edit {
                putString("last_view_route", currentRoute)
            }
        }
    }

    // Fix: Identify if we are in Settings sub-screens
    val isSettingsSubScreen = currentRoute == Screen.About.route || currentRoute == Screen.Statistics.route
    val isSettingsActive = currentRoute == Screen.Settings.route || isSettingsSubScreen

    // Shared by the bottom navigation bar (portrait) and the navigation rail (landscape)
    val navItems = listOf(Screen.Stations, Screen.Favourites, Screen.History, Screen.Settings)

    fun isNavItemSelected(screen: Screen): Boolean =
        if (screen == Screen.Settings) isSettingsActive
        else currentDestination?.hierarchy?.any { it.route == screen.route } == true

    fun onNavItemClick(screen: Screen) {
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
    }

    BackHandler(enabled = isPlayerExpanded) { isPlayerExpanded = false }

    BackHandler(enabled = mainUiState.isSearching) {
        mainViewModel.setSearchActive(false)
        searchViewModel.clearResults()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Landscape screens (car tablets, tablets, phones held sideways) use a wide layout:
        // navigation rail on the left and a permanent player column on the right, instead of
        // bottom bar + mini player, which together take almost half of a landscape screen's height.
        // maxWidth/maxHeight already include the app's own UI scale (see Theme.kt).
        val useWideLayout = maxWidth > maxHeight && maxWidth >= 560.dp
        val sidePanelWidth = (maxWidth * 0.36f).coerceIn(300.dp, 440.dp)
        val showSidePlayer = useWideLayout && playerUiState.currentStation != null

        // The expandable full-screen player is not used in the wide layout
        LaunchedEffect(useWideLayout) {
            if (useWideLayout) isPlayerExpanded = false
        }

        CompositionLocalProvider(LocalScrollToTopController provides scrollToTopController) {
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
                        if (active) {
                            isPlayerExpanded = false
                            if (mainUiState.searchQuery.isNotEmpty()) {
                                searchViewModel.search(mainUiState.searchQuery)
                            }
                        }
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
                    sleepTimerRemainingMinutes = playerUiState.sleepTimerRemainingMinutes,
                    isDeleteVisible = currentRoute == Screen.Favourites.route || currentRoute == Screen.History.route,
                    deleteTitleRes = R.string.action_delete,
                    currentStation = playerUiState.currentStation,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup
                )
            },
            bottomBar = {
                if (!useWideLayout && !mainUiState.isSearching) {
                    NavigationBar(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(64.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        navItems.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                                selected = isNavItemSelected(screen),
                                onClick = { onNavItemClick(screen) },
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
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useWideLayout) {
                    // Landscape: vertical navigation rail with icons only (no text labels)
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        navItems.forEach { screen ->
                            NavigationRailItem(
                                icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                                selected = isNavItemSelected(screen),
                                onClick = {
                                    // The rail stays visible while searching: leave search before navigating
                                    if (mainUiState.isSearching) {
                                        mainViewModel.setSearchActive(false)
                                        searchViewModel.clearResults()
                                    }
                                    onNavItemClick(screen)
                                },
                                alwaysShowLabel = false,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AmaradioAmber,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val showRecorder = remember(context) {
                        try {
                            context.resources.getBoolean(R.bool.show_recorder_button)
                        } catch (_: Exception) {
                            false
                        }
                    }
                    val isRecording by remember(showRecorder) {
                        if (showRecorder) {
                            RecordManager.getInstance(context)
                                .recordingState
                                .map { it.isRecording }
                                .distinctUntilChanged()
                        } else {
                            flowOf(false)
                        }
                    }.collectAsStateWithLifecycle(initialValue = false)

                    // Fixed bottom padding to avoid measurement loops during scrolling.
                    // In the wide layout the player has its own column, so nothing overlaps the content.
                    val contentBottomPadding = if (useWideLayout) 0.dp else if (isRecording) 158.dp else 110.dp
                    val contentTopPadding = if (useWideLayout && !showSidePlayer && isRecording) 48.dp else 0.dp
                    val density = LocalDensity.current
                    var miniPlayerHeightPx by remember { mutableIntStateOf(0) }
                    val miniPlayerHeightDp = with(density) { miniPlayerHeightPx.toDp() }
                
                    if (mainUiState.isSearching) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = contentTopPadding,
                                    bottom = if (playerUiState.currentStation != null || isRecording) contentBottomPadding else 0.dp
                                ),
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
                            startDestination = startRoute,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = contentTopPadding, bottom = contentBottomPadding)
                                .then(
                                    if (isPlayerExpanded && playerUiState.currentStation != null)
                                        Modifier.clearAndSetSemantics { }
                                    else
                                        Modifier
                                )
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
                                    onFavoriteClick = { station -> 
                                        if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                        else app.favouriteManager.add(station)
                                    },
                                    isFavorite = { uuid -> mainUiState.favoriteIds.contains(uuid) }
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
                                        Utils.openBatteryOrAppSettings(context)
                                    },
                                    onRateApp = {
                                        (context as? android.app.Activity)?.let {
                                            app.reviewManager.launchReviewFlow(it)
                                        }
                                    },
                                    onExportBackup = onExportBackup,
                                    onImportBackup = onImportBackup
                                )
                            }
                            composable(Screen.About.route) { AboutScreen { navController.popBackStack() } }
                            composable(Screen.Statistics.route) { 
                                ServerInfoScreen(
                                    viewModel = serverInfoViewModel,
                                    currentPlayingUuid = playerUiState.currentStation?.StationUuid,
                                    isPlaying = playerUiState.playState == PlayState.Playing,
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

                    // Permanently visible bottom-right Scroll-to-Top button positioned above the MiniPlayer
                    val baseBottomPadding = if (useWideLayout) {
                        16.dp
                    } else if (miniPlayerHeightDp > 0.dp) {
                        miniPlayerHeightDp + 12.dp
                    } else {
                        contentBottomPadding + 12.dp
                    }
                    ScrollToTopButton(
                        onClick = {
                            coroutineScope.launch {
                                scrollToTopController.scrollToTop()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 16.dp,
                                bottom = baseBottomPadding + scrollToTopController.extraBottomOffset,
                            )
                            .then(
                                if (isPlayerExpanded && playerUiState.currentStation != null)
                                    Modifier.clearAndSetSemantics { }
                                else
                                    Modifier
                            )
                    )

                    // MiniPlayer / FullPlayer Container (portrait and narrow screens only)
                    if (!useWideLayout) Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isPlayerExpanded && playerUiState.currentStation != null) 
                                    Modifier
                                        .fillMaxHeight()
                                        .pointerInput(Unit) { detectTapGestures { } }
                                        .semantics { isTraversalGroup = true }
                                else 
                                    Modifier
                                        .wrapContentHeight()
                                        .onSizeChanged { miniPlayerHeightPx = it.height }
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
                            val minimizeDesc = stringResource(R.string.accessibility_minimize_player)
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    // 1. Left-aligned Minimize IconButton (48dp x 48dp) for TalkBack
                                    IconButton(
                                        onClick = { isPlayerExpanded = false },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .align(Alignment.CenterStart)
                                            .clearAndSetSemantics {
                                                contentDescription = minimizeDesc
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // 2. Centered Drag Handle Pill (decorative for TalkBack)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(40.dp, 4.dp)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
                                            .clearAndSetSemantics {}
                                    )
                                }
                                RecordingStatusBar()
                                FullPlayer(
                                    playerViewModel = playerViewModel,
                                    trackHistoryViewModel = trackHistoryViewModel,
                                    onTrackClick = { }
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                RecordingStatusBar()
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

                    if (useWideLayout && !showSidePlayer) {
                        RecordingStatusBar(
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }

                // Wide layout: the player is always visible in its own column on the right
                if (showSidePlayer) {
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    Column(modifier = Modifier.width(sidePanelWidth).fillMaxHeight()) {
                        RecordingStatusBar()
                        SidePanelPlayer(
                            playerViewModel = playerViewModel,
                            trackHistoryViewModel = trackHistoryViewModel,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
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
        val isDark = isSystemInDarkTheme()
        val deleteRedColor = if (isDark) SolidRedError else SolidRedErrorLight

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
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
                    text = stringResource(msgRes),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (msgRes == R.string.alert_delete_favorites) {
                            app.favouriteManager.clear()
                            Utils.showToast(context, R.string.notify_deleted_favorites)
                        } else {
                            app.historyManager.clear()
                            app.trackHistoryRepository.deleteHistory()
                            Utils.showToast(context, R.string.notify_deleted_history)
                        }
                        showDeleteConfirmDialog = null
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
                    onClick = { showDeleteConfirmDialog = null },
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
}

private fun sharedPrefHasExternalPlayer(context: Context): Boolean {
    return try {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("play_external", false)
    } catch (_: Exception) {
        false
    }
}
