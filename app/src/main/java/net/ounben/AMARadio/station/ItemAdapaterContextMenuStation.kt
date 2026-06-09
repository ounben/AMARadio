package net.ounben.AMARadio.station

import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import net.ounben.AMARadio.utils.RecyclerItemMoveAndSwipeHelper
import net.ounben.AMARadio.utils.SwipeableViewHolder

open class ItemAdapaterContextMenuStation(
    fragmentActivity: FragmentActivity,
    resourceId: Int,
    filterType: StationsFilter.FilterType
) : ItemAdapterStation(fragmentActivity, resourceId, filterType),
    RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    private var timeLastDragEnded: Long = 0

    override fun onDragged(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Double, dY: Double) {
        val foregroundView = (viewHolder as SwipeableViewHolder).foregroundView ?: return
        val stationViewHolder = viewHolder as? ItemAdapterIconOnlyStation.IconOnlyStationViewHolder ?: return

        if (Math.abs(dX) > foregroundView.width * DISMISS_MENU_DRAG_THRESHOLD ||
            Math.abs(dY) > foregroundView.height * DISMISS_MENU_DRAG_THRESHOLD) {
            stationViewHolder.dismissContextMenu()
        } else {
            // How to access contextMenu from here? It was private in my IconOnlyStationViewHolder conversion.
            // I should make it public or provide a check.
            if (System.currentTimeMillis() > timeLastDragEnded + MIN_INTERVAL_BETWEEN_DRAG_AND_MENU_OPEN) {
                Log.d(TAG, "Creating contextMenu from onDragged")
                stationViewHolder.onCreateContextMenu(null!!, foregroundView, null)
            }
        }
    }

    override fun onMoveEnded(viewHolder: StationViewHolder) {
        timeLastDragEnded = System.currentTimeMillis()
        super.onMoveEnded(viewHolder)
    }

    companion object {
        private const val TAG = "IconOnlyStation"
        private const val MIN_INTERVAL_BETWEEN_DRAG_AND_MENU_OPEN = 200L
        private const val DISMISS_MENU_DRAG_THRESHOLD = 0.15
        private const val NEVER_IN_THE_FUTURE = Long.MAX_VALUE / 2
    }
}
