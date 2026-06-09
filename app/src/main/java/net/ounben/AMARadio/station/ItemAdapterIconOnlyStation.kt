package net.ounben.AMARadio.station

import android.util.TypedValue
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import net.ounben.AMARadio.R
import net.ounben.AMARadio.Utils
import net.ounben.AMARadio.service.PlayerServiceUtil
import net.ounben.AMARadio.utils.RecyclerItemMoveAndSwipeHelper
import net.ounben.AMARadio.utils.SwipeableViewHolder

class ItemAdapterIconOnlyStation(fragmentActivity: FragmentActivity, resourceId: Int, filterType: StationsFilter.FilterType) : 
    ItemAdapaterContextMenuStation(fragmentActivity, resourceId, filterType),
    RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    inner class IconOnlyStationViewHolder(itemView: View) : ItemAdapterStation.StationViewHolder(itemView), View.OnCreateContextMenuListener, SwipeableViewHolder {
        private var contextMenu: PopupMenu? = null

        init {
            itemView.findViewById<View>(R.id.station_icon_foreground)?.let { viewForeground = it }
            itemView.findViewById<FrameLayout>(R.id.stationIconFrameLayout)?.let { frameLayout = it }
            itemView.findViewById<ImageView>(R.id.iconImageViewIcon)?.let { imageViewIcon = it }
            itemView.findViewById<ImageView>(R.id.iconTransparentCircle)?.let { transparentImageView = it }
            itemView.setOnCreateContextMenuListener(this)
        }

        fun dismissContextMenu() {
            contextMenu?.dismiss()
            contextMenu = null
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            if (contextMenu != null) return
            val pos = adapterPosition
            if (pos == RecyclerView.NO_POSITION) return
            val station = filteredStationsList[pos]
            contextMenu = StationPopupMenu.open(v, fragmentActivity, fragmentActivity, station, this@ItemAdapterIconOnlyStation)
            contextMenu?.setOnDismissListener {
                dismissContextMenu()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val v = inflater.inflate(resourceId, parent, false)
        return IconOnlyStationViewHolder(v)
    }

    override fun onBindViewHolder(holder: ItemAdapterStation.StationViewHolder, position: Int) {
        val station = filteredStationsList[position]
        val useCircularIcons = Utils.useCircularIcons(fragmentActivity)
        if (station.hasIcon()) {
            setupIcon(useCircularIcons, holder.imageViewIcon, holder.transparentImageView)
            PlayerServiceUtil.getStationIcon(holder.imageViewIcon, station.IconUrl)
        } else {
            holder.imageViewIcon.setImageDrawable(null)
        }
        val tv = TypedValue()
        if (playingStationPosition == position) {
            fragmentActivity.theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, tv, true)
            holder.frameLayout.setBackgroundColor(tv.data)
            holder.transparentImageView.setColorFilter(tv.data)
        } else {
            fragmentActivity.theme.resolveAttribute(R.attr.iconsInItemBackgroundColor, tv, true)
            holder.frameLayout.setBackgroundColor(tv.data)
        }
    }

    fun enableItemMove(recyclerView: RecyclerView) {
        val swipeAndMoveHelper = RecyclerItemMoveAndSwipeHelper(fragmentActivity, ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0, this)
        ItemTouchHelper(swipeAndMoveHelper).attachToRecyclerView(recyclerView)
    }
}
