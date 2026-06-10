package net.ounben.AMARadio.views

import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint

class EmojiDrawable(private val emoji: String) : Drawable() {
    private val paint = TextPaint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        
        // Use full height for text size
        paint.textSize = bounds.height().toFloat()
        
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val yOffset = textHeight / 2 - metrics.descent
        
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat() + yOffset
        
        canvas.drawText(emoji, x, y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int {
        paint.textSize = 100f
        return paint.measureText(emoji).toInt()
    }

    override fun getIntrinsicHeight(): Int = 100
}
