package com.ounben.amaradio.recorder

import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ounben.amaradio.R
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.SolidRedError
import com.ounben.amaradio.ui.StationIcon
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Lean, minimalist floating player dialog using AndroidX Media3 (ExoPlayer).
 * Decodes OggFLAC, OggVorbis, OggOpus, MP3, AAC, M4A, and FLAC containers cleanly
 * without C++ Stagefright status=0x1 native crashes.
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
    var playerError by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(record.fileUri) {
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setMp3ExtractorFlags(
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING or
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_DISABLE_ID3_METADATA
            )
            setAdtsExtractorFlags(
                androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
            )
            setConstantBitrateSeekingAlwaysEnabled(false)
        }
        val mediaSourceFactory = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
            androidx.media3.datasource.DefaultDataSource.Factory(context),
            extractorsFactory
        )

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                ctx: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(ctx)
                    .setEnableFloatOutput(false)
                    .build()
            }
        }.apply {
            setEnableDecoderFallback(true)
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
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
                if (state == Player.STATE_READY && exoPlayer.duration > 0) {
                    val exDuration = exoPlayer.duration
                    if (record.format.equals("ogg", ignoreCase = true) || exDuration > record.durationMs * 3 || exDuration <= 0L) {
                        durationMs = record.durationMs
                    } else {
                        durationMs = exDuration
                    }
                } else if (state == Player.STATE_ENDED) {
                    isPlaying = false
                    exoPlayer.seekTo(0L)
                    currentPositionMs = 0L
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("RecordingPlayer", "ExoPlayer playback error for ${record.fileUri}", error)
                playerError = context.getString(R.string.error_cannot_open_file)
                isPlaying = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isSeeking) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPositionMs = pos.coerceIn(0L, durationMs)
            }
            delay(200)
        }
    }

    AlertDialog(
        onDismissRequest = {
            try {
                exoPlayer.stop()
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
                                exoPlayer.stop()
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

                // Error Message Display if playerError != null
                if (playerError != null) {
                    Text(
                        text = playerError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SolidRedError,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Slider & Time Display
                val maxDuration = maxOf(durationMs, 1L)
                val displayPosition = (if (isSeeking) sliderPositionMs.toLong() else currentPositionMs).coerceIn(0L, maxDuration)

                Slider(
                    value = displayPosition.coerceIn(0L, maxDuration).toFloat(),
                    onValueChange = {
                        isSeeking = true
                        sliderPositionMs = it
                    },
                    onValueChangeFinished = {
                        try {
                            val targetMs = sliderPositionMs.toLong().coerceIn(0L, maxDuration)
                            currentPositionMs = targetMs
                            exoPlayer.seekTo(targetMs)
                        } catch (_: Exception) {}
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
                            if (exoPlayer.playbackState == Player.STATE_ENDED || currentPositionMs >= durationMs) {
                                isSeeking = true
                                currentPositionMs = 0L
                                exoPlayer.seekTo(0L)
                                exoPlayer.play()
                                isPlaying = true
                            } else if (isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlaying = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isSeeking = false
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
