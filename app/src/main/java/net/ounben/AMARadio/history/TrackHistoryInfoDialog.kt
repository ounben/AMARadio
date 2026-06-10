package net.ounben.AMARadio.history

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.picasso.Picasso
import net.ounben.AMARadio.R
import java.text.DateFormat

class TrackHistoryInfoDialog(private val historyEntry: TrackHistoryEntry) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        @Suppress("DEPRECATION")
        retainInstance = true

        val view = inflater.inflate(R.layout.dialog_track_history_details, container, false)

        val imageViewTrackArt = view.findViewById<AppCompatImageView>(R.id.imageViewTrackArt)
        val textViewDate = view.findViewById<TextView>(R.id.textViewDate)
        val textViewDuration = view.findViewById<TextView>(R.id.textViewDuration)
        val btnLyrics = view.findViewById<AppCompatButton>(R.id.btnViewLyrics)
        val btnCopyInfo = view.findViewById<AppCompatButton>(R.id.btnCopyTrackInfo)

        val resource = requireContext().resources
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, resource.displayMetrics)
        Picasso.get()
                .load(historyEntry.artUrl)
                .placeholder(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_radio_24dp)!!)
                .resize(px.toInt(), 0)
                .into(imageViewTrackArt)

        textViewDate.text = DateFormat.getDateInstance().format(historyEntry.startTime)

        if (historyEntry.endTime.after(historyEntry.startTime)) {
            val elapsedTime = DateUtils.formatElapsedTime((historyEntry.endTime.time - historyEntry.startTime.time) / 1000)
            textViewDuration.text = elapsedTime
        } else {
            textViewDuration.text = ""
        }

        btnLyrics.setOnClickListener {
            if (isQuickLyricInstalled) {
                requireContext().startActivity(Intent("com.geecko.QuickLyric.getLyrics")
                        .putExtra("TAGS", arrayOf(historyEntry.artist, historyEntry.track)))
            } else {
                AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.alert_install_lyrics_app))
                        .setCancelable(true)
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.geecko.QuickLyric"))
                                requireContext().startActivity(browserIntent)
                            } catch (ex: ActivityNotFoundException) {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.geecko.QuickLyric"))
                                    requireContext().startActivity(browserIntent)
                                } catch (ex2: ActivityNotFoundException) {
                                    Toast.makeText(context, R.string.notify_open_link_failure, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
            }
        }

        btnCopyInfo.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("Track info", "${historyEntry.artist} ${historyEntry.track}")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext().applicationContext, R.string.notify_track_info_copied, Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private val isQuickLyricInstalled: Boolean
        get() {
            val pm = requireContext().packageManager
            return try {
                pm.getApplicationInfo("com.geecko.QuickLyric", 0).enabled
            } catch (ignored: Exception) {
                false
            }
        }

    companion object {
        const val FRAGMENT_TAG = "tracks_history_info_dialog_fragment"
    }
}
