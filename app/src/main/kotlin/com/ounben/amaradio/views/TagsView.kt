package com.ounben.amaradio.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.ounben.amaradio.R

class TagsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    fun interface TagSelectionCallback {
        fun onTagSelected(tag: String)
    }

    private class RoundedBackgroundSpan(
        private val mHeight: Int,
        private val mCornerRadius: Int,
        private val mTextHorizontalPadding: Int,
        private val mTextVerticalMargin: Int,
        private val mBackgroundColor: Int,
        private val mTextColor: Int
    ) : ReplacementSpan() {

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            var t = top
            var b = bottom
            t += ((b - t) / 2) - (mHeight / 2)
            b = t + mHeight

            val fm = paint.fontMetrics
            val adjustedY = t + ((mHeight / 2) + ((-fm.top - fm.bottom) / 2))

            val rect = RectF(x, t.toFloat(), x + measureText(paint, text, start, end) + 2 * mTextHorizontalPadding, b.toFloat())
            paint.color = mBackgroundColor
            canvas.drawRoundRect(rect, mCornerRadius.toFloat(), mCornerRadius.toFloat(), paint)
            paint.color = mTextColor
            canvas.drawText(text, start, end, x + mTextHorizontalPadding, adjustedY, paint)
        }

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            if (fm != null) {
                paint.getFontMetricsInt(fm)
                val textHeight = fm.descent - fm.ascent

                val spaceBetweenTopAndText = (mHeight - textHeight) / 2

                val textTop = fm.top
                val bkgTop = fm.top - spaceBetweenTopAndText

                val textBottom = fm.bottom
                val bkgBottom = fm.bottom + spaceBetweenTopAndText

                // Text may be bigger than given height
                val topOfContent = Math.min(textTop, bkgTop)
                val bottomOfContent = Math.max(textBottom, bkgBottom)

                val topOfContentWithPadding = topOfContent - mTextVerticalMargin
                val bottomOfContentWithPadding = bottomOfContent + mTextVerticalMargin

                fm.ascent = topOfContentWithPadding
                fm.descent = bottomOfContentWithPadding
                fm.top = topOfContentWithPadding
                fm.bottom = bottomOfContentWithPadding
            }

            return Math.round(paint.measureText(text, start, end)) + mTextHorizontalPadding * 2
        }

        private fun measureText(paint: Paint, text: CharSequence, start: Int, end: Int): Float {
            return paint.measureText(text, start, end)
        }
    }

    private var mTagBackgroundColor = Color.RED
    private var mCornerRadius = 16
    private var mTagHeight = 20
    private var mTextHorizontalPadding = 8
    private var mTextVerticalMargin = 4
    private var mTagSelectionCallback: TagSelectionCallback? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.TagsView, defStyle, 0)

        mTagBackgroundColor = a.getColor(R.styleable.TagsView_tagBackgroundColor, mTagBackgroundColor)
        mCornerRadius = a.getDimensionPixelSize(R.styleable.TagsView_cornerRadius, mCornerRadius)
        mTagHeight = a.getDimensionPixelSize(R.styleable.TagsView_tagHeight, mTagHeight)
        mTextHorizontalPadding = a.getDimensionPixelSize(R.styleable.TagsView_textHorizontalPadding, mTextHorizontalPadding)
        mTextVerticalMargin = a.getDimensionPixelSize(R.styleable.TagsView_textVerticalMargin, mTextVerticalMargin)

        a.recycle()
    }

    fun setTags(tags: List<String>) {
        val stringBuilder = SpannableStringBuilder()

        val spacing = "  "
        for (tag in tags) {
            val tagWithBufferSpace = tag + spacing
            stringBuilder.append(tagWithBufferSpace)

            val span = RoundedBackgroundSpan(
                mTagHeight, mCornerRadius,
                mTextHorizontalPadding, mTextVerticalMargin, mTagBackgroundColor, currentTextColor
            )
            val start = stringBuilder.length - tagWithBufferSpace.length
            val end = stringBuilder.length - spacing.length
            stringBuilder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(view: View) {
                    mTagSelectionCallback?.onTagSelected(tag)
                }
            }
            stringBuilder.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        text = stringBuilder
        movementMethod = LinkMovementMethod.getInstance()
    }

    fun setTagSelectionCallback(tagSelectionCallback: TagSelectionCallback?) {
        mTagSelectionCallback = tagSelectionCallback
    }
}
