package com.ounben.amaradio.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.ui.AMARadioTheme
import java.text.DateFormat

class TrackHistoryInfoDialog(private val historyEntry: TrackHistoryEntry) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AMARadioTheme {
                    TrackInfoContent(
                        entry = historyEntry,
                        onCopy = { copyToClipboard() }
                    )
                }
            }
        }
    }

    @Composable
    private fun TrackInfoContent(entry: TrackHistoryEntry, onCopy: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp
            ) {
                AsyncImage(
                    model = entry.artUrl,
                    contentDescription = null,
                    error = painterResource(R.drawable.ic_radio_24dp),
                    placeholder = painterResource(R.drawable.ic_radio_24dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = entry.track ?: "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = entry.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            val dateStr = DateFormat.getDateInstance().format(entry.startTime)
            val durationStr = if (entry.endTime.after(entry.startTime)) {
                val elapsed = (entry.endTime.time - entry.startTime.time) / 1000
                " • ${DateUtils.formatElapsedTime(elapsed)}"
            } else ""

            Text(
                text = "$dateStr$durationStr",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.action_copy_info))
            }
        }
    }

    private fun copyToClipboard() {
        val context = context ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val info = "${historyEntry.artist} - ${historyEntry.track}"
            val clip = ClipData.newPlainText("Track info", info)
            clipboard.setPrimaryClip(clip)
            Utils.showModernToast(requireActivity(), R.string.notify_track_info_copied)
        }
        dismiss()
    }

    companion object {
        const val FRAGMENT_TAG = "tracks_history_info_dialog_fragment"
    }
}
