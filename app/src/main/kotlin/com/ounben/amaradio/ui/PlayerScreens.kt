package com.ounben.amaradio.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
                    AsyncImage(
                        model = station?.IconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
                        error = painterResource(R.drawable.ic_radio_24dp),
                        placeholder = painterResource(R.drawable.ic_radio_24dp)
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
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    AsyncImage(
                        model = station?.IconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit,
                        error = painterResource(R.drawable.ic_radio_24dp),
                        placeholder = painterResource(R.drawable.ic_radio_24dp)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerViewModel.skipToPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier.size(84.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying) 
                            Icons.Default.PauseCircle 
                        else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(76.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { playerViewModel.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { playerViewModel.toggleFavorite() }) {
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
