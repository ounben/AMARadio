package com.ounben.amaradio.recorder

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.StationIcon
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Lean, minimalist floating player dialog using native android.media.MediaPlayer.
 * Plays local audio recordings cleanly through prerolls, bitrate changes, and headers without premature EOS.
 */
@Composable
fun RecordingPlayerDialog(
    record: RecordEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableFloatStateOf(0f) }

    val mediaPlayer = remember(record.fileUri) {
        MediaPlayer().apply {
            try {
                setDataSource(context, Uri.parse(record.fileUri))
                prepare()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val durationMs = remember(mediaPlayer) {
        val mpDuration = try { mediaPlayer.duration.toLong() } catch (_: Exception) { 0L }
        mpDuration.coerceAtLeast(record.durationMs)
    }

    DisposableEffect(mediaPlayer) {
        mediaPlayer.setOnErrorListener { _, _, _ ->
            true // Prevent crashes on invalid state calls
        }
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            currentPositionMs = durationMs
        }
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.reset()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isSeeking) {
                currentPositionMs = try { mediaPlayer.currentPosition.toLong() } catch (_: Exception) { currentPositionMs }
            }
            delay(200)
        }
    }

    AlertDialog(
        onDismissRequest = {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
            } catch (_: Exception) {}
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Station Name & Close Button
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
                            try {
                                if (mediaPlayer.isPlaying) {
                                    mediaPlayer.stop()
                                }
                            } catch (_: Exception) {}
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Station Icon
                Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    StationIcon(
                        stationName = record.Name,
                        stationUuid = record.StationUuid,
                        iconUrl = record.Favicon.ifEmpty { null },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Slider & Time Display
                val displayPosition = if (isSeeking) sliderPositionMs.toLong() else currentPositionMs
                val maxDuration = maxOf(durationMs, 1L)

                Slider(
                    value = displayPosition.coerceIn(0L, maxDuration).toFloat(),
                    onValueChange = {
                        isSeeking = true
                        sliderPositionMs = it
                    },
                    onValueChangeFinished = {
                        try {
                            mediaPlayer.seekTo(sliderPositionMs.toInt())
                        } catch (_: Exception) {}
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

                Spacer(modifier = Modifier.height(16.dp))

                // Central Play/Pause Button
                IconButton(
                    onClick = {
                        try {
                            if (currentPositionMs >= durationMs) {
                                mediaPlayer.seekTo(0)
                                mediaPlayer.start()
                                isPlaying = true
                            } else if (isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
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
