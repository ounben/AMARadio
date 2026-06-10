package net.ounben.AMARadio.station

import android.content.*
import android.content.pm.ShortcutManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import net.ounben.AMARadio.*
import net.ounben.AMARadio.interfaces.IAdapterRefreshable
import net.ounben.AMARadio.players.PlayStationTask
import net.ounben.AMARadio.players.selector.PlayerType
import net.ounben.AMARadio.service.PlayerService
import net.ounben.AMARadio.service.PlayerServiceUtil
import jp.wasabeef.transformers.picasso.RoundedCornersTransformation
import net.ounben.AMARadio.utils.*
import net.ounben.AMARadio.views.TagsView
import java.util.*

open class ItemAdapterStation(
    protected val fragmentActivity: FragmentActivity,
    protected val resourceId: Int,
    protected val filterType: StationsFilter.FilterType
) : RecyclerView.Adapter<ItemAdapterStation.StationViewHolder>(),
    RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    interface StationActionsListener {
        fun onStationClick(station: DataRadioStation, pos: Int)
        fun onStationMoved(from: Int, to: Int)
        fun onStationSwiped(station: DataRadioStation)
        fun onStationMoveFinished()
    }

    fun interface FilterListener {
        fun onSearchCompleted(searchStatus: StationsFilter.SearchStatus)
    }

    private val TAG = "AdapterStations"
    private var stationsList: List<DataRadioStation>? = null
    var filteredStationsList: List<DataRadioStation> = ArrayList()
    var stationActionsListener: StationActionsListener? = null
    private var filterListener: FilterListener? = null
    private var supportsStationRemoval = false
    private var shouldLoadIcons = false
    private var refreshable: IAdapterRefreshable? = null
    private var updateUIReceiver: BroadcastReceiver? = null
    private var expandedPosition = -1
    var playingStationPosition = -1
    protected val stationImagePlaceholder: Drawable? = AppCompatResources.getDrawable(fragmentActivity, R.drawable.ic_radio_24dp)
    private val favouriteManager: FavouriteManager = (fragmentActivity.application as AMARadioApp).favouriteManager
    private var filter: StationsFilter? = null

    private val tagSelectionCallback = TagsView.TagSelectionCallback { tag ->
        val i = Intent(fragmentActivity, ActivityMain::class.java)
        i.putExtra(ActivityMain.EXTRA_SEARCH_TAG, tag)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fragmentActivity.startActivity(i)
    }

    open inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, SwipeableViewHolder {
        var viewForeground: View = itemView.findViewById(R.id.station_foreground)
        var layoutMain: LinearLayout = itemView.findViewById(R.id.layoutMain)
        var frameLayout: FrameLayout = itemView.findViewById(R.id.frameLayout)
        var imageViewIcon: ImageView = itemView.findViewById(R.id.imageViewIcon)
        var starredStatusIcon: ImageView = itemView.findViewById(R.id.starredStatusIcon)
        var textViewTitle: TextView = itemView.findViewById(R.id.textViewTitle)
        var textViewShortDescription: TextView = itemView.findViewById(R.id.textViewShortDescription)
        var textViewTags: TextView = itemView.findViewById(R.id.textViewTags)
        var buttonMore: ImageButton = itemView.findViewById(R.id.buttonMore)
        var stubDetails: ViewStub? = itemView.findViewById(R.id.stubDetails)

        var viewDetails: View? = null
        var buttonVisitWebsite: ImageButton? = null
        var buttonBookmark: ImageButton? = null
        var buttonShare: ImageButton? = null
        var buttonAddAlarm: ImageButton? = null
        var viewTags: TagsView? = null
        var buttonCreateShortcut: ImageButton? = null
        var buttonPlayInternalOrExternal: ImageButton? = null

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            val pos = adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                stationActionsListener?.onStationClick(filteredStationsList[pos], pos)
            }
        }

        override val foregroundView: View
            get() = viewForeground
    }

    init {
        val filterIntentFilter = IntentFilter()
        filterIntentFilter.addAction(PlayerService.PLAYER_SERVICE_META_UPDATE)
        filterIntentFilter.addAction(DataRadioStation.RADIO_STATION_LOCAL_INFO_CHAGED)
        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent == null) return
                when (intent.action) {
                    PlayerService.PLAYER_SERVICE_META_UPDATE -> highlightCurrentStation()
                    DataRadioStation.RADIO_STATION_LOCAL_INFO_CHAGED -> {
                        val uuid = intent.getStringExtra(DataRadioStation.RADIO_STATION_UUID)
                        notifyChangedByStationUuid(uuid)
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(fragmentActivity).registerReceiver(updateUIReceiver!!, filterIntentFilter)
    }

    fun enableItemRemoval(recyclerView: RecyclerView) {
        if (!supportsStationRemoval) {
            supportsStationRemoval = true
            val swipeHelper = RecyclerItemSwipeHelper(fragmentActivity, 0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, this)
            ItemTouchHelper(swipeHelper).attachToRecyclerView(recyclerView)
        }
    }

    fun enableItemMoveAndRemoval(recyclerView: RecyclerView) {
        if (!supportsStationRemoval) {
            supportsStationRemoval = true
            val swipeAndMoveHelper = RecyclerItemMoveAndSwipeHelper(fragmentActivity, ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, this)
            ItemTouchHelper(swipeAndMoveHelper).attachToRecyclerView(recyclerView)
        }
    }

    fun updateList(refreshableList: IAdapterRefreshable?, stationsList: List<DataRadioStation>) {
        this.refreshable = refreshableList
        this.stationsList = stationsList
        this.filteredStationsList = stationsList
        notifyStationsChanged()
    }

    private fun notifyStationsChanged() {
        expandedPosition = -1
        playingStationPosition = -1
        shouldLoadIcons = Utils.shouldLoadIcons(fragmentActivity)
        highlightCurrentStation()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val v = inflater.inflate(resourceId, parent, false)
        return StationViewHolder(v)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = filteredStationsList[position]
        val prefs = PreferenceManager.getDefaultSharedPreferences(fragmentActivity.applicationContext)
        
        when {
            station.DeletedOnServer -> holder.itemView.setBackgroundColor(-0x10000)
            !station.Working -> holder.itemView.setBackgroundColor(-0x100)
            else -> holder.itemView.setBackgroundColor(0)
        }

        if (!shouldLoadIcons) {
            holder.imageViewIcon.visibility = View.GONE
        } else {
            if (station.hasIcon()) {
                setupIcon(holder.imageViewIcon)
                PlayerServiceUtil.getStationIcon(holder.imageViewIcon, station.IconUrl)
            } else {
                holder.imageViewIcon.setImageDrawable(stationImagePlaceholder)
                setupIcon(holder.imageViewIcon)
            }
            if (UiScaler.getScaleFactor(fragmentActivity) == UiScaler.SCALE_COMPACT) {
                setupCompactStyle(holder)
            } else {
                setupRegularStyle(holder)
            }
            
            val isInFavorites = favouriteManager.has(station.StationUuid)
            val toggleFavoriteListener = View.OnClickListener {
                if (favouriteManager.has(station.StationUuid)) {
                    StationActions.removeFromFavourites(fragmentActivity, it, station)
                } else {
                    StationActions.markAsFavourite(fragmentActivity, station)
                }
                notifyItemChanged(holder.adapterPosition)
            }
            
            holder.starredStatusIcon.setOnClickListener(toggleFavoriteListener)
            holder.starredStatusIcon.contentDescription = fragmentActivity.getString(if (isInFavorites) R.string.detail_unstar else R.string.detail_star)

            val playListener = View.OnClickListener { holder.onClick(it) }
            holder.imageViewIcon.setOnClickListener(playListener)
            holder.frameLayout.setOnClickListener(playListener)
        }

        val isExpanded = position == expandedPosition
        holder.textViewTags.visibility = if (isExpanded) View.GONE else View.VISIBLE
        holder.buttonMore.setImageResource(if (isExpanded) R.drawable.ic_expand_less_black_24dp else R.drawable.ic_expand_more_black_24dp)
        holder.buttonMore.contentDescription = fragmentActivity.getString(if (isExpanded) R.string.image_button_less else R.string.image_button_more)
        holder.buttonMore.setOnClickListener {
            val oldExpanded = expandedPosition
            val currentPos = holder.adapterPosition
            expandedPosition = if (isExpanded) -1 else currentPos
            if (oldExpanded != -1) notifyItemChanged(oldExpanded)
            if (expandedPosition != -1) notifyItemChanged(expandedPosition)
        }

        val tv = TypedValue()
        if (playingStationPosition == position) {
            fragmentActivity.theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, tv, true)
            holder.textViewTitle.setTextColor(tv.data)
            holder.textViewTitle.setTypeface(null, Typeface.BOLD)
        } else {
            // fragmentActivity.theme.resolveAttribute(R.attr.boxBackgroundColor, tv, true)
            holder.textViewTitle.typeface = holder.textViewShortDescription.typeface
            fragmentActivity.theme.resolveAttribute(R.attr.menuTextColorDefault, tv, true)
            holder.textViewTitle.setTextColor(tv.data)
        }

        holder.textViewTitle.text = station.Name
        holder.textViewShortDescription.text = station.getShortDetails(fragmentActivity)
        holder.textViewTags.text = station.TagsAll.replace(",", ", ")

        val inFavourites = favouriteManager.has(station.StationUuid)
        holder.starredStatusIcon.visibility = View.VISIBLE
        if (inFavourites) {
            holder.starredStatusIcon.setImageResource(R.drawable.ic_star_24dp)
            holder.starredStatusIcon.alpha = 1.0f
        } else {
            holder.starredStatusIcon.setImageResource(R.drawable.ic_star_transparent_with_border_24dp)
            holder.starredStatusIcon.alpha = 0.5f
        }
        holder.starredStatusIcon.contentDescription = if (inFavourites) fragmentActivity.getString(R.string.action_favorite) else ""

        val flag = CountryFlagsLoader.instance.getFlag(fragmentActivity, station.CountryCode)
        flag?.let {
            val k = it.intrinsicWidth.toFloat() / it.intrinsicHeight.toFloat()
            val viewHeight = (holder.textViewShortDescription.textSize * 1.3f).toInt()
            it.setBounds(0, 0, (k * viewHeight).toInt(), viewHeight)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            holder.textViewShortDescription.setCompoundDrawablesRelative(flag, null, null, null)
        } else {
            holder.textViewShortDescription.setCompoundDrawables(flag, null, null, null)
        }

        if (isExpanded) {
            if (holder.stubDetails != null) {
                holder.viewDetails = holder.stubDetails!!.inflate()
                holder.stubDetails = null
                holder.viewTags = holder.viewDetails!!.findViewById(R.id.viewTags)
                holder.buttonVisitWebsite = holder.viewDetails!!.findViewById(R.id.buttonVisitWebsite)
                holder.buttonShare = holder.viewDetails!!.findViewById(R.id.buttonShare)
                holder.buttonBookmark = holder.viewDetails!!.findViewById(R.id.buttonBookmark)
                holder.buttonAddAlarm = holder.viewDetails!!.findViewById(R.id.buttonAddAlarm)
                holder.buttonCreateShortcut = holder.viewDetails!!.findViewById(R.id.buttonCreateShortcut)
                holder.buttonPlayInternalOrExternal = holder.viewDetails!!.findViewById(R.id.buttonPlayInAMARadio)
                
                applyScalingToExpandedDetails(holder)
            }

            holder.buttonVisitWebsite?.setOnClickListener { StationActions.openStationHomeUrl(fragmentActivity, station) }
            holder.buttonShare?.setOnClickListener { StationActions.share(fragmentActivity, station) }

            if (favouriteManager.has(station.StationUuid)) {
                holder.buttonBookmark?.visibility = View.GONE
            } else {
                holder.buttonBookmark?.setOnClickListener {
                    StationActions.markAsFavourite(fragmentActivity, station)
                    notifyItemChanged(holder.adapterPosition)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && fragmentActivity.applicationContext.getSystemService(ShortcutManager::class.java).isRequestPinShortcutSupported) {
                holder.buttonCreateShortcut?.visibility = View.VISIBLE
                holder.buttonCreateShortcut?.setOnClickListener {
                    station.prepareShortcut(fragmentActivity, { shortcut ->
                        val sm = fragmentActivity.applicationContext.getSystemService(ShortcutManager::class.java)
                        if (sm.isRequestPinShortcutSupported) sm.requestPinShortcut(shortcut, null)
                    })
                }
            } else {
                holder.buttonCreateShortcut?.visibility = View.INVISIBLE
            }

            holder.buttonAddAlarm?.setOnClickListener { StationActions.setAsAlarm(fragmentActivity, station) }

            if (prefs.getBoolean("play_external", false)) {
                holder.buttonPlayInternalOrExternal?.setOnClickListener { StationActions.playInAMARadio(fragmentActivity, station) }
            } else {
                holder.buttonPlayInternalOrExternal?.contentDescription = fragmentActivity.getString(R.string.detail_play_in_external_player)
                holder.buttonPlayInternalOrExternal?.setImageResource(R.drawable.ic_play_arrow_24dp)
                holder.buttonPlayInternalOrExternal?.setOnClickListener {
                    Utils.playAndWarnIfMetered(fragmentActivity.application as AMARadioApp, station, PlayerType.EXTERNAL) {
                        PlayStationTask.playExternal(station, fragmentActivity).execute()
                    }
                }
            }
            val tags = station.TagsAll.split(",").toTypedArray()
            holder.viewTags?.setTags(tags.toList())
            holder.viewTags?.setTagSelectionCallback(tagSelectionCallback)
        }
        holder.viewDetails?.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = filteredStationsList.size

    override fun onSwiped(viewHolder: StationViewHolder, direction: Int) {
        stationActionsListener?.onStationSwiped(filteredStationsList[viewHolder.adapterPosition])
    }

    override fun onDragged(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Double, dY: Double) {}

    override fun onMoved(viewHolder: StationViewHolder, from: Int, to: Int) {
        stationActionsListener?.onStationMoved(from, to)
        notifyItemMoved(from, to)
    }

    override fun onMoveEnded(viewHolder: StationViewHolder) {
        stationActionsListener?.onStationMoveFinished()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        LocalBroadcastManager.getInstance(fragmentActivity).unregisterReceiver(updateUIReceiver!!)
    }

    fun setFilterListener(filterListener: FilterListener) {
        this.filterListener = filterListener
    }

    fun getFilter(): StationsFilter {
        if (filter == null) {
            filter = StationsFilter(fragmentActivity, filterType, object : StationsFilter.DataProvider {
                override fun getOriginalStationList(): List<DataRadioStation> = stationsList ?: emptyList()
                override fun notifyFilteredStationsChanged(status: StationsFilter.SearchStatus, filteredStations: List<DataRadioStation>) {
                    filteredStationsList = filteredStations
                    notifyStationsChanged()
                    filterListener?.onSearchCompleted(status)
                }
            })
        }
        return filter!!
    }

    fun setupIcon(imageView: ImageView) {
        // No-op
    }

    private fun applyScalingToExpandedDetails(holder: StationViewHolder) {
        val scale = UiScaler.getScaleFactor(fragmentActivity)
        if (scale == UiScaler.SCALE_STANDARD) return
        
        val buttonSize = (48 * fragmentActivity.resources.displayMetrics.density * scale).toInt()
        
        listOf(
            holder.buttonVisitWebsite,
            holder.buttonShare,
            holder.buttonBookmark,
            holder.buttonAddAlarm,
            holder.buttonCreateShortcut,
            holder.buttonPlayInternalOrExternal
        ).forEach { button ->
            button?.layoutParams?.width = buttonSize
            button?.layoutParams?.height = buttonSize
            if (button is ImageButton) {
                button.scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        
        holder.viewTags?.let { tagsView ->
            val tagHeight = (25 * fragmentActivity.resources.displayMetrics.density * scale).toInt()
            // Note: TagsView uses custom attributes, we might need to expose them if we want deep scaling.
            // For now, fontScale handles the text size inside TagsView.
        }
    }

    private fun setupRegularStyle(holder: StationViewHolder) {
        val scale = UiScaler.getScaleFactor(fragmentActivity)
        holder.textViewShortDescription.visibility = View.VISIBLE
        if (scale == UiScaler.SCALE_STANDARD) return
        
        val lpMain = holder.layoutMain.layoutParams
        lpMain.height = (90 * fragmentActivity.resources.displayMetrics.density * scale).toInt()
        holder.layoutMain.layoutParams = lpMain
        holder.layoutMain.minimumHeight = lpMain.height
        
        val lpFrame = holder.frameLayout.layoutParams
        lpFrame.width = (fragmentActivity.resources.getDimension(R.dimen.regular_style_icon_container_width) * scale).toInt()
        holder.frameLayout.layoutParams = lpFrame
        
        val iconSize = (70 * fragmentActivity.resources.displayMetrics.density * scale).toInt()
        val lpIcon = holder.imageViewIcon.layoutParams
        lpIcon.width = iconSize
        lpIcon.height = iconSize
        holder.imageViewIcon.layoutParams = lpIcon
    }

    private fun setupCompactStyle(holder: StationViewHolder) {
        val scale = UiScaler.getScaleFactor(fragmentActivity)
        holder.textViewShortDescription.visibility = View.GONE
        
        val lpMain = holder.layoutMain.layoutParams
        lpMain.height = (fragmentActivity.resources.getDimension(R.dimen.compact_style_item_minimum_height) * scale).toInt()
        holder.layoutMain.layoutParams = lpMain
        holder.layoutMain.minimumHeight = lpMain.height
        
        val lpFrame = holder.frameLayout.layoutParams
        lpFrame.width = (fragmentActivity.resources.getDimension(R.dimen.compact_style_icon_container_width) * scale).toInt()
        holder.frameLayout.layoutParams = lpFrame
        
        val iconSize = (fragmentActivity.resources.getDimension(R.dimen.compact_style_icon_height) * scale).toInt()
        val iconWidth = (fragmentActivity.resources.getDimension(R.dimen.compact_style_icon_width) * scale).toInt()
        
        val lpIcon = holder.imageViewIcon.layoutParams
        lpIcon.width = iconWidth
        lpIcon.height = iconSize
        holder.imageViewIcon.layoutParams = lpIcon
    }

    private fun highlightCurrentStation() {
        if (!PlayerServiceUtil.isPlaying()) return
        val currentStationUuid = PlayerServiceUtil.getStationId()
        val oldPos = playingStationPosition
        playingStationPosition = filteredStationsList.indexOfFirst { it.StationUuid == currentStationUuid }
        if (playingStationPosition != oldPos) {
            if (oldPos > -1) notifyItemChanged(oldPos)
            if (playingStationPosition > -1) notifyItemChanged(playingStationPosition)
        }
    }

    private fun notifyChangedByStationUuid(uuid: String?) {
        val index = filteredStationsList.indexOfFirst { it.StationUuid == uuid }
        if (index != -1) notifyItemChanged(index)
    }
}
