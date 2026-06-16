package com.ounben.amaradio.utils

import android.content.Context
import android.graphics.Canvas
import androidx.recyclerview.widget.RecyclerView

class RecyclerItemMoveAndSwipeHelper<ViewHolderType : SwipeableViewHolder>(
    context: Context,
    dragDirs: Int,
    swipeDirs: Int,
    private val moveAndSwipeListener: MoveAndSwipeCallback<ViewHolderType>
) : RecyclerItemSwipeHelper<ViewHolderType>(context, dragDirs, swipeDirs, moveAndSwipeListener) {

    interface MoveAndSwipeCallback<ViewHolderType> : SwipeCallback<ViewHolderType> {
        fun onDragged(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Double, dY: Double)
        fun onMoved(viewHolder: ViewHolderType, from: Int, to: Int)
        fun onMoveEnded(viewHolder: ViewHolderType)
    }

    override fun isLongPressDragEnabled(): Boolean = true

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = true

    override fun onMoved(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        fromPos: Int,
        target: RecyclerView.ViewHolder,
        toPos: Int,
        x: Int,
        y: Int
    ) {
        @Suppress("UNCHECKED_CAST")
        val viewHolderType = viewHolder as ViewHolderType
        moveAndSwipeListener.onMoved(viewHolderType, fromPos, toPos)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        @Suppress("UNCHECKED_CAST")
        val viewHolderType = viewHolder as ViewHolderType
        super.clearView(recyclerView, viewHolder)
        moveAndSwipeListener.onMoveEnded(viewHolderType)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        moveAndSwipeListener.onDragged(recyclerView, viewHolder, dX.toDouble(), dY.toDouble())
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
