package com.ounben.amaradio.station

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.utils.RecyclerItemMoveAndSwipeHelper
import com.ounben.amaradio.utils.SwipeableViewHolder
import com.ounben.amaradio.utils.UiScaler

class ItemAdapterIconOnlyStation(fragmentActivity: FragmentActivity, resourceId: Int, filterType: StationsFilter.FilterType) : 
    ItemAdapaterContextMenuStation(fragmentActivity, resourceId, filterType),
    RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    inner class IconOnlyStationViewHolder(itemView: View) : StationViewHolder(itemView), View.OnCreateContextMenuListener, SwipeableViewHolder {
        private var contextMenu: PopupMenu? = null

        init {
            itemView.findViewById<View>(R.id.station_foreground)?.let { viewForeground = it }
            itemView.findViewById<FrameLayout>(R.id.stationIconFrameLayout)?.let { frameLayout = it }
            itemView.findViewById<ImageView>(R.id.iconImageViewIcon)?.let { imageViewIcon = it }
            itemView.findViewById<ImageView>(R.id.starredStatusIcon)?.let { starredStatusIcon = it }
            itemView.findViewById<TextView>(R.id.textViewTitle)?.let { textViewTitle = it }
            itemView.setOnCreateContextMenuListener(this)
        }

        override fun dismissContextMenu() {
            contextMenu?.dismiss()
            contextMenu = null
        }

        override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
            if (contextMenu != null) return
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return
            val station = filteredStationsList[pos]
            contextMenu = StationPopupMenu.open(v!!, fragmentActivity, fragmentActivity, station)
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

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = filteredStationsList[position]
        val shouldLoadIcons = Utils.shouldLoadIcons(fragmentActivity)
        
        // Bind Icon
        if (!shouldLoadIcons) {
            holder.frameLayout.visibility = View.GONE
        } else {
            holder.frameLayout.visibility = View.VISIBLE
            PlayerServiceUtil.getStationIcon(holder.imageViewIcon, if (station.hasIcon()) station.IconUrl else null)
            
            val playListener = View.OnClickListener { holder.onClick(it) }
            holder.imageViewIcon.setOnClickListener(playListener)
            holder.frameLayout.setOnClickListener(playListener)
        }
        
        // Bind Name
        holder.textViewTitle.text = station.Name
        
        // Bind Star
        val isInFavorites = (fragmentActivity.application as com.ounben.amaradio.AMARadioApp).favouriteManager.has(station.StationUuid)
        holder.starredStatusIcon.setImageResource(if (isInFavorites) R.drawable.ic_star_24dp else R.drawable.ic_star_border_24dp)
        holder.starredStatusIcon.setOnClickListener {
            if ((fragmentActivity.application as com.ounben.amaradio.AMARadioApp).favouriteManager.has(station.StationUuid)) {
                StationActions.removeFromFavourites(fragmentActivity, it, station)
            } else {
                StationActions.markAsFavourite(fragmentActivity, station)
            }
            notifyItemChanged(holder.bindingAdapterPosition)
        }

        // Highlight playing station
        if (playingStationPosition == position) {
            val tv = TypedValue()
            fragmentActivity.theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, tv, true)
            val gd = GradientDrawable()
            gd.setColor(Color.TRANSPARENT)
            val strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, fragmentActivity.resources.displayMetrics).toInt()
            gd.setStroke(strokeWidth, tv.data)
            holder.itemView.background = gd
        } else {
            val tv = TypedValue()
            fragmentActivity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            holder.itemView.setBackgroundResource(tv.resourceId)
        }
        
        applyScaling(holder)
    }

    private fun applyScaling(holder: StationViewHolder) {
        val factor = UiScaler.getScaleFactor(fragmentActivity)
        val baseSize = 80f // dp
        val pxSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, baseSize * factor, fragmentActivity.resources.displayMetrics).toInt()
        
        holder.frameLayout.layoutParams.width = pxSize
        holder.frameLayout.layoutParams.height = pxSize
        
        holder.textViewTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * factor)

        // Scale the favorite star
        val starBaseSize = 32f // dp
        val starPxSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, starBaseSize * factor, fragmentActivity.resources.displayMetrics).toInt()
        holder.starredStatusIcon.layoutParams.width = starPxSize
        holder.starredStatusIcon.layoutParams.height = starPxSize
    }
}
