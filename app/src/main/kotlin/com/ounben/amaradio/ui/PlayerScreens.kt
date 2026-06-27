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
    onMoreClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val station = uiState.currentStation
    val liveInfo = uiState.liveInfo
    val stationName = station?.Name ?: ""
    
    val songTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else if (liveInfo.title.isNotEmpty()) liveInfo.title else ""
    val artistName = if (liveInfo.track.isNotEmpty()) liveInfo.artist else ""
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onToggleBottomSheet() }
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopStart),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(40.dp, 4.dp)
                    .alpha(0.2f)
                    .background(MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.small)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Station Icon
                Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = station?.IconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
                        error = painterResource(R.drawable.ic_radio_24dp),
                        placeholder = painterResource(R.drawable.ic_radio_24dp),
                        colorFilter = if (station?.IconUrl.isNullOrEmpty()) ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Middle: Text Info (Flexible rows for accessibility)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Row 1: Station Name
                    Text(
                        text = stationName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = AmaradioAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Row 2: Song Title
                    if (songTitle.isNotEmpty()) {
                        Text(
                            text = songTitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Row 3: Artist
                    if (artistName.isNotEmpty()) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Row 4: Status Indicator
                    PlayerStatusRow(
                        playState = uiState.playState,
                        bandwidthFlow = viewModel.bandwidth
                    )
                }

                // Right: Play/Pause (Enlarged to 72dp)
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying) 
                            Icons.Default.PauseCircle 
                        else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
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
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        
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
                    tonalElevation = 2.dp
                ) {
                    AsyncImage(
                        model = station?.IconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit,
                        error = painterResource(R.drawable.ic_radio_24dp),
                        placeholder = painterResource(R.drawable.ic_radio_24dp),
                        colorFilter = if (station?.IconUrl.isNullOrEmpty()) ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Left-aligned Text Info
                Column(modifier = Modifier.weight(1f)) {
                    // Row 1: Station Name (like bodyLarge in list)
                    Text(
                        text = station?.Name ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = AmaradioAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Row 2: Song Title (like bodySmall in list)
                    if (songTitle.isNotEmpty()) {
                        Text(
                            text = songTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Row 3: Artist (like labelSmall in list)
                    if (artistName.isNotEmpty()) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (station != null) {
                        val flagEmoji = remember(station.CountryCode) { EmojiUtils.getFlagEmoji(station.CountryCode) ?: "" }
                        val details = remember(station) { station.getShortDetails(context) }
                        
                        Text(
                            text = if (flagEmoji.isNotEmpty()) "$flagEmoji $details" else details,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    PlayerStatusRow(
                        playState = uiState.playState,
                        bandwidthFlow = playerViewModel.bandwidth,
                        showText = true
                    )
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
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
            tonalElevation = 2.dp,
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
                        tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
    if (playState == PlayState.Idle || playState == PlayState.Paused) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        val color = when (playState) {
            PlayState.PrePlaying -> AmaradioAmber
            PlayState.Playing -> Color(0xFF4CAF50) // Green
            PlayState.Error -> Color(0xFFF44336) // Red
            else -> Color.Transparent
        }

        if (playState == PlayState.PrePlaying || playState == PlayState.Playing) {
            // HIGHLY OPTIMIZED: Only this tiny Text component recomposes every second.
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
        }
    }
}
