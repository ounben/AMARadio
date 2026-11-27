package com.ounben.amaradio.recorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ounben.amaradio.R
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.BindScrollToTop
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

    val allRecordings by db.recordDao().getAllRecordingsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val recordedStations by db.recordDao().getRecordedStationNamesFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedStation by rememberSaveable { mutableStateOf<String?>(null) }
    var startDateMs by rememberSaveable { mutableLongStateOf(0L) }
    var endDateMs by rememberSaveable { mutableLongStateOf(0L) }

    val recordings by db.recordDao().getFilteredRecordingsFlow(
        stationName = selectedStation,
        startTimestamp = startDateMs,
        endTimestamp = endDateMs
    ).collectAsStateWithLifecycle(initialValue = emptyList())

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

        val listState = rememberLazyListState()
        val firstItemFocusRequester = remember { FocusRequester() }

        BindScrollToTop(
            listState = listState,
            firstItemFocusRequester = firstItemFocusRequester,
            enabled = recordings.isNotEmpty()
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
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(recordings, key = { _, it -> it.id }) { index, record ->
                        Column {
                            RecordingListItem(
                                record = record,
                                onPlay = { recordingToPlay = record },
                                onLongClick = { recordingWithOptions = record },
                                focusRequester = if (index == 0) firstItemFocusRequester else null
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
            onOpenFolder = { openRecordingFolder(context, record) },
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
private enum class DatePickerType { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingFilterHeader(
    recordedStations: List<String>,
    selectedStation: String?,
    onStationSelected: (String?) -> Unit,
    startDateMs: Long,
    endDateMs: Long,
    onDateRangeSelected: (Long, Long) -> Unit
) {
    var showStationDropdown by remember { mutableStateOf(false) }
    var activeDatePicker by remember { mutableStateOf<DatePickerType?>(null) }

    val allStationsLabel = stringResource(R.string.filter_all_stations)
    val anyDateLabel = stringResource(R.string.filter_date_any)
    val fromLabel = stringResource(R.string.filter_date_from)
    val toLabel = stringResource(R.string.filter_date_to)

    fun formatShortDate(millis: Long): String {
        if (millis <= 0L) return ""
        return try {
            val instant = java.time.Instant.ofEpochMilli(millis)
            val localDate = instant.atZone(java.time.ZoneOffset.UTC).toLocalDate()
            localDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy"))
        } catch (_: Exception) {
            val fmt = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            fmt.format(Date(millis))
        }
    }

    val isStationFiltered = !selectedStation.isNullOrBlank()
    val displayStationName = selectedStation?.takeIf { it.isNotBlank() } ?: allStationsLabel
    val formattedStartDate = remember(startDateMs) { formatShortDate(startDateMs) }
    val formattedEndDate = remember(endDateMs) { formatShortDate(endDateMs) }

    val startChipText = if (startDateMs > 0L) formattedStartDate else fromLabel
    val endChipText = if (endDateMs > 0L) formattedEndDate else toLabel

    val stationSemanticsDesc = stringResource(R.string.accessibility_filter_station, displayStationName)
    val startSemanticsDesc = stringResource(
        R.string.accessibility_filter_date_from,
        if (startDateMs > 0L) formattedStartDate else anyDateLabel
    )
    val endSemanticsDesc = stringResource(
        R.string.accessibility_filter_date_to,
        if (endDateMs > 0L) formattedEndDate else anyDateLabel
    )

    Surface(
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Zeile 1: Sender Dropdown Filter & Immer sichtbarer X-Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FilterChip(
                        selected = isStationFiltered,
                        onClick = { showStationDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                contentDescription = stationSemanticsDesc
                            },
                        label = {
                            Text(
                                text = displayStationName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clearAndSetSemantics { }
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
                            text = {
                                Text(
                                    text = allStationsLabel,
                                    fontWeight = if (!isStationFiltered) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onStationSelected(null)
                                showStationDropdown = false
                            }
                        )
                        if (recordedStations.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                        }
                        recordedStations.forEach { stationName ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stationName,
                                        fontWeight = if (selectedStation == stationName) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onStationSelected(stationName)
                                    showStationDropdown = false
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onStationSelected(null) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear_station_filter),
                        modifier = Modifier.size(18.dp),
                        tint = if (isStationFiltered) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // Zeile 2: Datumsbereich Filter (Von & Bis + Immer sichtbarer X-Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = startDateMs > 0L,
                    onClick = { activeDatePicker = DatePickerType.START },
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            contentDescription = startSemanticsDesc
                        },
                    label = {
                        Text(
                            text = startChipText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = AmaradioAmber,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black
                    )
                )

                FilterChip(
                    selected = endDateMs > 0L,
                    onClick = { activeDatePicker = DatePickerType.END },
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            contentDescription = endSemanticsDesc
                        },
                    label = {
                        Text(
                            text = endChipText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = AmaradioAmber,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black
                    )
                )

                IconButton(
                    onClick = { onDateRangeSelected(0L, 0L) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear_date_filter),
                        modifier = Modifier.size(18.dp),
                        tint = if (startDateMs > 0L || endDateMs > 0L) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }

    if (activeDatePicker != null) {
        val isStart = activeDatePicker == DatePickerType.START
        val currentMillis = if (isStart) {
            if (startDateMs > 0L) startDateMs else System.currentTimeMillis()
        } else {
            if (endDateMs > 0L) endDateMs else System.currentTimeMillis()
        }

        val selectableDates = remember(isStart, startDateMs, endDateMs) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return if (isStart) {
                        if (endDateMs > 0L) utcTimeMillis <= endDateMs else true
                    } else {
                        if (startDateMs > 0L) utcTimeMillis >= startDateMs else true
                    }
                }
                override fun isSelectableYear(year: Int): Boolean = year in 2000..2100
            }
        }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentMillis,
            initialDisplayMode = DisplayMode.Picker,
            yearRange = 2000..2100,
            selectableDates = selectableDates
        )

        val customDatePickerColors = DatePickerDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedDayContainerColor = AmaradioAmber,
            selectedDayContentColor = Color.Black,
            todayDateBorderColor = AmaradioAmber,
            todayContentColor = AmaradioAmber,
            currentYearContentColor = AmaradioAmber,
            selectedYearContainerColor = AmaradioAmber,
            selectedYearContentColor = Color.Black,
            dayContentColor = MaterialTheme.colorScheme.onSurface,
            headlineContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            yearContentColor = MaterialTheme.colorScheme.onSurface,
            dateTextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmaradioAmber,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = AmaradioAmber,
                cursorColor = AmaradioAmber,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        DatePickerDialog(
            onDismissRequest = { activeDatePicker = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            val localDate = java.time.Instant.ofEpochMilli(selected)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                            val startOfDayMs = localDate.atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                            val endOfDayMs = localDate.atTime(java.time.LocalTime.MAX)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()

                            if (isStart) {
                                val newEnd = if (endDateMs > 0L && endDateMs < startOfDayMs) endOfDayMs else endDateMs
                                onDateRangeSelected(startOfDayMs, newEnd)
                            } else {
                                val newStart = if (startDateMs > 0L && startDateMs > endOfDayMs) startOfDayMs else startDateMs
                                onDateRangeSelected(newStart, endOfDayMs)
                            }
                        }
                        activeDatePicker = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
                ) {
                    Text(stringResource(R.string.filter_apply), fontWeight = FontWeight.Bold, color = AmaradioAmber)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeDatePicker = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(stringResource(R.string.label_button_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            tonalElevation = 0.dp
        ) {
            DatePicker(
                state = datePickerState,
                colors = customDatePickerColors
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingListItem(
    record: RecordEntity,
    onPlay: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val db = remember { AMARadioUserDatabase.getDatabase(context) }
    var expanded by remember { mutableStateOf(false) }
    val tracks by db.recordDao().getTracksForRecordingFlow(record.id).collectAsStateWithLifecycle(initialValue = emptyList())

    val dateStr = remember(record.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val durationStr = remember(record.durationMs) { formatDuration(record.durationMs) }
    val sizeStr = remember(record.fileSizeBytes) { Formatter.formatShortFileSize(context, record.fileSizeBytes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
                    text = "$dateStr • $durationStr • $sizeStr (${record.format.uppercase(Locale.ROOT)})",
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
                    contentDescription = "${stringResource(R.string.action_play_recording)}: ${record.Name}",
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
                    text = "$dateStr • $durationStr • $sizeStr (${record.format.uppercase(Locale.ROOT)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_play_recording), color = MaterialTheme.colorScheme.onSurface) },
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
                    leadingContent = { Icon(painter = painterResource(R.drawable.delete_24), contentDescription = null, tint = SolidRedError) },
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

private fun openRecordingFolder(context: Context, record: RecordEntity) {
    val sanitizedStation = record.Name.replace(Regex("""[\\/:*?"<>|.]"""), "_").trim().ifEmpty { "Radio_Station" }
    val folderPath = "${Environment.DIRECTORY_MUSIC}/AMARadio/$sanitizedStation"

    // 1. Primary Method: Standard System File Explorer (com.android.externalstorage.documents)
    try {
        val treeUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$folderPath"
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {}

    // 2. Fallback: System Documents Provider (vnd.android.document/directory)
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(record.fileUri), "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {}

    // 3. Fallback: OEM File Manager Intent (resource/folder)
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(record.fileUri), "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {}

    Toast.makeText(context, context.getString(R.string.error_no_file_manager), Toast.LENGTH_SHORT).show()
}

private fun shareRecordingFile(context: Context, record: RecordEntity) {
    try {
        val uri = Uri.parse(record.fileUri)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, record.Name)
            putExtra(Intent.EXTRA_TEXT, "${record.Name}\nRecording: ${record.format.uppercase(Locale.ROOT)}")
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
