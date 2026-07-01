package com.ounben.amaradio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import com.ounben.amaradio.R
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.EmojiUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationListTemplate(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    isLoading: Boolean = false,
    error: String? = null,
    emptyMessage: String = "No stations found",
    onRetry: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { onRefresh?.invoke() },
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading && stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmaradioAmber)
            }
        } else if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = error, color = MaterialTheme.colorScheme.error)
                if (onRetry != null) {
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Retry")
                    }
                }
            }
        } else if (stations.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            StationList(
                stations = stations,
                isGrid = isGrid,
                onStationClick = onStationClick,
                onFavoriteClick = onFavoriteClick,
                isFavorite = isFavorite
            )
        }
    }
}

@Composable
fun StationList(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(stations, key = { it.StationUuid }) { station ->
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
        LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            items(stations, key = { it.StationUuid }) { station ->
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

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = isFavorite(station.StationUuid),
            onFavoriteClick = { onFavoriteClick(station) },
            onDismiss = { stationWithOptions = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackList(
    tracks: LazyPagingItems<TrackHistoryEntry>,
    onTrackClick: (TrackHistoryEntry) -> Unit,
    onTrackLongClick: (TrackHistoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRefreshing = tracks.loadState.refresh is androidx.paging.LoadState.Loading
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { tracks.refresh() },
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks.itemCount) { index ->
                tracks[index]?.let { track ->
                    TrackListItem(
                        track = track, 
                        onClick = { onTrackClick(track) },
                        onLongClick = { onTrackLongClick(track) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: TrackHistoryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = track.stationIconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(R.drawable.ic_radio_24dp),
                placeholder = painterResource(R.drawable.ic_radio_24dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Desired format: Line 1 = Title, Line 2 = Artist
            val displayTitle = track.track.ifBlank { track.title }
            val displayArtist = if (track.track.isNotBlank()) track.artist else ""

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (displayArtist.isNotBlank() && displayArtist != displayTitle) {
                Text(
                    text = displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = track.getFormattedTime(context),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationListItem(
    station: DataRadioStation,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val flagEmoji = remember(station.CountryCode) { EmojiUtils.getFlagEmoji(station.CountryCode) ?: "" }
    val details = remember(station.ClickCount, station.Votes, station.Language, station.Bitrate, station.Codec) { 
        station.getShortDetails(context) 
    }

    val accessibilityDesc = stringResource(
        R.string.accessibility_station_description,
        station.Name,
        station.Language.ifEmpty { "N/A" },
        station.TagsAll.ifEmpty { "N/A" }
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDesc
            }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = station.IconUrl,
                contentDescription = stringResource(R.string.accessibility_station_logo),
                modifier = Modifier.fillMaxSize(),
                error = painterResource(R.drawable.ic_radio_24dp),
                placeholder = painterResource(R.drawable.ic_radio_24dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.Name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (flagEmoji.isNotEmpty()) "$flagEmoji $details" else details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (station.TagsAll.isNotEmpty()) {
                Text(
                    text = station.TagsAll,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(48.dp)
                .combinedClickable(
                    onClick = { 
                        if (!isFavorite) onFavoriteClick() 
                        // Misclick protection: Removal only via long-click context menu
                    },
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = stringResource(
                    if (isFavorite) R.string.accessibility_favorite_selected 
                    else R.string.accessibility_favorite_not_selected
                ),
                tint = if (isFavorite) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationGridItem(
    station: DataRadioStation,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val accessibilityDesc = stringResource(
        R.string.accessibility_station_description,
        station.Name,
        station.Language.ifEmpty { "N/A" },
        station.TagsAll.ifEmpty { "N/A" }
    )

    Card(
        modifier = Modifier
            .padding(4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDesc
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = station.IconUrl,
                    contentDescription = stringResource(R.string.accessibility_station_logo),
                    modifier = Modifier
                        .size(80.dp)
                        .padding(4.dp),
                    contentScale = ContentScale.Fit,
                    error = painterResource(R.drawable.ic_radio_24dp),
                    placeholder = painterResource(R.drawable.ic_radio_24dp)
                )
                Text(
                    text = station.Name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                )
            }

            // Favorite Star - Positioned at top-right of the gray background rectangle
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .combinedClickable(
                        onClick = {
                            if (!isFavorite) onFavoriteClick()
                            // Misclick protection: Removal only via long-click context menu
                        },
                        onLongClick = onLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.accessibility_favorite_selected 
                        else R.string.accessibility_favorite_not_selected
                    ),
                    tint = if (isFavorite) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryList(
    categories: List<DataCategory>,
    isGrid: Boolean,
    onCategoryClick: (DataCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(categories) { category ->
                CategoryGridItem(category = category, onClick = { onCategoryClick(category) })
            }
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            items(categories) { category ->
                CategoryListItem(category = category, onClick = { onCategoryClick(category) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun CategoryListItem(
    category: DataCategory,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIcon(category = category, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = category.Name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (category.UsedCount > 0) {
            Text(
                text = category.UsedCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryGridItem(
    category: DataCategory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CategoryIcon(category = category, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = category.Name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoryIcon(category: DataCategory, modifier: Modifier = Modifier) {
    val emoji = remember(category.Name) {
        if (category.Name.length == 2) EmojiUtils.getFlagEmoji(category.Name) else null
    }
    
    if (emoji != null) {
        Text(text = emoji, modifier = modifier, textAlign = TextAlign.Center)
    } else {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
