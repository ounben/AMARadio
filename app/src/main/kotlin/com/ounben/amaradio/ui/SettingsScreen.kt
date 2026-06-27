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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenProxy: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onBatteryOptimize: () -> Unit,
    batterySummary: String
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    SingleTabContainer(titleRes = R.string.nav_item_settings) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance
            SettingsCategory(title = stringResource(R.string.settings_appearance)) {
                SettingsListPreference(
                    title = stringResource(R.string.settings_theme_selection_title),
                    currentValue = uiState.themeName,
                    entries = stringArrayResource(R.array.theme_entries),
                    entryValues = stringArrayResource(R.array.theme_values),
                    icon = Icons.Default.Monitor,
                    onValueChange = { viewModel.updateString("theme_name", it) }
                )
                SettingsListPreference(
                    title = stringResource(R.string.settings_ui_scale),
                    currentValue = uiState.uiScaleLevel,
                    entries = stringArrayResource(R.array.ui_scale_entries),
                    entryValues = stringArrayResource(R.array.ui_scale_values),
                    icon = Icons.Default.FormatSize,
                    onValueChange = { viewModel.updateString("ui_scale_level", it) }
                )
            }

            // Startup Behaviour
            SettingsCategory(title = stringResource(R.string.settings_startup_behaviour)) {
                SettingsListPreference(
                    title = stringResource(R.string.startup_action_title),
                    currentValue = uiState.startupAction,
                    entries = stringArrayResource(R.array.startup_action_entries),
                    entryValues = stringArrayResource(R.array.startup_action_entryvalues),
                    icon = Icons.Default.Home,
                    onValueChange = { viewModel.updateString("startup_action", it) }
                )
            }

            // Player
            SettingsCategory(title = stringResource(R.string.settings_play)) {
                SettingsSwitch(
                    title = stringResource(R.string.settings_play_external),
                    checked = uiState.playExternal,
                    icon = Icons.Default.Launch,
                    onCheckedChange = { viewModel.updateBoolean("play_external", it) }
                )
                SettingsSwitch(
                    title = stringResource(R.string.settings_warn_no_wifi),
                    checked = uiState.warnNoWifi,
                    icon = Icons.Default.WarningAmber,
                    onCheckedChange = { viewModel.updateBoolean("warn_no_wifi", it) }
                )
                SettingsSwitch(
                    title = stringResource(R.string.settings_pause_when_noisy),
                    checked = uiState.pauseWhenNoisy,
                    icon = Icons.Default.VolumeDown,
                    onCheckedChange = { viewModel.updateBoolean("pause_when_noisy", it) }
                )
                SettingsClickable(
                    title = stringResource(R.string.settings_equalizer),
                    icon = Icons.Default.Tune,
                    onClick = onOpenEqualizer
                )
            }

            // Connectivity
            SettingsCategory(title = stringResource(R.string.settings_connectivity)) {
                SettingsClickable(
                    title = stringResource(R.string.settings_proxy),
                    icon = Icons.Default.Lock,
                    onClick = onOpenProxy
                )
            }

            // Other
            SettingsCategory(title = stringResource(R.string.settings_other)) {
                SettingsClickable(
                    title = stringResource(R.string.settings_disable_battery_optimization),
                    summary = batterySummary,
                    icon = Icons.Default.BatteryChargingFull,
                    onClick = onBatteryOptimize
                )
                SettingsClickable(
                    title = stringResource(R.string.settings_statistics),
                    icon = Icons.Default.Poll,
                    onClick = onOpenStatistics
                )
                SettingsClickable(
                    title = stringResource(R.string.settings_about),
                    icon = Icons.Default.LiveHelp,
                    onClick = onOpenAbout
                )
            }
        }
    }
}

@Composable
fun SettingsCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickable(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsListPreference(
    title: String,
    currentValue: String,
    entries: Array<String>,
    entryValues: Array<String>,
    icon: ImageVector,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentIndex = entryValues.indexOf(currentValue).coerceAtLeast(0)
    val currentLabel = if (entries.isNotEmpty()) entries[currentIndex] else currentValue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = currentLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                Column {
                    entries.forEachIndexed { index, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onValueChange(entryValues[index])
                                    showDialog = false 
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (index == currentIndex),
                                onClick = null // handle via row click
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.label_button_cancel))
                }
            }
        )
    }
}
