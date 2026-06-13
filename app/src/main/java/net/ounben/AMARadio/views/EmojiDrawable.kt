package net.ounben.AMARadio.views

import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint

class EmojiDrawable(private val emoji: String) : Drawable() {
    private val paint = TextPaint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        color = Color.BLACK // Default
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        
        // Use a bit less than full height to avoid clipping
        paint.textSize = bounds.height().toFloat() * 0.8f
        
        // Ensure color is visible based on theme if not set
        // But better: let the caller set it or use a default
        
        val metrics = paint.fontMetrics
        val x = bounds.centerX().toFloat()
        // Center vertically
        val y = bounds.centerY().toFloat() - (metrics.ascent + metrics.descent) / 2
        
        canvas.drawText(emoji, x, y, paint)
    }

    fun setColor(color: Int) {
        paint.color = color
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
