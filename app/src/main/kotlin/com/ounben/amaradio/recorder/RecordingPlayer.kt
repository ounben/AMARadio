package com.ounben.amaradio.recorder

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.ounben.amaradio.R
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.SolidRedError
import com.ounben.amaradio.ui.StationIcon
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private const val SEEK_JUMP_MS = 15_000L
internal val PLAYBACK_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)

/**
 * Lean, minimalist floating player dialog using AndroidX Media3 (ExoPlayer).
 * Decodes OggFLAC, OggVorbis, OggOpus, MP3, AAC, M4A, and FLAC containers cleanly
 * without C++ Stagefright status=0x1 native crashes.
 */
@OptIn(ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun RecordingPlayerDialog(
    record: RecordEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(record.durationMs.coerceAtLeast(0L)) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember(record.fileUri) { mutableFloatStateOf(1.0f) }
    var playerErrorRes by remember { mutableStateOf<Int?>(null) }

    val exoPlayer = remember(record.fileUri) {
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            // Xing/Info/VBRI headers are automatically prioritized by Mp3Extractor;
            // FLAG_ENABLE_CONSTANT_BITRATE_SEEKING provides instant duration & seeking
            // fallback for legacy MP3 recordings without a Xing header.
            setMp3ExtractorFlags(
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
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
            .setLooper(android.os.Looper.getMainLooper())
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(record.fileUri)))
                setPlaybackSpeed(1.0f)
                prepare()
                playWhenReady = true
            }
    }

    fun applyPlaybackSpeed(newSpeed: Float) {
        playbackSpeed = newSpeed
        try {
            exoPlayer.setPlaybackSpeed(newSpeed)
        } catch (e: Exception) {
            Log.e("RecordingPlayer", "Failed to set playback speed $newSpeed", e)
        }
    }

    fun syncDurationFromPlayer() {
        val exDuration = exoPlayer.duration
        if (exDuration == C.TIME_UNSET || exDuration <= 0L) return

        // Protect against legacy OGG files recorded before full page-buffered granulepos normalization
        // (which can report multi-hour live server timestamps instead of the clip's duration).
        val isOggFormat = record.format.equals("ogg", ignoreCase = true) ||
            record.format.equals("oga", ignoreCase = true)
        val isSuspiciousLegacyOggDuration = isOggFormat &&
            record.durationMs > 0L &&
            exDuration > maxOf(record.durationMs * 3L, record.durationMs + 60_000L)

        if (!isSuspiciousLegacyOggDuration) {
            durationMs = exDuration
        } else if (durationMs <= 0L) {
            durationMs = record.durationMs
        }
    }

    fun seekToClamped(targetMs: Long, maxDuration: Long) {
        try {
            val clamped = targetMs.coerceIn(0L, maxDuration)
            currentPositionMs = clamped
            sliderPositionMs = clamped.toFloat()
            exoPlayer.seekTo(clamped)
        } catch (_: Exception) {}
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncDurationFromPlayer()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!isSeeking) {
                    val pos = newPosition.positionMs.coerceAtLeast(0L)
                    currentPositionMs = pos
                    if (pos > durationMs) {
                        durationMs = pos
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    syncDurationFromPlayer()
                } else if (state == Player.STATE_ENDED) {
                    isPlaying = false
                    isSeeking = false
                    exoPlayer.playWhenReady = false
                    exoPlayer.seekTo(0L)
                    currentPositionMs = 0L
                    sliderPositionMs = 0f
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("RecordingPlayer", "ExoPlayer playback error for ${record.fileUri}", error)
                playerErrorRes = R.string.error_cannot_open_file
                isPlaying = false
                isSeeking = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            try {
                exoPlayer.removeListener(listener)
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            syncDurationFromPlayer()
            if (!isSeeking) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPositionMs = pos
                if (pos > durationMs) {
                    durationMs = pos
                }
            }
            delay(100)
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
                            contentDescription = stringResource(R.string.accessibility_close),
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

                // Error Message Display if playerErrorRes != null
                playerErrorRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SolidRedError,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Slider & Time Display
                val maxDuration = maxOf(durationMs, currentPositionMs, 1L)
                val displayPosition = (if (isSeeking) sliderPositionMs.toLong() else currentPositionMs).coerceIn(0L, maxDuration)
                val adaptiveStepMs = calculateAdaptiveSeekStepMs(maxDuration)

                val seekSliderLabel = stringResource(R.string.accessibility_seek_slider)
                val rewind15Label = stringResource(R.string.action_rewind_15s)
                val forward15Label = stringResource(R.string.action_forward_15s)
                val speedLabel = formatPlaybackSpeed(playbackSpeed)
                val speedContentDesc = stringResource(R.string.accessibility_playback_speed, speedLabel)
                val changeSpeedLabel = stringResource(R.string.action_change_playback_speed)
                val resetSpeedLabel = stringResource(R.string.action_reset_playback_speed)
                val isCustomSpeed = abs(playbackSpeed - 1.0f) > 0.01f
                val accessibleTimeState = stringResource(
                    R.string.accessibility_time_position,
                    formatAccessibleDuration(displayPosition),
                    formatAccessibleDuration(maxDuration)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = seekSliderLabel
                            stateDescription = accessibleTimeState
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                current = displayPosition.toFloat(),
                                range = 0f..maxDuration.toFloat()
                            )
                            setProgress { targetValue ->
                                val newTargetMs = when {
                                    targetValue > displayPosition.toFloat() ->
                                        (displayPosition + adaptiveStepMs).coerceAtMost(maxDuration)
                                    targetValue < displayPosition.toFloat() ->
                                        (displayPosition - adaptiveStepMs).coerceAtLeast(0L)
                                    else -> displayPosition
                                }
                                if (newTargetMs == displayPosition) {
                                    false
                                } else {
                                    isSeeking = false
                                    seekToClamped(newTargetMs, maxDuration)
                                    true
                                }
                            }
                            customActions = buildList {
                                add(
                                    CustomAccessibilityAction(rewind15Label) {
                                        val target = (displayPosition - SEEK_JUMP_MS).coerceAtLeast(0L)
                                        if (target == displayPosition) {
                                            false
                                        } else {
                                            isSeeking = false
                                            seekToClamped(target, maxDuration)
                                            true
                                        }
                                    }
                                )
                                add(
                                    CustomAccessibilityAction(forward15Label) {
                                        val target = (displayPosition + SEEK_JUMP_MS).coerceAtMost(maxDuration)
                                        if (target == displayPosition) {
                                            false
                                        } else {
                                            isSeeking = false
                                            seekToClamped(target, maxDuration)
                                            true
                                        }
                                    }
                                )
                                add(
                                    CustomAccessibilityAction(changeSpeedLabel) {
                                        applyPlaybackSpeed(nextPlaybackSpeed(playbackSpeed))
                                        true
                                    }
                                )
                                if (isCustomSpeed) {
                                    add(
                                        CustomAccessibilityAction(resetSpeedLabel) {
                                            applyPlaybackSpeed(1.0f)
                                            true
                                        }
                                    )
                                }
                            }
                        }
                ) {
                    Slider(
                        value = displayPosition.toFloat(),
                        onValueChange = {
                            isSeeking = true
                            sliderPositionMs = it
                        },
                        onValueChangeFinished = {
                            seekToClamped(sliderPositionMs.toLong(), maxDuration)
                            isSeeking = false
                        },
                        valueRange = 0f..maxDuration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = AmaradioAmber,
                            activeTrackColor = AmaradioAmber,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics { }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(displayPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                    Text(
                        text = formatDuration(maxDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback Controls: Rewind 15s | Play/Pause | Forward 15s | Speed (1x..3x)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isSeeking = false
                            seekToClamped(displayPosition - SEEK_JUMP_MS, maxDuration)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = rewind15Label,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            try {
                                if (exoPlayer.playbackState == Player.STATE_ENDED ||
                                    (durationMs > 0L && currentPositionMs >= durationMs)
                                ) {
                                    isSeeking = false
                                    seekToClamped(0L, maxDuration)
                                    exoPlayer.play()
                                } else if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
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
                            contentDescription = stringResource(if (isPlaying) R.string.action_pause_recording else R.string.action_play_recording),
                            modifier = Modifier.size(56.dp),
                            tint = AmaradioAmber
                        )
                    }

                    IconButton(
                        onClick = {
                            isSeeking = false
                            seekToClamped(displayPosition + SEEK_JUMP_MS, maxDuration)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = forward15Label,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCustomSpeed) AmaradioAmber.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .semantics {
                                contentDescription = speedContentDesc
                                if (isCustomSpeed) {
                                    customActions = listOf(
                                        CustomAccessibilityAction(resetSpeedLabel) {
                                            applyPlaybackSpeed(1.0f)
                                            true
                                        }
                                    )
                                }
                            }
                            .combinedClickable(
                                role = Role.Button,
                                onClickLabel = changeSpeedLabel,
                                onLongClickLabel = if (isCustomSpeed) resetSpeedLabel else null,
                                onClick = { applyPlaybackSpeed(nextPlaybackSpeed(playbackSpeed)) },
                                onLongClick = {
                                    if (isCustomSpeed) {
                                        applyPlaybackSpeed(1.0f)
                                    }
                                }
                            )
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = speedLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = if (isCustomSpeed) AmaradioAmber else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

internal fun nextPlaybackSpeed(currentSpeed: Float): Float {
    val idx = PLAYBACK_SPEEDS.indexOfFirst { abs(it - currentSpeed) < 0.01f }
    return if (idx < 0) PLAYBACK_SPEEDS.first() else PLAYBACK_SPEEDS[(idx + 1) % PLAYBACK_SPEEDS.size]
}

internal fun formatPlaybackSpeed(speed: Float): String {
    val formatted = when {
        speed % 1f == 0f -> speed.toInt().toString()
        (speed * 10f) % 1f == 0f -> String.format(Locale.US, "%.1f", speed)
        else -> String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
    return "${formatted}x"
}

internal fun calculateAdaptiveSeekStepMs(durationMs: Long): Long = when {
    durationMs < 2L * 60_000L -> 5_000L
    durationMs <= 30L * 60_000L -> 15_000L
    durationMs <= 120L * 60_000L -> 60_000L
    else -> 300_000L
}

internal fun formatAccessibleDuration(ms: Long, locale: Locale = Locale.getDefault()): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    val measures = buildList {
        if (hours > 0L) add(Measure(hours, MeasureUnit.HOUR))
        if (minutes > 0L) add(Measure(minutes, MeasureUnit.MINUTE))
        if (seconds > 0L || (hours == 0L && minutes == 0L)) add(Measure(seconds, MeasureUnit.SECOND))
    }

    return try {
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)
            .formatMeasures(*measures.toTypedArray())
    } catch (_: Exception) {
        buildString {
            if (hours > 0L) append("$hours h ")
            if (minutes > 0L) append("$minutes min ")
            if (seconds > 0L || isEmpty()) append("$seconds s")
        }.trim()
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
