package com.ounben.amaradio.recorder

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.SolidRedError
import com.ounben.amaradio.ui.SolidRedErrorLight

@Composable
fun RecordButtonWithTimer(
    currentStation: DataRadioStation?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    val context = LocalContext.current
    val recordManager = remember { RecordManager.getInstance(context) }
    val recordState by recordManager.recordingState.collectAsState()
    var showTimerMenu by remember { mutableStateOf(false) }
    val noStationMsg = stringResource(R.string.error_station_load)

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                if (recordState.isRecording) {
                    recordManager.stopRecording()
                } else {
                    if (currentStation != null) {
                        showTimerMenu = true
                    } else {
                        Toast.makeText(context, noStationMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ) {
            val isDark = isSystemInDarkTheme()
            val recordRedColor = if (isDark) SolidRedError else SolidRedErrorLight
            Icon(
                imageVector = if (recordState.isRecording) Icons.Filled.Stop else Icons.Filled.RadioButtonChecked,
                contentDescription = "Record",
                modifier = Modifier.size(iconSize),
                tint = if (recordState.isRecording) recordRedColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = showTimerMenu,
            onDismissRequest = { showTimerMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
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
                        currentStation?.let { station ->
                            recordManager.startRecording(station, durationMs)
                        }
                    }
                )
            }
        }
    }
}
