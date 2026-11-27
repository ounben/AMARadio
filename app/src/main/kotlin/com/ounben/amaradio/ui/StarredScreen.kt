package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.InMemoryStationQueue
import com.ounben.amaradio.R
import com.ounben.amaradio.database.user.FavoriteListEntity
import com.ounben.amaradio.favorites.CreateEditFavoriteListDialog
import com.ounben.amaradio.favorites.DeleteFavoriteListConfirmDialog
import com.ounben.amaradio.favorites.FavoritePlaylistFilterBar
import com.ounben.amaradio.favorites.ManageFavoriteListsDialog
import com.ounben.amaradio.favorites.ManagePlaylistStationsDialog
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    var showCreateListDialog by remember { mutableStateOf(false) }
    var showManageListsDialog by remember { mutableStateOf(false) }
    var listToRename by remember { mutableStateOf<FavoriteListEntity?>(null) }
    var listToDelete by remember { mutableStateOf<FavoriteListEntity?>(null) }
    var listToEditStations by remember { mutableStateOf<FavoriteListEntity?>(null) }

    val favUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activePlaylist = remember(favUiState.favoriteLists, favUiState.selectedListId) {
        favUiState.favoriteLists.find { it.id == favUiState.selectedListId }
    }

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
                if (safeSelectedIndex in 0..1) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(safeSelectedIndex, matchContentSize = false),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
            CompositionLocalProvider(LocalScrollToTopActive provides (pagerState.currentPage == pageIndex)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (pageIndex == 0) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (favUiState.stations.isNotEmpty() || favUiState.favoriteLists.isNotEmpty()) {
                                FavoritePlaylistFilterBar(
                                    lists = favUiState.favoriteLists,
                                    selectedListId = favUiState.selectedListId,
                                    totalFavoritesCount = favUiState.stations.size,
                                    listItemCounts = favUiState.listItemCounts,
                                    onSelectList = { viewModel.selectFavoriteList(it) },
                                    onCreateListClick = { showCreateListDialog = true },
                                    onManageListsClick = { showManageListsDialog = true }
                                )
                            }

                            if (favUiState.filteredStations.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (activePlaylist != null && favUiState.query.isEmpty()) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.favorite_list_empty),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                            if (favUiState.stations.isNotEmpty()) {
                                                Button(
                                                    onClick = { listToEditStations = activePlaylist },
                                                    modifier = Modifier.heightIn(min = 48.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = AmaradioAmber,
                                                        contentColor = Color.Black
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = stringResource(R.string.favorite_list_add_stations),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = stringResource(R.string.searchpreference_no_results),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                val playlistQueue = remember(favUiState.selectedListId, favUiState.filteredStations) {
                                    if (favUiState.selectedListId != null) {
                                        InMemoryStationQueue(context, favUiState.filteredStations)
                                    } else {
                                        null
                                    }
                                }
                                key(favUiState.selectedListId) {
                                    ReorderableStationList(
                                        stations = favUiState.filteredStations,
                                        isGrid = favUiState.isGrid,
                                        onStationClick = { station ->
                                            station.queue = playlistQueue
                                                ?: (context.applicationContext as? AMARadioApp)?.favouriteManager
                                            onStationClick(station)
                                        },
                                        onFavoriteClick = onFavoriteClick,
                                        isFavorite = isFavorite,
                                        onReorder = { viewModel.updateAllOrder(it) },
                                        activePlaylistName = activePlaylist?.name,
                                        onRemoveFromPlaylistClick = if (activePlaylist != null) {
                                            { station -> viewModel.removeStationFromSelectedList(station.StationUuid) }
                                        } else {
                                            null
                                        },
                                        showFavoriteIcon = false
                                    )
                                }
                            }
                        }
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

    if (showCreateListDialog) {
        CreateEditFavoriteListDialog(
            onConfirm = { name ->
                viewModel.createFavoriteList(name, selectAfterCreate = true)
                showCreateListDialog = false
            },
            onDismiss = { showCreateListDialog = false }
        )
    }

    if (showManageListsDialog) {
        ManageFavoriteListsDialog(
            lists = favUiState.favoriteLists,
            listItemCounts = favUiState.listItemCounts,
            onMoveUp = { viewModel.moveFavoriteList(it, -1) },
            onMoveDown = { viewModel.moveFavoriteList(it, 1) },
            onRename = { listToRename = it },
            onEditStations = { listToEditStations = it },
            onDelete = { listToDelete = it },
            onCreateNew = {
                showManageListsDialog = false
                showCreateListDialog = true
            },
            onDismiss = { showManageListsDialog = false }
        )
    }

    listToRename?.let { list ->
        CreateEditFavoriteListDialog(
            initialName = list.name,
            isRename = true,
            onConfirm = { newName ->
                viewModel.renameFavoriteList(list.id, newName)
                listToRename = null
            },
            onDismiss = { listToRename = null }
        )
    }

    listToDelete?.let { list ->
        DeleteFavoriteListConfirmDialog(
            list = list,
            onConfirm = {
                viewModel.deleteFavoriteList(list.id)
                listToDelete = null
            },
            onDismiss = { listToDelete = null }
        )
    }

    listToEditStations?.let { list ->
        val initialSelected = remember(list.id) { viewModel.getStationUuidsForList(list.id) }
        ManagePlaylistStationsDialog(
            list = list,
            allFavorites = favUiState.stations,
            initialSelectedUuids = initialSelected,
            onSave = { selectedUuids ->
                viewModel.setStationsForList(list.id, selectedUuids)
            },
            onDismiss = { listToEditStations = null }
        )
    }
}
