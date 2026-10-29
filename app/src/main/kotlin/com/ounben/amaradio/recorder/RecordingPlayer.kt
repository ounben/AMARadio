package com.ounben.amaradio.recorder

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.StationIcon
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Standalone floating player dialog for recorded audio files.
 * Manages an isolated ExoPlayer instance with a full timeline/seek slider,
 * station artwork, playback controls, and interactive song chapter navigation.
 */
@Composable
fun RecordingPlayerDialog(
    record: RecordEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(record.durationMs) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableFloatStateOf(0f) }

    val db = remember { AMARadioUserDatabase.getDatabase(context) }
    val tracks by db.recordDao().getTracksForRecordingFlow(record.id).collectAsState(initial = emptyList())

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(record.fileUri)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && durationMs <= 0) {
                    durationMs = exoPlayer.duration.coerceAtLeast(1L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isSeeking) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    val currentTrackTitle = remember(currentPositionMs, tracks) {
        val currentTrack = tracks.lastOrNull { it.offsetMs <= currentPositionMs }
        currentTrack?.trackTitle ?: ""
    }

    AlertDialog(
        onDismissRequest = {
            exoPlayer.stop()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.Name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            exoPlayer.stop()
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    StationIcon(
                        stationName = record.Name,
                        stationUuid = record.StationUuid,
                        iconUrl = record.Favicon.ifEmpty { null },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (currentTrackTitle.isNotEmpty()) {
                    Text(
                        text = currentTrackTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmaradioAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                val displayPosition = if (isSeeking) sliderPositionMs.toLong() else currentPositionMs
                val maxDuration = if (durationMs > 0) durationMs else 1L

                Slider(
                    value = displayPosition.coerceIn(0L, maxDuration).toFloat(),
                    onValueChange = {
                        isSeeking = true
                        sliderPositionMs = it
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(sliderPositionMs.toLong())
                        currentPositionMs = sliderPositionMs.toLong()
                        isSeeking = false
                    },
                    valueRange = 0f..maxDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AmaradioAmber,
                        activeTrackColor = AmaradioAmber,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(displayPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(maxDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                        }
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(56.dp),
                            tint = AmaradioAmber
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxDuration)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                        }
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (tracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tracks.forEach { track ->
                            val isSelected = currentTrackTitle == track.trackTitle
                            Text(
                                text = "${formatDuration(track.offsetMs)} - ${track.trackTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AmaradioAmber else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        exoPlayer.seekTo(track.offsetMs)
                                        currentPositionMs = track.offsetMs
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
