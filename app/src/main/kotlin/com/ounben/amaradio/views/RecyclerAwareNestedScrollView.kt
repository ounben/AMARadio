package com.ounben.amaradio.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.reflect.Field

/**
 * Credits to Alex Lockwood blogpost "Experimenting with Nested Scrolling"
 * Credits to https://stackoverflow.com/questions/31829976/onclick-method-not-working-properly-after-nestedscrollview-scrolled
 *
 * Allows scroll view to have [RecyclerView] alongside with other content in it and be scrolled
 * as expected by user.
 *
 * The NestedScrollView should steal the scroll/fling events away from
 * the RecyclerView if either is true:
 * - the user is dragging their finger down and the RecyclerView is scrolled to the top of its content
 * - the user is dragging their finger up and the NestedScrollView is not scrolled to the bottom of its content.
 */
class RecyclerAwareNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private var mScroller: OverScroller? = getOverScrollerField()
    var isFling = false

    override fun fling(velocityY: Int) {
        super.fling(velocityY)

        if (childCount > 0) {
            ViewCompat.postInvalidateOnAnimation(this)
            isFling = true
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)

        if (isFling) {
            if (Math.abs(t - oldt) <= 3 || t == 0 || t == (getChildAt(0).measuredHeight - measuredHeight)) {
                isFling = false
                mScroller?.abortAnimation()
            }
        }
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (target is RecyclerView) {
            if ((dy < 0 && isRvScrolledToTop(target)) || (dy > 0 && !isNsvScrolledToBottom(this))) {
                scrollBy(0, dy)
                consumed[1] = dy
                return
            }
        }
        super.onNestedPreScroll(target, dx, dy, consumed, type)
    }

    override fun onNestedPreFling(target: View, velX: Float, velY: Float): Boolean {
        if (target is RecyclerView) {
            if ((velY < 0 && isRvScrolledToTop(target)) || (velY > 0 && !isNsvScrolledToBottom(this))) {
                fling(velY.toInt())
                return true
            }
        }
        return super.onNestedPreFling(target, velX, velY)
    }

    private fun isNsvScrolledToBottom(nsv: NestedScrollView): Boolean {
        return !nsv.canScrollVertically(1)
    }

    private fun isRvScrolledToTop(rv: RecyclerView): Boolean {
        val lm = rv.layoutManager as? LinearLayoutManager ?: return false
        val firstView = lm.findViewByPosition(0) ?: return false
        return lm.findFirstVisibleItemPosition() == 0 && firstView.top == 0
    }

    private fun getOverScrollerField(): OverScroller? {
        return try {
            val fs: Field = NestedScrollView::class.java.getDeclaredField("mScroller")
            fs.isAccessible = true
            fs.get(this) as OverScroller
        } catch (t: Throwable) {
            null
        }
    }
}
