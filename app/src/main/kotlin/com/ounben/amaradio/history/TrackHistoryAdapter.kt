package com.ounben.amaradio.history

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentActivity
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.service.PlayerServiceUtil

class TrackHistoryAdapter(private val activity: FragmentActivity) : PagingDataAdapter<TrackHistoryEntry, TrackHistoryAdapter.TrackHistoryItemViewHolder>(DIFF_CALLBACK) {
    class TrackHistoryItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rootview: View = itemView
        val imageViewStationIcon: ImageView = itemView.findViewById(R.id.imageViewStationIcon)
        val textViewTrackName: TextView = itemView.findViewById(R.id.textViewTrackName)
        val textViewTrackArtist: TextView = itemView.findViewById(R.id.textViewTrackArtist)
    }

    private val context: Context = activity
    private var shouldLoadIcons = false
    private val stationImagePlaceholder: Drawable? = AppCompatResources.getDrawable(context, R.drawable.ic_radio_24dp)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHistoryItemViewHolder {
        val inflater = LayoutInflater.from(context)
        val itemView = inflater.inflate(R.layout.list_item_history_track_item, parent, false)
        return TrackHistoryItemViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TrackHistoryItemViewHolder, position: Int) {
        val historyEntry = getItem(position) ?: return
        shouldLoadIcons = Utils.shouldLoadIcons(context)
        if (shouldLoadIcons) {
            if (!TextUtils.isEmpty(historyEntry.stationIconUrl)) {
                PlayerServiceUtil.getStationIcon(holder.imageViewStationIcon, historyEntry.stationIconUrl)
            } else {
                holder.imageViewStationIcon.setImageDrawable(stationImagePlaceholder)
            }
        } else {
            holder.imageViewStationIcon.visibility = View.GONE
        }
        holder.textViewTrackName.text = historyEntry.track
        holder.textViewTrackArtist.text = historyEntry.artist
        holder.textViewTrackName.isSelected = true
        holder.textViewTrackArtist.isSelected = true
        holder.rootview.setOnClickListener { showTrackInfoDialog(historyEntry) }
    }

    private fun showTrackInfoDialog(historyEntry: TrackHistoryEntry) {
        val trackHistoryInfoDialog = TrackHistoryInfoDialog(historyEntry)
        trackHistoryInfoDialog.show(activity.supportFragmentManager, TrackHistoryInfoDialog.FRAGMENT_TAG)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<TrackHistoryEntry> = object : DiffUtil.ItemCallback<TrackHistoryEntry>() {
            override fun areItemsTheSame(oldEntry: TrackHistoryEntry, newEntry: TrackHistoryEntry): Boolean {
                return oldEntry.uid == newEntry.uid
            }

            override fun areContentsTheSame(oldEntry: TrackHistoryEntry, newEntry: TrackHistoryEntry): Boolean {
                return oldEntry == newEntry
            }
        }
    }
}
