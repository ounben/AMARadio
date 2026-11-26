package com.ounben.amaradio.recorder

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.ui.AmaradioAmber

@Composable
fun RecordButtonWithTimer(
    currentStation: DataRadioStation?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
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
    val recordState by recordManager.recordingState.collectAsStateWithLifecycle()
    var showTimerMenu by remember { mutableStateOf(false) }
    var pendingReplaceRequest by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val noStationMsg = stringResource(R.string.error_station_load)

    val isThisStationRecording = recordState.isRecording &&
        (currentStation == null || recordState.stationUuid == currentStation.StationUuid)
    val isOtherStationRecording = recordState.isRecording &&
        currentStation != null && recordState.stationUuid != currentStation.StationUuid

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                when {
                    isThisStationRecording -> recordManager.stopRecording()
                    isOtherStationRecording -> showTimerMenu = true
                    currentStation != null -> showTimerMenu = true
                    else -> Toast.makeText(context, noStationMsg, Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Icon(
                imageVector = if (recordState.isRecording) Icons.Filled.Stop else Icons.Filled.RadioButtonChecked,
                contentDescription = stringResource(if (recordState.isRecording) R.string.action_stop_recording else R.string.action_record),
                modifier = Modifier.size(if (recordState.isRecording) iconSize * 1.22f else iconSize),
                tint = if (recordState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = showTimerMenu,
            onDismissRequest = { showTimerMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            if (isOtherStationRecording) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${stringResource(R.string.action_stop_recording)}: ${recordState.stationName}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showTimerMenu = false
                        recordManager.stopRecording()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Text(
                    text = "${stringResource(R.string.action_record)}: ${currentStation.Name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            val infiniteLabel = stringResource(R.string.timer_infinite)
            val timerOptions = listOf(
                " 5 min" to 5 * 60 * 1000L,
                "15 min" to 15 * 60 * 1000L,
                "30 min" to 30 * 60 * 1000L,
                " 1 h" to 60 * 60 * 1000L,
                " 2 h" to 120 * 60 * 1000L,
                " $infiniteLabel" to 0L
            )

            timerOptions.forEach { (label, durationMs) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = AmaradioAmber) },
                    onClick = {
                        showTimerMenu = false
                        val station = currentStation ?: return@DropdownMenuItem
                        val liveState = recordManager.recordingState.value
                        if (liveState.isRecording && liveState.stationUuid != station.StationUuid) {
                            pendingReplaceRequest = Pair(durationMs, liveState.stationName)
                        } else if (!liveState.isRecording) {
                            requestRecordingNotificationPermissionIfNeeded(context)
                            recordManager.startRecording(station, durationMs)
                        }
                    }
                )
            }
        }
    }

    pendingReplaceRequest?.let { (durationMs, activeStationName) ->
        currentStation?.let { targetStation ->
            ReplaceRecordingWarningDialog(
                currentStationName = activeStationName,
                newStationName = targetStation.Name,
                onConfirm = {
                    pendingReplaceRequest = null
                    requestRecordingNotificationPermissionIfNeeded(context)
                    recordManager.replaceRecording(targetStation, durationMs)
                },
                onDismiss = {
                    pendingReplaceRequest = null
                }
            )
        }
    }
}

@Composable
fun ReplaceRecordingWarningDialog(
    currentStationName: String,
    newStationName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.dialog_replace_recording_title),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.dialog_replace_recording_message,
                    currentStationName,
                    newStationName
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.yes),
                    fontWeight = FontWeight.Bold,
                    color = AmaradioAmber
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.no),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

internal fun requestRecordingNotificationPermissionIfNeeded(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            (context as? android.app.Activity)?.let { activity ->
                androidx.core.app.ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
