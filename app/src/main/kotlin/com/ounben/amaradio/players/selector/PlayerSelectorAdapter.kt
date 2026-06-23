package com.ounben.amaradio.players.selector

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.players.mpd.MPDClient
import com.ounben.amaradio.players.mpd.MPDServerData
import com.ounben.amaradio.players.mpd.tasks.MPDChangeVolumeTask
import com.ounben.amaradio.players.mpd.tasks.MPDPauseTask
import com.ounben.amaradio.players.mpd.tasks.MPDResumeTask
import com.ounben.amaradio.players.mpd.tasks.MPDStopTask
import com.ounben.amaradio.service.PauseReason
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation

class PlayerSelectorAdapter(private val context: Context, private val stationToPlay: DataRadioStation?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface ActionListener {
        fun editServer(mpdServerData: MPDServerData)
        fun removeServer(mpdServerData: MPDServerData)
    }

    private class MPDServerItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgConnectionStatus: ImageView = itemView.findViewById(R.id.imgConnectionStatus)
        val textViewServerName: TextView = itemView.findViewById(R.id.textViewMPDName)
        val btnPlay: ImageButton = itemView.findViewById(R.id.buttonPlay)
        val btnStop: ImageButton = itemView.findViewById(R.id.buttonStop)
        val btnMore: ImageButton = itemView.findViewById(R.id.buttonMore)
        val textViewNoConnection: TextView = itemView.findViewById(R.id.textViewNoConnection)
        val btnDecreaseVolume: AppCompatImageButton = itemView.findViewById(R.id.buttonMPDDecreaseVolume)
        val btnIncreaseVolume: AppCompatImageButton = itemView.findViewById(R.id.buttonMPDIncreaseVolume)
        val textViewCurrentVolume: TextView = itemView.findViewById(R.id.textViewMPDVolume)
        var mpdServerData: MPDServerData? = null
    }

    private class PlayerItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewDescription: TextView = itemView.findViewById(R.id.textViewDescription)
        val btnPlay: ImageButton = itemView.findViewById(R.id.buttonPlay)
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val showPlayInExternal: Boolean
    private val warnOnMeteredConnection: Boolean
    private var fixedViewsCount: Int = 0
    private val viewTypes: MutableList<Int> = ArrayList()
    private var actionListener: ActionListener? = null
    private val mpdClient: MPDClient = (context.applicationContext as AMARadioApp).mpdClient
    private var mpdServers: List<MPDServerData>? = null

    init {
        val AMARadioApp = context.applicationContext as AMARadioApp
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        showPlayInExternal = sharedPref.getBoolean("play_external", false) && stationToPlay != null
        warnOnMeteredConnection = sharedPref.getBoolean(PlayerService.METERED_CONNECTION_WARNING_KEY, false)

        if (stationToPlay != null) {
            fixedViewsCount++
            viewTypes.add(PlayerType.AMARadio.value)
        }
        if (showPlayInExternal) {
            fixedViewsCount++
            viewTypes.add(PlayerType.EXTERNAL.value)
        }
        if (AMARadioApp.castHandler.isCastSessionAvailable) {
            fixedViewsCount++
            viewTypes.add(PlayerType.CAST.value)
        }
    }

    fun setActionListener(actionListener: ActionListener?) {
        this.actionListener = actionListener
    }

    fun notifyAMARadioPlaybackStateChanged() {
        if (stationToPlay != null) {
            val pos = viewTypes.indexOf(PlayerType.AMARadio.value)
            if (pos != -1) {
                notifyItemChanged(pos)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType != PlayerType.MPD_SERVER.value) {
            val itemView = inflater.inflate(R.layout.list_item_play_in, parent, false)
            PlayerItemViewHolder(itemView)
        } else {
            val itemView = inflater.inflate(R.layout.list_item_mpd_server, parent, false)
            MPDServerItemViewHolder(itemView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MPDServerItemViewHolder) {
            bindMPDViewHolder(holder, position)
        } else if (holder is PlayerItemViewHolder) {
            bindPlayerViewHolder(holder, position)
        }
    }

    private fun bindPlayerViewHolder(holder: PlayerItemViewHolder, position: Int) {
        val viewType = getItemViewType(position)
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
        } else if (viewType == PlayerType.CAST.value) {
            holder.textViewDescription.setText(R.string.media_route_menu_title)
            holder.btnPlay.setOnClickListener {
                PlayStationTask.playCAST(stationToPlay!!, context).execute()
            }
        }
    }

    private fun bindMPDViewHolder(holder: MPDServerItemViewHolder, position: Int) {
        val mpdServerData = mpdServers!![translatePosition(position)]
        holder.mpdServerData = mpdServerData
        holder.textViewServerName.text = mpdServerData.name
        if (mpdServerData.connected) {
            holder.btnPlay.visibility = View.VISIBLE
            holder.textViewNoConnection.visibility = View.GONE
            holder.textViewCurrentVolume.text = mpdServerData.volume.toString()
            holder.textViewCurrentVolume.visibility = View.VISIBLE
            holder.imgConnectionStatus.setImageResource(R.drawable.ic_mpd_connected_24dp)
        } else {
            holder.btnPlay.visibility = View.GONE
            holder.textViewCurrentVolume.visibility = View.GONE
            holder.textViewNoConnection.visibility = View.VISIBLE
            holder.imgConnectionStatus.setImageResource(R.drawable.ic_mpd_disconnected_24dp)
        }

        if (mpdServerData.connected && stationToPlay == null && mpdServerData.status != MPDServerData.Status.Playing) {
            holder.btnPlay.visibility = View.GONE
        }

        if (mpdServerData.connected && mpdServerData.status != MPDServerData.Status.Idle) {
            holder.btnStop.visibility = View.VISIBLE
            holder.btnStop.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDStopTask(null)) }
        } else {
            holder.btnStop.visibility = View.GONE
        }

        if (mpdServerData.connected && mpdServerData.status != MPDServerData.Status.Idle) {
            holder.btnDecreaseVolume.visibility = View.VISIBLE
            holder.btnIncreaseVolume.visibility = View.VISIBLE
            holder.btnDecreaseVolume.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDChangeVolumeTask(-10, null, mpdServerData)) }
            holder.btnIncreaseVolume.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDChangeVolumeTask(10, null, mpdServerData)) }
        } else {
            holder.btnDecreaseVolume.visibility = View.INVISIBLE
            holder.btnIncreaseVolume.visibility = View.INVISIBLE
        }

        holder.btnMore.setOnClickListener {
            val dropDownMenu = PopupMenu(context, holder.btnMore)
            dropDownMenu.menuInflater.inflate(R.menu.menu_mpd_server, dropDownMenu.menu)
            if (stationToPlay == null) {
                dropDownMenu.menu.findItem(R.id.action_play).isVisible = false
                dropDownMenu.menu.findItem(R.id.action_pause).isVisible = false
            } else {
                if (mpdServerData.status != MPDServerData.Status.Playing) {
                    dropDownMenu.menu.findItem(R.id.action_pause).isVisible = false
                } else {
                    dropDownMenu.menu.findItem(R.id.action_play).isVisible = false
                }
            }
            dropDownMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> actionListener?.editServer(mpdServerData)
                    R.id.action_remove -> actionListener?.removeServer(mpdServerData)
                    R.id.action_play -> PlayStationTask.playMPD(mpdClient, mpdServerData, stationToPlay!!, context).execute()
                    R.id.action_pause -> mpdClient.enqueueTask(mpdServerData, MPDPauseTask(null))
                }
                true
            }
            dropDownMenu.show()
        }

        if (mpdServerData.connected) {
            if (stationToPlay != null) {
                holder.btnPlay.contentDescription = context.resources.getString(R.string.detail_play)
                holder.btnPlay.setImageResource(R.drawable.ic_play_circle)
                if (mpdServerData.status != MPDServerData.Status.Playing) {
                    holder.btnPlay.setOnClickListener { PlayStationTask.playMPD(mpdClient, mpdServerData, stationToPlay, context).execute() }
                } else {
                    holder.btnPlay.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDResumeTask(null)) }
                }
            }
            if (mpdServerData.status == MPDServerData.Status.Playing) {
                holder.btnPlay.contentDescription = context.resources.getString(R.string.detail_pause)
                holder.btnPlay.setImageResource(R.drawable.ic_pause_circle)
                holder.btnPlay.setOnClickListener { mpdClient.enqueueTask(mpdServerData, MPDPauseTask(null)) }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position >= fixedViewsCount) {
            PlayerType.MPD_SERVER.value
        } else {
            viewTypes[position]
        }
    }

    fun setEntries(mpdServers: List<MPDServerData>) {
        this.mpdServers = mpdServers
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return (mpdServers?.size ?: 0) + fixedViewsCount
    }

    private fun translatePosition(position: Int): Int {
        return position - fixedViewsCount
    }
}
