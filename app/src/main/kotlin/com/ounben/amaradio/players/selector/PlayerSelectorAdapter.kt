package com.ounben.amaradio.players.selector

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.service.PauseReason
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation

class PlayerSelectorAdapter(private val context: Context, private val stationToPlay: DataRadioStation?) :
    RecyclerView.Adapter<PlayerSelectorAdapter.PlayerItemViewHolder>() {

    class PlayerItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewDescription: TextView = itemView.findViewById(R.id.textViewDescription)
        val btnPlay: ImageButton = itemView.findViewById(R.id.buttonPlay)
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val showPlayInExternal: Boolean
    private val viewTypes: MutableList<Int> = ArrayList()

    init {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        showPlayInExternal = sharedPref.getBoolean("play_external", false) && stationToPlay != null

        if (stationToPlay != null) {
            viewTypes.add(PlayerType.AMARadio.value)
        }
        if (showPlayInExternal) {
            viewTypes.add(PlayerType.EXTERNAL.value)
        }
    }

    fun notifyAMARadioPlaybackStateChanged() {
        if (stationToPlay != null) {
            val pos = viewTypes.indexOf(PlayerType.AMARadio.value)
            if (pos != -1) {
                notifyItemChanged(pos)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerItemViewHolder {
        val itemView = inflater.inflate(R.layout.list_item_play_in, parent, false)
        return PlayerItemViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PlayerItemViewHolder, position: Int) {
        val viewType = viewTypes[position]
        if (viewType == PlayerType.AMARadio.value) {
            holder.textViewDescription.setText(R.string.app_name)
            if (PlayerServiceUtil.isPlaying()) {
                holder.btnPlay.setImageResource(R.drawable.ic_pause_circle)
                holder.btnPlay.contentDescription = context.resources.getString(R.string.detail_pause)
            } else {
                holder.btnPlay.setImageResource(R.drawable.ic_play_circle)
                holder.btnPlay.contentDescription = context.getString(R.string.detail_play)
            }
            holder.btnPlay.setOnClickListener {
                if (PlayerServiceUtil.isPlaying()) {
                    PlayerServiceUtil.pause(PauseReason.USER)
                } else {
                    Utils.playAndWarnIfMetered(context.applicationContext as AMARadioApp, stationToPlay!!, PlayerType.AMARadio) {
                        Utils.play(stationToPlay)
                    }
                }
            }
        } else if (viewType == PlayerType.EXTERNAL.value) {
            holder.textViewDescription.setText(R.string.action_play_in_external)
            holder.btnPlay.setOnClickListener {
                Utils.playAndWarnIfMetered(context.applicationContext as AMARadioApp, stationToPlay!!, PlayerType.EXTERNAL) {
                    PlayStationTask.playExternal(stationToPlay, context).execute()
                }
            }
        }
    }

    override fun getItemCount(): Int = viewTypes.size
}
