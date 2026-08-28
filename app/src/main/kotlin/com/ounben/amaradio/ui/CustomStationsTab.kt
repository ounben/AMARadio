package com.ounben.amaradio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.players.selector.PlayerType
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.utils.StationIconProvider

@Composable
fun CustomStationsTab(
    viewModel: CustomStationsViewModel,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var stationToEdit by remember { mutableStateOf<DataRadioStation?>(null) }
    var stationToDelete by remember { mutableStateOf<DataRadioStation?>(null) }
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.filteredStations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.searchpreference_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (uiState.isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                itemsIndexed(uiState.filteredStations, key = { _, s -> s.StationUuid }) { _, station ->
                    StationGridItem(
                        station = station,
                        isFavorite = isFavorite(station.StationUuid),
                        onClick = { onStationClick(station) },
                        onFavoriteClick = { onFavoriteClick(station) },
                        onLongClick = { stationWithOptions = station }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(uiState.filteredStations, key = { _, s -> s.StationUuid }) { _, station ->
                    StationListItem(
                        station = station,
                        isFavorite = isFavorite(station.StationUuid),
                        onClick = { onStationClick(station) },
                        onFavoriteClick = { onFavoriteClick(station) },
                        onLongClick = { stationWithOptions = station }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_custom))
        }
    }

    if (showAddDialog) {
        AddEditCustomStationDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, iconUri ->
                viewModel.addCustomStation(name, url, iconUri)
                showAddDialog = false
            }
        )
    }

    stationToEdit?.let { station ->
        AddEditCustomStationDialog(
            station = station,
            onDismiss = { stationToEdit = null },
            onConfirm = { name, url, iconUri ->
                val updated = station.copy(Name = name, StreamUrl = url)
                viewModel.updateCustomStation(updated, iconUri)
                stationToEdit = null
            }
        )
    }

    stationToDelete?.let { station ->
        AlertDialog(
            onDismissRequest = { stationToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(station.Name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(station.StationUuid)
                    stationToDelete = null
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { stationToDelete = null }) { Text(stringResource(R.string.no)) }
            }
        )
    }

    stationWithOptions?.let { station ->
        val index = uiState.filteredStations.indexOf(station)
        CustomStationOptionsDialog(
            station = station,
            onDismiss = { stationWithOptions = null },
            onEdit = { 
                stationToEdit = station
                stationWithOptions = null
            },
            onDelete = {
                stationToDelete = station
                stationWithOptions = null
            },
            onMoveUp = if (index > 0) { { viewModel.reorder(index, index - 1) } } else null,
            onMoveDown = if (index < uiState.filteredStations.size - 1) { { viewModel.reorder(index, index + 1) } } else null
        )
    }
}

@Composable
fun CustomStationOptionsDialog(
    station: DataRadioStation,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(station.Name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.detail_play), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = AmaradioAmber) },
                    modifier = Modifier.clickable {
                        StationActions.playInAMARadio(context, station)
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_play_in_external), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        Utils.playAndWarnIfMetered(context, station, PlayerType.EXTERNAL) {
                            PlayStationTask.playExternal(station, context).execute()
                        }
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_edit), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onEdit() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (onMoveUp != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.description_btn_skip_to_previous), color = MaterialTheme.colorScheme.onSurface) },
                        leadingContent = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { 
                            onMoveUp()
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                if (onMoveDown != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.description_btn_skip_to_next), color = MaterialTheme.colorScheme.onSurface) },
                        leadingContent = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { 
                            onMoveDown()
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_station_visit_website), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        StationActions.openStationHomeUrl(context, station)
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_station_share), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        StationActions.share(context, station)
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onDelete() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun AddEditCustomStationDialog(
    station: DataRadioStation? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Uri?) -> Unit
) {
    var name by remember { mutableStateOf(station?.Name ?: "") }
    var url by remember { mutableStateOf(station?.StreamUrl ?: "") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        iconUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(if (station == null) stringResource(R.string.action_add_custom) else stringResource(R.string.action_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.station_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.stream_url)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    val model = iconUri ?: station?.IconUrl?.ifEmpty { null }
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                    
                    Button(onClick = { photoPickerLauncher.launch("image/*") }) {
                        Text(stringResource(R.string.select_image))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onConfirm(name, url, iconUri) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
