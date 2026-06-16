package com.ounben.amaradio.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import coil.load
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import java.text.DateFormat

class TrackHistoryInfoDialog(private val historyEntry: TrackHistoryEntry) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        @Suppress("DEPRECATION")
        retainInstance = true

        val view = inflater.inflate(R.layout.dialog_track_history_details, container, false)

        val imageViewTrackArt = view.findViewById<AppCompatImageView>(R.id.imageViewTrackArt)
        val textViewTrack = view.findViewById<TextView>(R.id.textViewTrack)
        val textViewArtist = view.findViewById<TextView>(R.id.textViewArtist)
        val textViewDate = view.findViewById<TextView>(R.id.textViewDate)
        val textViewDuration = view.findViewById<TextView>(R.id.textViewDuration)
        val btnCopyInfo = view.findViewById<AppCompatButton>(R.id.btnCopyTrackInfo)

        val resource = requireContext().resources
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, resource.displayMetrics).toInt().coerceAtLeast(1)
        imageViewTrackArt.load(historyEntry.artUrl) {
            placeholder(R.drawable.ic_radio_24dp)
            size(px)
        }

        textViewTrack.text = historyEntry.track
        textViewArtist.text = historyEntry.artist
        textViewDate.text = DateFormat.getDateInstance().format(historyEntry.startTime)

        if (historyEntry.endTime.after(historyEntry.startTime)) {
            val elapsedTime = DateUtils.formatElapsedTime((historyEntry.endTime.time - historyEntry.startTime.time) / 1000)
            textViewDuration.text = " • $elapsedTime"
        } else {
            textViewDuration.text = ""
        }

        btnCopyInfo.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val info = "${historyEntry.artist} - ${historyEntry.track}"
                val clip = ClipData.newPlainText("Track info", info)
                clipboard.setPrimaryClip(clip)
                Utils.showSnackbar(view, "Kopiert: $info")
            }
        }

        return view
    }

    companion object {
        const val FRAGMENT_TAG = "tracks_history_info_dialog_fragment"
    }
}
