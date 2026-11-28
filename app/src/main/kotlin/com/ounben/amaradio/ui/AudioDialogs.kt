package com.ounben.amaradio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.audio.InAppEqualizerManager
import com.ounben.amaradio.audio.StationAudioManager
import com.ounben.amaradio.players.exoplayer.VolumeNormalizationAudioProcessor
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StationGainDialog(
    station: DataRadioStation,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val stationAudioManager = remember(context) {
        (context.applicationContext as AMARadioApp).stationAudioManager
    }
    val stationGains by stationAudioManager.stationGains.collectAsStateWithLifecycle()
    val currentGainDb = stationGains[station.StationUuid] ?: stationAudioManager.getManualGainDb(station.StationUuid)

    var sliderValue by remember(station.StationUuid) { mutableFloatStateOf(currentGainDb) }
    val formattedGain = remember(sliderValue) { StationAudioManager.formatGainDb(sliderValue) }
    val presets = remember { listOf(-6f, -3f, 0f, 3f, 6f) }

    fun applyGain(newGainDb: Float) {
        val normalized = StationAudioManager.normalizeGainStep(newGainDb)
        sliderValue = normalized
        stationAudioManager.setManualGainDb(station.StationUuid, normalized)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.action_station_gain),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = station.Name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = formattedGain,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (abs(sliderValue) >= 0.01f) AmaradioAmber else MaterialTheme.colorScheme.onSurface
                )

                Slider(
                    value = sliderValue,
                    onValueChange = { applyGain(it) },
                    valueRange = VolumeNormalizationAudioProcessor.MIN_MANUAL_GAIN_DB..VolumeNormalizationAudioProcessor.MAX_MANUAL_GAIN_DB,
                    steps = 47, // 0.5 dB steps from -12.0 to +12.0 dB
                    modifier = Modifier.semantics {
                        stateDescription = formattedGain
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = AmaradioAmber,
                        activeTrackColor = AmaradioAmber,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "-12 dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "0 dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+12 dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = abs(sliderValue - preset) < 0.01f
                        val label = if (preset > 0f) "+${preset.toInt()}" else "${preset.toInt()}"
                        FilterChip(
                            selected = isSelected,
                            onClick = { applyGain(preset) },
                            label = {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) AmaradioAmber else MaterialTheme.colorScheme.outline
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = AmaradioAmber
                            )
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.station_gain_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
            ) {
                Text(
                    text = stringResource(R.string.action_ok),
                    fontWeight = FontWeight.Bold,
                    color = AmaradioAmber
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { applyGain(0f) },
                enabled = abs(sliderValue) >= 0.01f,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(
                    text = stringResource(R.string.action_reset),
                    color = if (abs(sliderValue) >= 0.01f) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    )
}

@Composable
fun InAppEqualizerDialog(
    onOpenSystemEqualizer: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val eqManager = remember(context) {
        (context.applicationContext as AMARadioApp).inAppEqualizerManager
    }
    val uiState by eqManager.uiState.collectAsStateWithLifecycle()

    DisposableEffect(eqManager) {
        eqManager.onDialogOpened(PlayerServiceUtil.getAudioSessionId())
        onDispose {
            eqManager.onDialogClosed()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = AmaradioAmber
                )
                Text(
                    text = stringResource(R.string.settings_equalizer),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Enable / Disable Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.eq_enable),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = uiState.enabled,
                        onCheckedChange = { checked ->
                            eqManager.setEnabled(checked, PlayerServiceUtil.getAudioSessionId())
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = AmaradioAmber
                        )
                    )
                }

                if (!uiState.isSupported) {
                    Text(
                        text = stringResource(R.string.eq_not_supported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    // Presets Row
                    if (uiState.presetNames.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isCustom = uiState.presetIndex < 0
                                FilterChip(
                                    selected = isCustom,
                                    enabled = uiState.enabled,
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = stringResource(R.string.eq_preset_custom),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    border = BorderStroke(
                                        width = if (isCustom) 1.5.dp else 1.dp,
                                        color = if (isCustom && uiState.enabled) AmaradioAmber else MaterialTheme.colorScheme.outline
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        selectedLabelColor = AmaradioAmber
                                    )
                                )
                            }
                            itemsIndexed(uiState.presetNames) { index, name ->
                                val isSelected = uiState.presetIndex == index
                                FilterChip(
                                    selected = isSelected,
                                    enabled = uiState.enabled,
                                    onClick = {
                                        eqManager.selectPreset(index, PlayerServiceUtil.getAudioSessionId())
                                    },
                                    label = {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected && uiState.enabled) AmaradioAmber else MaterialTheme.colorScheme.outline
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        selectedLabelColor = AmaradioAmber
                                    )
                                )
                            }
                        }
                    }

                    // Band Sliders (5 bands typically: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz)
                    uiState.bands.forEach { band ->
                        val freqLabel = remember(band.centerFreqHz) {
                            "${InAppEqualizerManager.formatFrequency(band.centerFreqHz)} Hz"
                        }
                        val dbVal = (band.levelMb / 100f)
                        val dbText = remember(band.levelMb) {
                            val rounded = dbVal.roundToInt()
                            if (rounded > 0) "+${rounded} dB" else "${rounded} dB"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(52.dp),
                                maxLines = 1
                            )
                            Slider(
                                value = band.levelMb.toFloat(),
                                onValueChange = { rawMb ->
                                    val snapped = ((rawMb / 100f).roundToInt() * 100).toShort()
                                    eqManager.setBandLevel(
                                        bandIndex = band.index,
                                        levelMb = snapped,
                                        audioSessionId = PlayerServiceUtil.getAudioSessionId()
                                    )
                                },
                                enabled = uiState.enabled,
                                valueRange = band.minMb.toFloat()..band.maxMb.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        stateDescription = "$freqLabel $dbText"
                                    },
                                colors = SliderDefaults.colors(
                                    thumbColor = AmaradioAmber,
                                    activeTrackColor = AmaradioAmber,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Text(
                                text = dbText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.enabled && band.levelMb != 0.toShort()) {
                                    AmaradioAmber
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.width(44.dp),
                                maxLines = 1
                            )
                        }
                    }

                    if (!uiState.isAttachedToSession && uiState.enabled) {
                        Text(
                            text = stringResource(R.string.eq_active_on_playback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onOpenSystemEqualizer != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(
                        onClick = {
                            eqManager.setEnabled(false, PlayerServiceUtil.getAudioSessionId())
                            onDismiss()
                            onOpenSystemEqualizer()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.eq_system_equalizer),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
            ) {
                Text(
                    text = stringResource(R.string.action_ok),
                    fontWeight = FontWeight.Bold,
                    color = AmaradioAmber
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    eqManager.reset(PlayerServiceUtil.getAudioSessionId())
                },
                enabled = uiState.enabled && uiState.bands.any { it.levelMb != 0.toShort() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(
                    text = stringResource(R.string.action_reset),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
