package com.ounben.amaradio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.filled.SwapVert
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.zIndex
import android.content.ClipDescription
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.time.Duration.Companion.milliseconds
import android.content.ClipData
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ounben.amaradio.R
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.EmojiUtils
import com.ounben.amaradio.utils.StationIconProvider
import com.ounben.amaradio.utils.StationPlaceholderUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun StationIcon(
    stationName: String,
    stationUuid: String,
    iconUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val placeholderText = remember(stationName) { StationPlaceholderUtils.extractPlaceholderText(stationName) }
    val placeholderColor = remember(stationUuid) { Color(StationPlaceholderUtils.getPlaceholderColor(stationUuid)) }
    
    val finalIconUri = remember(stationUuid, iconUrl) {
        val iconDir = File(context.filesDir, "station_icons")
        val iconFile = File(iconDir, "$stationUuid.jpg")
        
        if (!iconUrl.isNullOrBlank() && iconUrl.startsWith("file:/")) {
            android.net.Uri.parse(iconUrl)
        } else if (iconFile.exists()) {
            StationIconProvider.getIconUri(stationUuid, stationName)
        } else if (!iconUrl.isNullOrBlank() && iconUrl != "null" && iconUrl.startsWith("http")) {
            android.net.Uri.parse(iconUrl)
        } else {
            StationIconProvider.getIconUri(stationUuid, stationName)
        }
    }
    
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    val imageRequest = remember(finalIconUri, stationUuid) {
        ImageRequest.Builder(context)
            .data(finalIconUri)
            .size(512, 512) 
            .allowHardware(false)
            .crossfade(true)
            .listener(
                onSuccess = { _, result ->
                    val iconDir = File(context.filesDir, "station_icons")
                    val iconFile = File(iconDir, "$stationUuid.jpg")
                    if (!iconFile.exists()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                if (!iconDir.exists()) iconDir.mkdirs()
                                val bitmap = result.drawable.toBitmap(512, 512, android.graphics.Bitmap.Config.RGB_565)
                                FileOutputStream(iconFile).use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
            )
            .build()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isError || isLoading) placeholderColor else Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading || isError) {
            Text(
                text = placeholderText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(4.dp),
            contentScale = ContentScale.Fit,
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
                isError = state is AsyncImagePainter.State.Error
            }
        )
    }
}

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
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
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
        } else if (stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyMessage,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            StationList(
                stations = stations,
                isGrid = isGrid,
                onStationClick = onStationClick,
                onFavoriteClick = onFavoriteClick,
                isFavorite = isFavorite,
                onDeleteClick = onDeleteClick
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
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(
                items = stations, 
                key = { _, station -> station.StationUuid },
                contentType = { _, _ -> "station" }
            ) { _, station ->
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
            itemsIndexed(
                items = stations, 
                key = { _, station -> station.StationUuid },
                contentType = { _, _ -> "station" }
            ) { _, station ->
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
            onDeleteClick = onDeleteClick?.let { { it(station) } },
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
            items(
                count = tracks.itemCount,
                key = { index -> 
                    val track = tracks.peek(index)
                    if (track != null) "${track.uid}_$index" else "placeholder_$index"
                },
                contentType = { "track" }
            ) { index ->
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
            StationIcon(
                stationName = track.stationName.ifEmpty { track.stationUuid },
                stationUuid = track.stationUuid,
                iconUrl = track.stationIconUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    useInternalClickable: Boolean = true,
    dragHandle: (@Composable (Modifier) -> Unit)? = null
) {
    val context = LocalContext.current
    val flagEmoji = remember(station.CountryCode) { EmojiUtils.getFlagEmoji(station.CountryCode) ?: "" }
    val details = remember(station.ClickCount, station.Votes, station.Language, station.Bitrate, station.Codec) { 
        station.getShortDetails(context) 
    }

    val accessibilityDesc = stringResource(
        R.string.accessibility_station_description,
        station.Name,
        station.Language.ifEmpty { stringResource(R.string.not_applicable) },
        station.TagsAll.ifEmpty { stringResource(R.string.not_applicable) }
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDesc
            }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (useInternalClickable) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                StationIcon(
                    stationName = station.Name,
                    stationUuid = station.StationUuid,
                    iconUrl = station.IconUrl,
                    modifier = Modifier.fillMaxSize()
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (station.TagsAll.isNotEmpty()) {
                    Text(
                        text = station.TagsAll,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(48.dp)
                .combinedClickable(
                    onClick = { 
                        if (!isFavorite) onFavoriteClick() 
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
                tint = if (isFavorite) AmaradioAmber else MaterialTheme.colorScheme.onSurface
            )
        }

        if (dragHandle != null) {
            dragHandle(Modifier.fillMaxHeight())
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    useInternalClickable: Boolean = true,
    dragHandle: (@Composable (Modifier) -> Unit)? = null
) {
    val accessibilityDesc = stringResource(
        R.string.accessibility_station_description,
        station.Name,
        station.Language.ifEmpty { stringResource(R.string.not_applicable) },
        station.TagsAll.ifEmpty { stringResource(R.string.not_applicable) }
    )

    Card(
        modifier = modifier
            .padding(4.dp)
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDesc
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (useInternalClickable) {
                            Modifier.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick
                            )
                        } else Modifier
                    )
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StationIcon(
                    stationName = station.Name,
                    stationUuid = station.StationUuid,
                    iconUrl = station.IconUrl,
                    modifier = Modifier.size(80.dp)
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
                        .heightIn(min = 32.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (dragHandle != null) {
                Box(modifier = Modifier.align(Alignment.TopStart).fillMaxHeight()) {
                    dragHandle(Modifier.fillMaxHeight())
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .combinedClickable(
                        onClick = {
                            if (!isFavorite) onFavoriteClick()
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
                    tint = if (isFavorite) AmaradioAmber else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderableStationList(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onReorder: (List<DataRadioStation>) -> Unit,
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
    onLongClick: ((DataRadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stationsLocal = remember { mutableStateListOf<DataRadioStation>() }
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }
    
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(stations) {
        if (stationsLocal.size != stations.size || stationsLocal.map { it.StationUuid } != stations.map { it.StationUuid }) {
            stationsLocal.clear()
            stationsLocal.addAll(stations)
        }
    }

    if (isGrid) {
        val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
            stationsLocal.add(to.index, stationsLocal.removeAt(from.index))
            onReorder(stationsLocal.toList())
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(140.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(stationsLocal, key = { _, s -> s.StationUuid }) { index, station ->
                ReorderableItem(reorderableState, key = station.StationUuid) { isDragging ->
                    val itemScope = this
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                            .graphicsLayer {
                                scaleX = if (isDragging) 1.05f else 1f
                                scaleY = if (isDragging) 1.05f else 1f
                                alpha = if (isDragging) 0.8f else 1f
                            }
                    ) {
                        StationGridItem(
                            station = station,
                            isFavorite = isFavorite(station.StationUuid),
                            onClick = { onStationClick(station) },
                            onFavoriteClick = { onFavoriteClick(station) },
                            onLongClick = { 
                                if (onLongClick != null) onLongClick.invoke(station) 
                                else stationWithOptions = station 
                            },
                            useInternalClickable = true,
                            dragHandle = { dragModifier ->
                                with(itemScope) {
                                    Box(
                                        modifier = dragModifier
                                            .width(56.dp)
                                            .draggableHandle(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    } else {
        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
            stationsLocal.add(to.index, stationsLocal.removeAt(from.index))
            onReorder(stationsLocal.toList())
        }

        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            itemsIndexed(stationsLocal, key = { _, s -> s.StationUuid }) { index, station ->
                ReorderableItem(reorderableState, key = station.StationUuid) { isDragging ->
                    val itemScope = this
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = if (isDragging) 1.03f else 1f
                                scaleY = if (isDragging) 1.03f else 1f
                            },
                        shadowElevation = elevation,
                        tonalElevation = elevation
                    ) {
                        Column {
                            StationListItem(
                                station = station,
                                isFavorite = isFavorite(station.StationUuid),
                                onClick = { onStationClick(station) },
                                onFavoriteClick = { onFavoriteClick(station) },
                                onLongClick = { 
                                    if (onLongClick != null) onLongClick.invoke(station) 
                                    else stationWithOptions = station 
                                },
                                useInternalClickable = true,
                                dragHandle = { dragModifier ->
                                    with(itemScope) {
                                        Box(
                                            modifier = dragModifier
                                                .width(56.dp)
                                                .draggableHandle(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SwapVert,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = isFavorite(station.StationUuid),
            onFavoriteClick = { onFavoriteClick(station) },
            onDeleteClick = onDeleteClick?.let { { it(station) } },
            onDismiss = { stationWithOptions = null }
        )
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
            itemsIndexed(
                items = categories,
                key = { index, category -> "${category.Name}_$index" },
                contentType = { _, _ -> "category" }
            ) { _, category ->
                CategoryGridItem(category = category, onClick = { onCategoryClick(category) })
            }
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            itemsIndexed(
                items = categories,
                key = { index, category -> "${category.Name}_$index" },
                contentType = { _, _ -> "category" }
            ) { _, category ->
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
