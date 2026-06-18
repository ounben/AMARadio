package com.ounben.amaradio.station

import androidx.fragment.app.FragmentActivity
import com.ounben.amaradio.utils.RecyclerItemMoveAndSwipeHelper

open class ItemAdapaterContextMenuStation(
    fragmentActivity: FragmentActivity,
    resourceId: Int,
    filterType: StationsFilter.FilterType
) : ItemAdapterStation(fragmentActivity, resourceId, filterType),
    RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    // Logic is now moved to base class ItemAdapterStation to support both List and Grid views.
}
