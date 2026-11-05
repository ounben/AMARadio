package com.ounben.amaradio.recorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    val allRecordings by db.recordDao().getAllRecordingsFlow().collectAsState(initial = emptyList())
    val recordedStations by db.recordDao().getRecordedStationNamesFlow().collectAsState(initial = emptyList())

    var selectedStation by rememberSaveable { mutableStateOf("") }
    var startDateMs by rememberSaveable { mutableLongStateOf(0L) }
    var endDateMs by rememberSaveable { mutableLongStateOf(0L) }

    val recordings by db.recordDao().getFilteredRecordingsFlow(
        stationName = selectedStation,
        startTimestamp = startDateMs,
        endTimestamp = endDateMs
    ).collectAsState(initial = emptyList())

    var recordingToPlay by remember { mutableStateOf<RecordEntity?>(null) }
    var recordingWithOptions by remember { mutableStateOf<RecordEntity?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordEntity?>(null) }

    // Auto-reconcile database with physical storage (cleans up externally deleted files)
    LaunchedEffect(allRecordings) {
        withContext(Dispatchers.IO) {
            allRecordings.forEach { record ->
                if (!isRecordingFileExists(context, record.fileUri)) {
                    android.util.Log.d("RecordingsTab", "Cleaning up externally deleted recording: ${record.fileUri}")
                    db.recordDao().deleteRecord(record.id)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Sticky Recording Filter Header
        RecordingFilterHeader(
            recordedStations = recordedStations,
            selectedStation = selectedStation,
            onStationSelected = { selectedStation = it },
            startDateMs = startDateMs,
            endDateMs = endDateMs,
            onDateRangeSelected = { start, end ->
                startDateMs = start
                endDateMs = end
            }
        )

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.searchpreference_no_results),
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
                    text = "${stringResource(R.string.action_delete)}: ${record.Name}?",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingFilterHeader(
    recordedStations: List<String>,
    selectedStation: String,
    onStationSelected: (String) -> Unit,
    startDateMs: Long,
    endDateMs: Long,
    onDateRangeSelected: (Long, Long) -> Unit
) {
    var showStationDropdown by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val dateRangeLabel = remember(startDateMs, endDateMs) {
        if (startDateMs > 0L && endDateMs > 0L) {
            val fmt = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            "${fmt.format(Date(startDateMs))} - ${fmt.format(Date(endDateMs))}"
        } else if (startDateMs > 0L) {
            val fmt = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            "From ${fmt.format(Date(startDateMs))}"
        } else {
            "All"
        }
    }

    Surface(
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Station Dropdown Filter (Links, exakt 50% der Zeilenbreite)
            Box(modifier = Modifier.weight(1f)) {
                FilterChip(
                    selected = selectedStation.isNotEmpty(),
                    onClick = { showStationDropdown = true },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = selectedStation.ifEmpty { "All" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = AmaradioAmber,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black,
                        selectedTrailingIconColor = Color.Black
                    )
                )

                DropdownMenu(
                    expanded = showStationDropdown,
                    onDismissRequest = { showStationDropdown = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("All", fontWeight = if (selectedStation.isEmpty()) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onStationSelected("")
                            showStationDropdown = false
                        }
                    )
                    if (recordedStations.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    }
                    recordedStations.forEach { stationName ->
                        DropdownMenuItem(
                            text = { Text(stationName, fontWeight = if (selectedStation == stationName) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                onStationSelected(stationName)
                                showStationDropdown = false
                            }
                        )
                    }
                }
            }

            // Von - Bis Date Range Filter Chip (Rechts, exakt 50% der Zeilenbreite)
            Box(modifier = Modifier.weight(1f)) {
                FilterChip(
                    selected = startDateMs > 0L,
                    onClick = { showDateRangePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = dateRangeLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (startDateMs > 0L) {
                            IconButton(
                                onClick = { onDateRangeSelected(0L, 0L) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.sleep_timer_clear), modifier = Modifier.size(14.dp), tint = Color.Black)
                            }
                        } else {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = AmaradioAmber,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black,
                        selectedTrailingIconColor = Color.Black
                    )
                )
            }
        }
    }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = if (startDateMs > 0L) startDateMs else null,
            initialSelectedEndDateMillis = if (endDateMs > 0L) endDateMs else null
        )

        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis ?: 0L
                        val rawEnd = dateRangePickerState.selectedEndDateMillis ?: start
                        val end = if (rawEnd > 0L) rawEnd + (24 * 60 * 60 * 1000L - 1) else 0L
                        onDateRangeSelected(start, end)
                        showDateRangePicker = false
                    }
                ) {
                    Text(stringResource(R.string.filter_apply), fontWeight = FontWeight.Bold, color = AmaradioAmber)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text(stringResource(R.string.label_button_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = AmaradioAmber,
                    selectedDayContentColor = Color.Black,
                    dayInSelectionRangeContainerColor = AmaradioAmber.copy(alpha = 0.2f)
                )
            )
        }
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
                    stationName = record.Name,
                    stationUuid = record.StationUuid,
                    iconUrl = record.Favicon.ifEmpty { null },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.Name,
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
                    text = record.Name,
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
                    headlineContent = { Text(stringResource(R.string.action_open_file_location), color = MaterialTheme.colorScheme.onSurface) },
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
        Toast.makeText(context, context.getString(R.string.error_cannot_open_file), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, context.getString(R.string.error_no_file_manager), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareRecordingFile(context: Context, record: RecordEntity) {
    try {
        val uri = Uri.parse(record.fileUri)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, record.Name)
            putExtra(Intent.EXTRA_TEXT, "${record.Name}\nRecording: ${record.format.uppercase()}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.action_station_share))
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_sharing), Toast.LENGTH_SHORT).show()
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
