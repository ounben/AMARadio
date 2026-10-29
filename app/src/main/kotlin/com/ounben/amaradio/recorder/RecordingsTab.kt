package com.ounben.amaradio.recorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.SolidRedError
import com.ounben.amaradio.ui.StationIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingsTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AMARadioUserDatabase.getDatabase(context) }
    val recordings by db.recordDao().getAllRecordingsFlow().collectAsState(initial = emptyList())
    var recordingToPlay by remember { mutableStateOf<RecordEntity?>(null) }
    var recordingWithOptions by remember { mutableStateOf<RecordEntity?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordEntity?>(null) }

    // Auto-reconcile database with physical storage (cleans up externally deleted files)
    LaunchedEffect(recordings) {
        withContext(Dispatchers.IO) {
            recordings.forEach { record ->
                if (!isRecordingFileExists(context, record.fileUri)) {
                    android.util.Log.d("RecordingsTab", "Cleaning up externally deleted recording: ${record.fileUri}")
                    db.recordDao().deleteRecord(record.id)
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recordings found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(recordings, key = { it.id }) { record ->
                    Column {
                        RecordingListItem(
                            record = record,
                            onPlay = { recordingToPlay = record },
                            onLongClick = { recordingWithOptions = record }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    // Floating Standalone Recording Player Dialog
    recordingToPlay?.let { record ->
        RecordingPlayerDialog(
            record = record,
            onDismiss = { recordingToPlay = null }
        )
    }

    recordingWithOptions?.let { record ->
        RecordingOptionsDialog(
            record = record,
            onPlay = { recordingToPlay = record },
            onPlayExternal = { openRecordingFile(context, record.fileUri) },
            onOpenFolder = { openRecordingFolder(context, record.fileUri) },
            onShare = { shareRecordingFile(context, record) },
            onDeleteClick = { recordingToDelete = record },
            onDismiss = { recordingWithOptions = null }
        )
    }

    recordingToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.action_delete),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Delete recording: ${record.stationName}?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                context.contentResolver.delete(Uri.parse(record.fileUri), null, null)
                            } catch (_: Exception) {}
                            db.recordDao().deleteRecord(record.id)
                            withContext(Dispatchers.Main) { recordingToDelete = null }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SolidRedError)
                ) {
                    Text(
                        text = stringResource(R.string.yes),
                        fontWeight = FontWeight.Bold,
                        color = SolidRedError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text(
                        text = stringResource(R.string.no),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingListItem(
    record: RecordEntity,
    onPlay: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AMARadioUserDatabase.getDatabase(context) }
    var expanded by remember { mutableStateOf(false) }
    val tracks by db.recordDao().getTracksForRecordingFlow(record.id).collectAsState(initial = emptyList())

    val dateStr = remember(record.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val durationStr = remember(record.durationMs) { formatDuration(record.durationMs) }
    val sizeStr = remember(record.fileSizeBytes) { Formatter.formatShortFileSize(context, record.fileSizeBytes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                StationIcon(
                    stationName = record.stationName,
                    stationUuid = record.stationUuid,
                    iconUrl = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.stationName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$dateStr • $durationStr • $sizeStr (${record.format.uppercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (tracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tracks (${tracks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmaradioAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = AmaradioAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = AmaradioAmber,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded && tracks.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 64.dp, top = 6.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tracks.forEach { track ->
                    Text(
                        text = "${formatDuration(track.offsetMs)} - ${track.trackTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingOptionsDialog(
    record: RecordEntity,
    onPlay: () -> Unit,
    onPlayExternal: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = remember(record.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val durationStr = remember(record.durationMs) { formatDuration(record.durationMs) }
    val sizeStr = remember(record.fileSizeBytes) { Formatter.formatShortFileSize(context, record.fileSizeBytes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    text = record.stationName,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$dateStr • $durationStr • $sizeStr (${record.format.uppercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.detail_play), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = AmaradioAmber) },
                    modifier = Modifier.clickable {
                        onPlay()
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_play_in_external), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        onPlayExternal()
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )

                ListItem(
                    headlineContent = { Text("Open file location", color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        onOpenFolder()
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_station_share), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        onShare()
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_delete), color = SolidRedError) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = SolidRedError) },
                    modifier = Modifier.clickable {
                        onDeleteClick()
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private fun openRecordingFile(context: Context, fileUriStr: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(fileUriStr), "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot open recording file", Toast.LENGTH_SHORT).show()
    }
}

private fun openRecordingFolder(context: Context, fileUriStr: String) {
    try {
        val uri = Uri.parse(fileUriStr)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(fileUriStr), "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open file manager", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareRecordingFile(context: Context, record: RecordEntity) {
    try {
        val uri = Uri.parse(record.fileUri)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, record.stationName)
            putExtra(Intent.EXTRA_TEXT, "${record.stationName}\nRecording: ${record.format.uppercase()}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.action_station_share))
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, "Error sharing recording", Toast.LENGTH_SHORT).show()
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun isRecordingFileExists(context: Context, fileUriStr: String): Boolean {
    return try {
        val uri = Uri.parse(fileUriStr)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd != null) {
            pfd.close()
            true
        } else false
    } catch (_: Exception) {
        false
    }
}
