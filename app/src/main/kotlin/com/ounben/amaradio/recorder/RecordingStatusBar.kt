package com.ounben.amaradio.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ounben.amaradio.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private data class RecordingBannerSnapshot(
    val isRecording: Boolean = false,
    val stationName: String = "",
    val startTimeMs: Long = 0L,
    val sizeText: String = "0 KB"
)

/**
 * Compact status bar shown above the player while a background recording is active.
 * Displays a record icon, truncated station name, live elapsed duration, file size, and stop action.
 */
@Composable
fun RecordingStatusBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val showRecorder = remember(context) {
        try {
            context.resources.getBoolean(R.bool.show_recorder_button)
        } catch (_: Exception) {
            false
        }
    }
    if (!showRecorder) return

    val recordManager = remember { RecordManager.getInstance(context) }
    val bannerState by remember(recordManager) {
        recordManager.recordingState
            .map { state ->
                RecordingBannerSnapshot(
                    isRecording = state.isRecording,
                    stationName = state.stationName,
                    startTimeMs = state.startTimeMs,
                    sizeText = formatRecordingSize(state.fileSizeBytes)
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = RecordingBannerSnapshot())

    if (!bannerState.isRecording) return

    var elapsedMs by remember(bannerState.startTimeMs) {
        mutableLongStateOf(
            if (bannerState.startTimeMs > 0L) {
                (System.currentTimeMillis() - bannerState.startTimeMs).coerceAtLeast(0L)
            } else {
                0L
            }
        )
    }

    LaunchedEffect(bannerState.isRecording, bannerState.startTimeMs) {
        while (true) {
            elapsedMs = if (bannerState.startTimeMs > 0L) {
                (System.currentTimeMillis() - bannerState.startTimeMs).coerceAtLeast(0L)
            } else {
                0L
            }
            delay(1000L)
        }
    }

    val durationText = formatRecordingDuration(elapsedMs)
    val recordLabel = stringResource(R.string.action_record)
    val stopLabel = stringResource(R.string.action_stop_recording)
    val statusSummary = "$recordLabel: ${bannerState.stationName}, $durationText, ${bannerState.sizeText}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = statusSummary
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.RadioButtonChecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = bannerState.stationName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$durationText • ${bannerState.sizeText}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }

            IconButton(
                onClick = { recordManager.stopRecording() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stopLabel,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatRecordingDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatRecordingSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes / 1024.0
        String.format(Locale.US, "%.0f KB", kb)
    }
}
