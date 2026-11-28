package com.ounben.amaradio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.favorites.AssignFavoriteListsDialog
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.players.selector.PlayerType
import com.ounben.amaradio.recorder.formatAccessibleDuration
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import java.net.Proxy
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sharedPref = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val cur = PlayerServiceUtil.getTimerSeconds()
    
    val minMinutes = 1
    val maxMinutes = 120

    var sliderValue by remember {
        mutableStateOf(
            (if (cur <= 0) sharedPref.getInt("sleep_timer_default_minutes", 10)
            else if (cur < 60) minMinutes else (cur / 60).toInt())
                .coerceIn(minMinutes, maxMinutes)
                .toFloat()
        )
    }

    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()
    }
    val totalMinutes = sliderValue.roundToInt()
    val spokenMinutes = remember(totalMinutes, locale) {
        try {
            MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)
                .format(Measure(totalMinutes, MeasureUnit.MINUTE))
        } catch (_: Exception) {
            "$totalMinutes min"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.sleep_timer_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalMinutes min",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = spokenMinutes
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it.roundToInt().toFloat() },
                    valueRange = minMinutes.toFloat()..maxMinutes.toFloat(),
                    steps = (maxMinutes - minMinutes - 1).coerceAtLeast(0),
                    modifier = Modifier.semantics {
                        stateDescription = spokenMinutes
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = AmaradioAmber,
                        activeTrackColor = AmaradioAmber,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = remember {
                        listOf(
                            listOf("5 min" to 5f, "15 min" to 15f, "30 min" to 30f),
                            listOf("45 min" to 45f, "1 h" to 60f, "2 h" to 120f)
                        )
                    }

                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (label, minutes) ->
                                val isSelected = totalMinutes == minutes.toInt()
                                val spokenLabel = remember(minutes, locale) {
                                    formatAccessibleDuration(minutes.toLong() * 60_000L, locale)
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { sliderValue = minutes },
                                    label = {
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clearAndSetSemantics {
                                                    contentDescription = spokenLabel
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        selectedContainerColor = AmaradioAmber,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    PlayerServiceUtil.clearTimer()
                    PlayerServiceUtil.addTimer(totalMinutes * 60)
                    sharedPref.edit { putInt("sleep_timer_default_minutes", totalMinutes) }
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
            ) {
                Text(stringResource(R.string.sleep_timer_apply), fontWeight = FontWeight.Bold, color = AmaradioAmber)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    PlayerServiceUtil.clearTimer()
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.sleep_timer_clear), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun PlayerSelectorDialogCompose(
    station: DataRadioStation,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.alert_select_player), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { 
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = AmaradioAmber
                        ) 
                    },
                    modifier = Modifier.clickable {
                        Utils.playAndWarnIfMetered(context, station, PlayerType.AMARadio) {
                            playerViewModel.play(station)
                        }
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_play_in_external), color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { 
                        Icon(
                            Icons.Default.PlayArrow, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    modifier = Modifier.clickable {
                        Utils.playAndWarnIfMetered(context, station, PlayerType.EXTERNAL) {
                            PlayStationTask.playExternal(station, context).execute()
                        }
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsDialogCompose(onDismiss: () -> Unit) {
    val viewModel: ProxyViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.settings_proxy), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.host,
                    onValueChange = viewModel::onHostChange,
                    label = { Text(stringResource(R.string.hostname)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmaradioAmber,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = viewModel::onPortChange,
                    label = { Text(stringResource(R.string.port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmaradioAmber,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = uiState.type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Proxy Type") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmaradioAmber,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
                    DropdownMenu(
                        expanded = expanded, 
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        listOf(Proxy.Type.DIRECT, Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.onTypeChange(type)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.login,
                    onValueChange = viewModel::onLoginChange,
                    label = { Text(stringResource(R.string.settings_proxy_login)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmaradioAmber,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.settings_proxy_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmaradioAmber,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (uiState.testResult.isNotEmpty()) {
                    Text(
                        text = uiState.testResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                if (uiState.isTesting) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AmaradioAmber,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.save()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
            ) {
                Text(stringResource(R.string.action_ok), fontWeight = FontWeight.Bold, color = AmaradioAmber)
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { viewModel.testProxy() },
                    enabled = !uiState.isTesting,
                    colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
                ) {
                    Text(stringResource(R.string.settings_proxy_action_test), fontWeight = FontWeight.Bold, color = AmaradioAmber)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
fun StationOptionsDialog(
    station: DataRadioStation,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    activePlaylistName: String? = null,
    onRemoveFromPlaylistClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val showRecorder = remember(context) {
        try {
            context.resources.getBoolean(R.bool.show_recorder_button)
        } catch (_: Exception) {
            false
        }
    }
    var showRecordTimerDialog by remember { mutableStateOf(false) }
    var showAssignPlaylistsDialog by remember { mutableStateOf(false) }
    var showStationGainDialog by remember { mutableStateOf(false) }
    var pendingReplaceRequest by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val isDark = isSystemInDarkTheme()
    val recordRedColor = if (isDark) SolidRedError else SolidRedErrorLight
    val recordManager = remember { com.ounben.amaradio.recorder.RecordManager.getInstance(context) }
    val recordState by recordManager.recordingState.collectAsStateWithLifecycle()
    val isThisStationRecording = recordState.isRecording && recordState.stationUuid == station.StationUuid
    val stationAudioManager = remember(context) {
        (context.applicationContext as com.ounben.amaradio.AMARadioApp).stationAudioManager
    }
    val stationGains by stationAudioManager.stationGains.collectAsStateWithLifecycle()
    val stationManualGainDb = stationGains[station.StationUuid] ?: 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(station.Name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ListItem(
                    headlineContent = { 
                        Text(
                            text = stringResource(R.string.detail_play),
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    leadingContent = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = AmaradioAmber) },
                    modifier = Modifier.clickable {
                        StationActions.playInAMARadio(context, station)
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
                ListItem(
                    headlineContent = { 
                        Text(
                            text = stringResource(R.string.action_play_in_external),
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        Utils.playAndWarnIfMetered(context, station, PlayerType.EXTERNAL) {
                            PlayStationTask.playExternal(station, context).execute()
                        }
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
                if (showRecorder) {
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = if (isThisStationRecording) stringResource(R.string.label_button_cancel) else stringResource(R.string.action_record),
                                color = if (isThisStationRecording) recordRedColor else MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        leadingContent = { 
                            Icon(
                                imageVector = if (isThisStationRecording) Icons.Filled.Stop else Icons.Filled.RadioButtonChecked, 
                                contentDescription = null, 
                                tint = recordRedColor
                            ) 
                        },
                        modifier = Modifier.clickable {
                            if (isThisStationRecording) {
                                recordManager.stopRecording()
                                onDismiss()
                            } else {
                                showRecordTimerDialog = true
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
                ListItem(
                    headlineContent = { 
                        Text(
                            text = stringResource(if (isFavorite) R.string.action_starred_remove else R.string.action_starred_add),
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    leadingContent = { 
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, 
                            contentDescription = null,
                            tint = if (isFavorite) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    modifier = Modifier.clickable {
                        onFavoriteClick()
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.action_manage_station_playlists),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        showAssignPlaylistsDialog = true
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
                if (activePlaylistName != null && onRemoveFromPlaylistClick != null) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.action_remove_from_playlist, activePlaylistName),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.PlaylistRemove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable {
                            onRemoveFromPlaylistClick()
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.action_station_gain),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = if (kotlin.math.abs(stationManualGainDb) >= 0.01f) {
                        {
                            Text(
                                text = com.ounben.amaradio.audio.StationAudioManager.formatGainDb(stationManualGainDb),
                                color = AmaradioAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else null,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (kotlin.math.abs(stationManualGainDb) >= 0.01f) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        showStationGainDialog = true
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
                ListItem(
                    headlineContent = { 
                        Text(
                            text = stringResource(R.string.action_station_visit_website),
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        StationActions.openStationHomeUrl(context, station)
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
                ListItem(
                    headlineContent = { 
                        Text(
                            text = stringResource(R.string.action_station_share),
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        StationActions.share(context, station)
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
                if (onDeleteClick != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error
                            ) 
                        },
                        leadingContent = { Icon(painter = painterResource(R.drawable.delete_24), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            onDeleteClick()
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )

    if (showRecordTimerDialog) {
        val configuration = LocalConfiguration.current
        val locale = remember(configuration) {
            configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()
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

        AlertDialog(
            onDismissRequest = { showRecordTimerDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.action_record), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    timerOptions.forEach { (label, durationMs) ->
                        val spokenLabel = remember(durationMs, infiniteLabel, locale) {
                            if (durationMs > 0L) formatAccessibleDuration(durationMs, locale) else infiniteLabel
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = spokenLabel
                                    }
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Timer, contentDescription = null, tint = AmaradioAmber) },
                            modifier = Modifier.clickable {
                                showRecordTimerDialog = false
                                val liveState = recordManager.recordingState.value
                                if (liveState.isRecording && liveState.stationUuid != station.StationUuid) {
                                    pendingReplaceRequest = Pair(durationMs, liveState.stationName)
                                } else if (!liveState.isRecording) {
                                    com.ounben.amaradio.recorder.requestRecordingNotificationPermissionIfNeeded(context)
                                    recordManager.startRecording(station, durationMs)
                                    onDismiss()
                                } else {
                                    onDismiss()
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecordTimerDialog = false }) {
                    Text(stringResource(R.string.label_button_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showAssignPlaylistsDialog) {
        AssignFavoriteListsDialog(
            station = station,
            onDismiss = {
                showAssignPlaylistsDialog = false
                onDismiss()
            }
        )
    }

    if (showStationGainDialog) {
        StationGainDialog(
            station = station,
            onDismiss = {
                showStationGainDialog = false
            }
        )
    }

    pendingReplaceRequest?.let { (durationMs, activeStationName) ->
        com.ounben.amaradio.recorder.ReplaceRecordingWarningDialog(
            currentStationName = activeStationName,
            newStationName = station.Name,
            onConfirm = {
                pendingReplaceRequest = null
                com.ounben.amaradio.recorder.requestRecordingNotificationPermissionIfNeeded(context)
                recordManager.replaceRecording(station, durationMs)
                onDismiss()
            },
            onDismiss = {
                pendingReplaceRequest = null
            }
        )
    }
}

@Composable
fun TrackOptionsDialog(
    track: TrackHistoryEntry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val trackInfo = if (track.artist.isNotEmpty() && track.track.isNotEmpty()) {
        "${track.artist} - ${track.track}"
    } else {
        track.title.ifEmpty { track.track }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.tab_player_history), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    text = trackInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                ListItem(
                    headlineContent = { 
                        Text(
                            text = stringResource(R.string.action_copy_info),
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null) {
                            val clip = ClipData.newPlainText("Track info", trackInfo)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, R.string.notify_track_info_copied, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.settings_disable_battery_optimization),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_disable_battery_optimization_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
            ) {
                Text(
                    text = stringResource(R.string.battery_opt_open_settings),
                    color = AmaradioAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(
                    text = stringResource(R.string.battery_opt_later),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
