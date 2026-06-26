package com.ounben.amaradio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
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
        title = { Text(stringResource(R.string.sleep_timer_title)) },
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
                    colors = SliderDefaults.colors(thumbColor = AmaradioAmber, activeTrackColor = AmaradioAmber)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                PlayerServiceUtil.clearTimer()
                PlayerServiceUtil.addTimer(sliderValue.toInt() * 60)
                sharedPref.edit { putInt("sleep_timer_default_minutes", sliderValue.toInt()) }
                onDismiss()
            }) {
                Text(stringResource(R.string.sleep_timer_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                PlayerServiceUtil.clearTimer()
                onDismiss()
            }) {
                Text(stringResource(R.string.sleep_timer_clear))
            }
        }
    )
}

@Composable
fun PlayerSelectorDialogCompose(
    station: DataRadioStation,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alert_select_player)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    leadingContent = { 
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    modifier = Modifier.clickable {
                        Utils.playAndWarnIfMetered(context, station, PlayerType.AMARadio) {
                            Utils.play(station)
                        }
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_play_in_external)) },
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
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
        title = { Text(stringResource(R.string.settings_proxy)) },
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
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = viewModel::onPortChange,
                    label = { Text(stringResource(R.string.port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = uiState.type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Proxy Type") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(Proxy.Type.DIRECT, Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
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
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.settings_proxy_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                        color = AmaradioAmber
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.save()
                onDismiss()
            }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.testProxy() }, enabled = !uiState.isTesting) {
                    Text(stringResource(R.string.settings_proxy_action_test))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
fun StationOptionsDialog(
    station: DataRadioStation,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(station.Name) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_station_visit_website)) },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.clickable {
                        StationActions.openStationHomeUrl(context, station)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_station_share)) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        StationActions.share(context, station)
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.detail_create_shortcut)) },
                    leadingContent = { Icon(Icons.Default.Shortcut, contentDescription = null) },
                    modifier = Modifier.clickable {
                        // Shortcut logic
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
