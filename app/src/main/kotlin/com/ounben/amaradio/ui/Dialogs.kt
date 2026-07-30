package com.ounben.amaradio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.players.selector.PlayerType
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import java.net.Proxy

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sharedPref = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val cur = PlayerServiceUtil.getTimerSeconds()
    
    var sliderValue by remember { 
        mutableStateOf(if (cur <= 0) sharedPref.getInt("sleep_timer_default_minutes", 10).toFloat() 
                       else (if (cur < 60) 1f else (cur / 60).toFloat())) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.sleep_timer_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val totalMinutes = sliderValue.toInt()
                Text(
                    text = stringResource(R.string.sleep_timer, totalMinutes / 60, totalMinutes % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..120f,
                    steps = 119,
                    colors = SliderDefaults.colors(
                        thumbColor = AmaradioAmber,
                        activeTrackColor = AmaradioAmber,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    PlayerServiceUtil.clearTimer()
                    PlayerServiceUtil.addTimer(sliderValue.toInt() * 60)
                    sharedPref.edit { putInt("sleep_timer_default_minutes", sliderValue.toInt()) }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.sleep_timer_apply), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    PlayerServiceUtil.clearTimer()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.sleep_timer_clear), fontWeight = FontWeight.Bold)
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
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsDialogCompose(onDismiss: () -> Unit) {
    val viewModel: ProxyViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.settings_proxy), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
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
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.save()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_ok), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                Button(
                    onClick = { viewModel.testProxy() }, 
                    enabled = !uiState.isTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.settings_proxy_action_test), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(station.Name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (onDeleteClick != null) {
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error
                            ) 
                        },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            onDeleteClick()
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
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
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
}
