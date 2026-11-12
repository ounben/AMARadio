package com.ounben.amaradio.ui

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.LiveHelp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
private data class CountryJson(
    val name: String? = null,
    val code: String? = null
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    serverInfoViewModel: ServerInfoViewModel,
    onOpenProxy: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onBatteryOptimize: () -> Unit,
    onRateApp: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverInfoUiState by serverInfoViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    fun checkBatteryIgnoring(): Boolean {
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    var isIgnoringBatteryOpt by remember { mutableStateOf(checkBatteryIgnoring()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isIgnoringBatteryOpt = checkBatteryIgnoring()
    }

    SingleTabContainer(titleRes = R.string.nav_item_settings) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!uiState.isReviewCompleted || !isIgnoringBatteryOpt) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!uiState.isReviewCompleted) {
                        val app = LocalContext.current.applicationContext as AMARadioApp
                        SettingsClickable(
                            title = stringResource(app.reviewManager.getLabelRes()),
                            summary = stringResource(app.reviewManager.getSummaryRes()),
                            icon = Icons.Default.Star,
                            verticalPadding = 4.dp,
                            onClick = onRateApp
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }

                    if (!isIgnoringBatteryOpt) {
                        val isDark = isSystemInDarkTheme()
                        val batteryIconTint = if (isDark) SolidRedError else SolidRedErrorLight
                        SettingsClickable(
                            title = stringResource(R.string.settings_disable_battery_optimization),
                            summary = stringResource(R.string.settings_disable_battery_optimization_desc),
                            icon = Icons.Outlined.WarningAmber,
                            iconTint = batteryIconTint,
                            iconSize = 32.dp,
                            verticalPadding = 4.dp,
                            onClick = onBatteryOptimize
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }
                }
            }

            // Database info
            SettingsCategory(title = stringResource(R.string.database_summary_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoRow(stringResource(R.string.database_last_sync), serverInfoUiState.lastSyncTime)
                    InfoRow(stringResource(R.string.database_total_stations), "${serverInfoUiState.localStationCount}")

                    if (serverInfoUiState.isSyncing) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.database_syncing),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = AmaradioAmber
                            )
                            if (serverInfoUiState.syncProgressText.isNotEmpty()) {
                                Text(
                                    text = serverInfoUiState.syncProgressText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = AmaradioAmber,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }

                    if (serverInfoUiState.error != null) {
                        Text(
                            text = serverInfoUiState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    SettingsSwitch(
                        title = stringResource(R.string.settings_auto_db_update),
                        summary = stringResource(R.string.settings_auto_db_update_desc),
                        checked = uiState.autoDbUpdate,
                        icon = Icons.Default.CloudSync,
                        onCheckedChange = { viewModel.updateBoolean("settings_auto_db_update", it) }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { serverInfoViewModel.triggerFullUpdate() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !serverInfoUiState.isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = AmaradioAmber)
                    ) {
                        if (serverInfoUiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (serverInfoUiState.isSyncing) stringResource(R.string.database_syncing) else stringResource(R.string.database_full_update))
                    }
                }
            }

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
                SettingsListPreference(
                    title = stringResource(R.string.settings_language),
                    currentValue = uiState.language,
                    entries = stringArrayResource(R.array.language_entries),
                    entryValues = stringArrayResource(R.array.language_values),
                    icon = Icons.Default.Language,
                    onValueChange = { viewModel.updateString("settings_language", it) }
                )

                // Local Country Preference
                var showCountryDialog by remember { mutableStateOf(false) }
                val currentCountryCode = uiState.localCountry
                val currentCountryDisplay = if (currentCountryCode == "SYSTEM") {
                    stringResource(R.string.theme_system)
                } else {
                    val flag = com.ounben.amaradio.utils.EmojiUtils.getFlagEmoji(currentCountryCode) ?: ""
                    val countryName = com.ounben.amaradio.CountryCodeDictionary.instance.getCountryByCode(currentCountryCode) ?: currentCountryCode
                    if (flag.isNotEmpty()) "$flag $countryName ($currentCountryCode)" else "$countryName ($currentCountryCode)"
                }

                SettingsClickable(
                    title = stringResource(R.string.filter_country),
                    summary = currentCountryDisplay,
                    icon = Icons.Default.Public,
                    onClick = { showCountryDialog = true }
                )

                if (showCountryDialog) {
                    val systemLabel = stringResource(R.string.theme_system)
                    val countryOptions = remember(systemLabel) {
                        val list = mutableListOf(
                            FilterViewModel.CategoryItem("SYSTEM", systemLabel, emoji = "🌐", count = 0)
                        )
                        val app = context.applicationContext as AMARadioApp
                        try {
                            val inputStream = app.resources.openRawResource(R.raw.countries)
                            val jsonContent = inputStream.bufferedReader().use { it.readText() }
                            val json = Json { ignoreUnknownKeys = true }
                            val countries = json.decodeFromString<List<CountryJson>>(jsonContent)
                            val categories = countries.mapNotNull { item ->
                                val code = item.code?.uppercase(Locale.ROOT) ?: return@mapNotNull null
                                val rawName = item.name ?: code
                                val label = com.ounben.amaradio.CountryCodeDictionary.instance.getCountryByCode(code) ?: rawName
                                val emoji = com.ounben.amaradio.utils.EmojiUtils.getFlagEmoji(code) ?: ""
                                FilterViewModel.CategoryItem(code, label, emoji = emoji, count = 0)
                            }.sortedBy { it.label }
                            list.addAll(categories)
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsScreen", "Error parsing countries", e)
                        }
                        list
                    }
                    SearchableSelectionDialog(
                        title = stringResource(R.string.filter_country),
                        options = countryOptions,
                        showCount = false,
                        onSelect = { item ->
                            viewModel.updateString("settings_manual_country_code", item.code)
                            showCountryDialog = false
                        },
                        onDismiss = { showCountryDialog = false }
                    )
                }
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
                    icon = Icons.AutoMirrored.Filled.Launch,
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
                    icon = Icons.Default.Headphones,
                    onCheckedChange = { viewModel.updateBoolean("pause_when_noisy", it) }
                )
                SettingsSwitch(
                    title = stringResource(R.string.settings_remove_notification_on_stop),
                    checked = uiState.removeNotificationOnStop,
                    icon = Icons.Default.NotificationsOff,
                    onCheckedChange = { viewModel.updateBoolean("settings_remove_notification_on_stop", it) }
                )
                SettingsSwitch(
                    title = stringResource(R.string.settings_auto_resume_audio_focus),
                    checked = uiState.autoResumeAudioFocus,
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onCheckedChange = { viewModel.updateBoolean("settings_auto_resume_audio_focus", it) }
                )
            }

            // Backup & Restore
            SettingsCategory(title = stringResource(R.string.settings_backup_restore)) {
                SettingsClickable(
                    title = stringResource(R.string.settings_export_backup),
                    summary = stringResource(R.string.settings_export_backup_desc),
                    icon = Icons.Default.CloudUpload,
                    onClick = onExportBackup
                )
                SettingsClickable(
                    title = stringResource(R.string.settings_import_backup),
                    summary = stringResource(R.string.settings_import_backup_desc),
                    icon = Icons.Default.CloudDownload,
                    onClick = onImportBackup
                )
            }

            // Other
            SettingsCategory(title = stringResource(R.string.settings_other)) {
                SettingsClickable(
                    title = stringResource(R.string.settings_statistics),
                    icon = Icons.Default.Poll,
                    onClick = onOpenStatistics
                )
                SettingsClickable(
                    title = stringResource(R.string.settings_about),
                    icon = Icons.AutoMirrored.Filled.LiveHelp,
                    onClick = onOpenAbout
                )

                SettingsSwitch(
                    title = stringResource(R.string.settings_station_click_counter),
                    summary = stringResource(R.string.settings_station_click_counter_desc),
                    checked = uiState.stationClickCounter,
                    icon = Icons.Default.Analytics,
                    onCheckedChange = { viewModel.updateBoolean("settings_station_click_counter", it) }
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
    summary: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrBlank()) {
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickable(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 24.dp,
    verticalPadding: Dp = 12.dp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrBlank()) {
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
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!currentLabel.isNullOrBlank()) {
                Text(text = currentLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { 
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = {
                // LazyColumn with a fixed max height to ensure scrollability in the dialog
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    itemsIndexed(entries) { index, label ->
                        // Only show non-empty entries and ensure we have a corresponding value
                        if (label.trim().isNotEmpty() && index < entryValues.size) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        onValueChange(entryValues[index])
                                        showDialog = false 
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (index == currentIndex),
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AmaradioAmber,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}
