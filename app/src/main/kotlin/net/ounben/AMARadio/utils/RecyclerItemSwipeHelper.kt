package net.ounben.AMARadio.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import net.ounben.AMARadio.R
import net.ounben.AMARadio.Utils

open class RecyclerItemSwipeHelper<ViewHolderType : SwipeableViewHolder>(
    context: Context,
    dragDirs: Int,
    swipeDirs: Int,
    private val swipeListener: SwipeCallback<ViewHolderType>
) : ItemTouchHelper.SimpleCallback(dragDirs, swipeDirs) {

    fun interface SwipeCallback<ViewHolderType> {
        fun onSwiped(viewHolder: ViewHolderType, direction: Int)
    }

    private val swipeToDeleteIsEnabled: Boolean = ((swipeDirs and ItemTouchHelper.LEFT) > 0) || ((swipeDirs and ItemTouchHelper.RIGHT) > 0)
    private var icon: Drawable? = null
    private val background: ColorDrawable = ColorDrawable(Utils.themeAttributeToColor(R.attr.swipeDeleteBackgroundColor, context, Color.RED))

    init {
        if (swipeToDeleteIsEnabled) {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_delete_sweep_24dp)?.mutate()
            icon?.setTint(Utils.themeAttributeToColor(R.attr.swipeDeleteIconColor, context, Color.WHITE))
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (viewHolder is SwipeableViewHolder) {
            viewHolder.foregroundView?.let {
                getDefaultUIUtil().onSelected(it)
            }
        }
    }

    override fun onChildDrawOver(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (viewHolder is SwipeableViewHolder) {
            viewHolder.foregroundView?.let {
                getDefaultUIUtil().onDrawOver(c, recyclerView, it, dX, dY, actionState, isCurrentlyActive)
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder is SwipeableViewHolder) {
            viewHolder.foregroundView?.let {
                getDefaultUIUtil().clearView(it)
            }
        }
    }

    private fun drawSwipeToDeleteBackground(c: Canvas, itemView: android.view.View, dX: Float, dY: Float) {
        val currentIcon = icon ?: return
        val iconSize = (48 * itemView.context.resources.displayMetrics.density).toInt()
        val iconMargin = (itemView.height - iconSize) / 2
        val iconTop = itemView.top + iconMargin
        val iconBottom = iconTop + iconSize

        if (dX > 0) { // Swiping to the right
            var iconRight = itemView.left + iconMargin + iconSize
            var iconLeft = itemView.left + iconMargin

            val magicConstraint = if (itemView.left + dX.toInt() < iconRight + iconMargin) dX.toInt() - iconSize - (iconMargin * 2) else 0
            iconLeft += magicConstraint
            iconRight += magicConstraint
            currentIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

            background.setBounds(
                itemView.left, itemView.top,
                itemView.left + dX.toInt(),
                itemView.bottom
            )
        } else if (dX < 0) { // Swiping to the left
            var iconRight = itemView.right - iconMargin
            var iconLeft = itemView.right - iconMargin - iconSize

            val magicConstraint = if (itemView.right + dX.toInt() > iconLeft - iconMargin) iconSize + (iconMargin * 2) + dX.toInt() else 0
            iconLeft += magicConstraint
            iconRight += magicConstraint
            currentIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

            background.setBounds(
                itemView.right + dX.toInt(), itemView.top,
                itemView.right,
                itemView.bottom
            )
        } else { // view is unSwiped
            currentIcon.setBounds(0, 0, 0, 0)
            background.setBounds(0, 0, 0, 0)
        }

        background.draw(c)
        currentIcon.draw(c)
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
        if (viewHolder is SwipeableViewHolder) {
            val foregroundView = viewHolder.foregroundView

            if (swipeToDeleteIsEnabled) {
                drawSwipeToDeleteBackground(c, viewHolder.itemView, dX, dY)
            }

            if (foregroundView != null) {
                getDefaultUIUtil().onDraw(c, recyclerView, foregroundView, dX, dY, actionState, isCurrentlyActive)
            }
        }
    }

    override fun isLongPressDragEnabled(): Boolean = false

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        @Suppress("UNCHECKED_CAST")
        val viewHolderType = viewHolder as ViewHolderType
        swipeListener.onSwiped(viewHolderType, direction)
    }

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = 1f

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.35f
}
