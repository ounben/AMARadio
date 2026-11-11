package com.ounben.amaradio.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.ounben.amaradio.R
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.utils.EmojiUtils
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    isHeaderRole: Boolean,
    onToggleBottomSheet: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val station = uiState.currentStation
    val liveInfo = uiState.liveInfo
    val stationName = station?.Name ?: ""
    
    val songTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else if (liveInfo.title.isNotEmpty()) liveInfo.title else ""
    val artistName = if (liveInfo.track.isNotEmpty()) liveInfo.artist else ""
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onToggleBottomSheet() }
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopStart),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(40.dp, 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, shape = MaterialTheme.shapes.small)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Station Icon
                Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    StationIcon(
                        stationName = stationName,
                        stationUuid = station?.StationUuid ?: "",
                        iconUrl = station?.IconUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Middle: Text Info (STRICT 4-ROW LAYOUT)
                // IMPORTANT: We always render exactly 4 lines to ensure constant height regardless of metadata availability.
                // If a value is missing, we use " " as a placeholder so the line still takes up space in the system font.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Row 1: Station Name
                    Text(
                        text = stationName.ifEmpty { " " },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = AmaradioAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Row 2: Song Title
                    Text(
                        text = songTitle.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Row 3: Artist
                    Text(
                        text = artistName.ifEmpty { " " },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Row 4: Status Indicator
                    Box(modifier = Modifier.wrapContentHeight().padding(top = 2.dp), contentAlignment = Alignment.CenterStart) {
                        PlayerStatusRow(
                            playState = uiState.playState,
                            bandwidthFlow = viewModel.bandwidth
                        )
                    }
                }

                // Right: Play/Pause
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying) 
                            Icons.Default.PauseCircle 
                        else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullPlayer(
    playerViewModel: PlayerViewModel,
    trackHistoryViewModel: TrackHistoryViewModel,
    onTrackClick: (TrackHistoryEntry) -> Unit
) {
    val uiState by playerViewModel.uiState.collectAsState()
    val tracks = trackHistoryViewModel.allHistoryPaged.collectAsLazyPagingItems()
    val context = LocalContext.current
    var trackWithOptions by remember { mutableStateOf<TrackHistoryEntry?>(null) }
    var showRemoveFavoriteDialog by remember { mutableStateOf(false) }
    
    val station = uiState.currentStation
    val liveInfo = uiState.liveInfo
    
    val songTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else if (liveInfo.title.isNotEmpty()) liveInfo.title else ""
    val artistName = if (liveInfo.track.isNotEmpty()) liveInfo.artist else ""

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Header Layout: Row with Icon Left and Text Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large Station Icon
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    StationIcon(
                        stationName = station?.Name ?: "",
                        stationUuid = station?.StationUuid ?: "",
                        iconUrl = station?.IconUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Left-aligned Text Info (STRICT 4-ROW LAYOUT)
                // IMPORTANT: We always render exactly 4 lines to ensure constant height regardless of metadata availability.
                // If a value is missing, we use " " as a placeholder so the line still takes up space in the system font.
                Column(modifier = Modifier.weight(1f)) {
                    // Row 1: Station Name
                    Text(
                        text = (station?.Name ?: "").ifEmpty { " " },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = AmaradioAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 22.dp)
                    )

                    // Row 2: Song Title
                    Text(
                        text = songTitle.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 18.dp)
                    )

                    // Row 3: Artist
                    Text(
                        text = artistName.ifEmpty { " " },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 16.dp)
                    )

                    // Row 4: Status / Metadata
                    Box(modifier = Modifier.wrapContentHeight().padding(top = 2.dp), contentAlignment = Alignment.CenterStart) {
                        if (uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying) {
                            PlayerStatusRow(
                                playState = uiState.playState,
                                bandwidthFlow = playerViewModel.bandwidth,
                                showText = true
                            )
                        } else if (station != null) {
                            val flagEmoji = remember(station.CountryCode) { EmojiUtils.getFlagEmoji(station.CountryCode) ?: "" }
                            val details = remember(station) { station.getShortDetails(context) }
                            Text(
                                text = if (flagEmoji.isNotEmpty()) "$flagEmoji $details" else details,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tags
            if (station != null && station.TagsAll.isNotEmpty()) {
                val tags = remember(station.TagsAll) { 
                    station.TagsAll.split(",").map { it.trim() }.filter { it.isNotEmpty() } 
                }
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    tags.take(8).forEach { tag ->
                        Surface(
                            modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.tab_player_history),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            TrackList(
                tracks = tracks,
                onTrackClick = onTrackClick,
                onTrackLongClick = { trackWithOptions = it },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Control Bar
        Surface(
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            val recordManager = remember { com.ounben.amaradio.recorder.RecordManager.getInstance(context) }
            val recordState by recordManager.recordingState.collectAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerViewModel.skipToPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying) 
                            Icons.Default.PauseCircle 
                        else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { playerViewModel.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Record Button (🔴 / 🛑) with Timer Selection
                com.ounben.amaradio.recorder.RecordButtonWithTimer(
                    currentStation = station,
                    iconSize = 32.dp
                )

                IconButton(
                    onClick = {
                        if (uiState.isFavorite) {
                            showRemoveFavoriteDialog = true
                        } else {
                            playerViewModel.toggleFavorite()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (uiState.isFavorite) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showRemoveFavoriteDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveFavoriteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.detail_unstar),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "${stringResource(R.string.detail_unstar)}: ${station?.Name ?: ""}?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playerViewModel.toggleFavorite()
                        showRemoveFavoriteDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_ok),
                        fontWeight = FontWeight.Bold,
                        color = AmaradioAmber
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFavoriteDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    trackWithOptions?.let { track ->
        TrackOptionsDialog(
            track = track,
            onDismiss = { trackWithOptions = null }
        )
    }
}

/**
 * Permanent player column for the wide (landscape) layout, e.g. car tablets.
 * Large centred artwork and text that can be read at a glance, and comfortable touch targets.
 * On short screens (small head units) the recent-tracks list is dropped so the
 * controls always stay fully visible.
 */
@Composable
fun SidePanelPlayer(
    playerViewModel: PlayerViewModel,
    trackHistoryViewModel: TrackHistoryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by playerViewModel.uiState.collectAsState()
    val historyFlow = remember(trackHistoryViewModel) { trackHistoryViewModel.allHistoryPaged }
    val tracks = historyFlow.collectAsLazyPagingItems()
    var trackWithOptions by remember { mutableStateOf<TrackHistoryEntry?>(null) }
    var showRemoveFavoriteDialog by remember { mutableStateOf(false) }

    val station = uiState.currentStation
    val liveInfo = uiState.liveInfo
    val songTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else if (liveInfo.title.isNotEmpty()) liveInfo.title else ""
    val artistName = if (liveInfo.track.isNotEmpty()) liveInfo.artist else ""
    val isPlaying = uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying

    BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        val showTrackList = maxHeight >= 480.dp
        val iconSize = (maxHeight * 0.26f).coerceIn(64.dp, 140.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (showTrackList) Arrangement.Top else Arrangement.Center
            ) {
                Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                    StationIcon(
                        stationName = station?.Name ?: "",
                        stationUuid = station?.StationUuid ?: "",
                        iconUrl = station?.IconUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fixed number of lines (" " placeholders) so the layout does not jump with metadata
                Text(
                    text = (station?.Name ?: "").ifEmpty { " " },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AmaradioAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = songTitle.ifEmpty { " " },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = if (showTrackList) 2 else 1,
                    maxLines = if (showTrackList) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = artistName.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    PlayerStatusRow(
                        playState = uiState.playState,
                        bandwidthFlow = playerViewModel.bandwidth,
                        showText = true
                    )
                }

                if (showTrackList) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.tab_player_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    TrackList(
                        tracks = tracks,
                        onTrackClick = { },
                        onTrackLongClick = { trackWithOptions = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            // Controls: still comfortable touch targets (>= 44dp), but compact enough for small panels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playerViewModel.skipToPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { playerViewModel.skipToNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        // Same as FullPlayer: removing a favourite asks for confirmation
                        if (uiState.isFavorite) {
                            showRemoveFavoriteDialog = true
                        } else {
                            playerViewModel.toggleFavorite()
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = if (uiState.isFavorite) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showRemoveFavoriteDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveFavoriteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.detail_unstar),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "${stringResource(R.string.detail_unstar)}: ${station?.Name ?: ""}?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playerViewModel.toggleFavorite()
                        showRemoveFavoriteDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_ok),
                        fontWeight = FontWeight.Bold,
                        color = AmaradioAmber
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFavoriteDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    trackWithOptions?.let { track ->
        TrackOptionsDialog(
            track = track,
            onDismiss = { trackWithOptions = null }
        )
    }
}


@Composable
fun PlayerStatusRow(
    playState: PlayState,
    bandwidthFlow: StateFlow<String>,
    showText: Boolean = false
) {
    val color = when (playState) {
        PlayState.PrePlaying -> AmaradioAmber
        PlayState.Playing -> Color(0xFF4CAF50) // Green
        PlayState.Error -> Color(0xFFF44336) // Red
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.wrapContentHeight()
    ) {
        if (playState == PlayState.PrePlaying || playState == PlayState.Playing) {
            val bandwidth by bandwidthFlow.collectAsState()
            Text(
                text = bandwidth.ifEmpty { "0.0 kB/s" },
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (showText && playState == PlayState.PrePlaying) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.notify_pre_play),
                    color = color,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else if (playState == PlayState.Error) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.error_station_load),
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        } else {
            // Placeholder to keep the 4th row height stable during Idle/Paused
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
