package com.ounben.amaradio.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.ounben.amaradio.R
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.utils.EmojiUtils
import androidx.compose.ui.platform.LocalContext

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    isHeaderRole: Boolean,
    onToggleBottomSheet: () -> Unit,
    onMoreClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val stationName = uiState.currentStation?.Name ?: ""
    val streamTitle = uiState.liveInfo.title
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onToggleBottomSheet() }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Top Shadow/Divider
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
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = uiState.currentStation?.IconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    error = painterResource(R.drawable.ic_radio_24dp),
                    placeholder = painterResource(R.drawable.ic_radio_24dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isHeaderRole) {
                        Text(
                            text = stationName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = stationName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (streamTitle.isNotEmpty()) {
                            Text(
                                text = streamTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        PlayerStatusRow(state = uiState.playState)
                    }
                }

                if (isHeaderRole) {
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying) 
                                    R.drawable.ic_pause_circle 
                                else R.drawable.ic_play_circle
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
    
    val station = uiState.currentStation
    val streamTitle = uiState.liveInfo.title
    val displayTitle = if (streamTitle.isNotEmpty()) streamTitle else station?.Name ?: ""

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (station != null) {
                val flagEmoji = remember(station.CountryCode) { EmojiUtils.getFlagEmoji(station.CountryCode) ?: "" }
                val details = remember(station) { station.getLongDetails(context) }
                
                Text(
                    text = if (flagEmoji.isNotEmpty()) "$flagEmoji $details" else details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                PlayerStatusRow(state = uiState.playState, showText = true)

                Spacer(modifier = Modifier.height(8.dp))

                val tags = remember(station.TagsAll) { 
                    station.TagsAll.split(",").map { it.trim() }.filter { it.isNotEmpty() } 
                }
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    tags.take(6).forEach { tag ->
                        Surface(
                            modifier = Modifier.padding(4.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
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
                color = MaterialTheme.colorScheme.primary
            )
            
            TrackList(
                tracks = tracks,
                onTrackClick = onTrackClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Playback Controls
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
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
                        painter = painterResource(R.drawable.ic_skip_previous_circle),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying) 
                                R.drawable.ic_pause_circle 
                            else R.drawable.ic_play_circle
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { playerViewModel.skipToNext() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next_circle),
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
}

@Composable
fun PlayerStatusRow(state: PlayState, showText: Boolean = false) {
    if (state == PlayState.Idle || state == PlayState.Paused) return

    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        val color = when (state) {
            PlayState.PrePlaying -> Color(0xFFFFA500) // Orange
            PlayState.Playing -> Color(0xFF4CAF50) // Green
            PlayState.Error -> Color(0xFFF44336) // Red
            else -> Color.Transparent
        }

        Icon(
            painter = painterResource(R.drawable.ic_sync_black_24dp),
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(14.dp)
                .then(if (state == PlayState.PrePlaying) Modifier.alpha(alpha) else Modifier)
        )

        if (state == PlayState.Error) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.error_station_load),
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        } else if (showText && state == PlayState.PrePlaying) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.notify_pre_play),
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
