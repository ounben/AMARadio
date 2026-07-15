package com.ounben.amaradio.utils

import java.util.*

/**
 * Utility class to generate dynamic placeholders for radio stations.
 */
object StationPlaceholderUtils {

    // Defined Material Design color palette (AARRGGBB)
    private val MATERIAL_PALETTE = longArrayOf(
        0xFF2196F3, // Blue
        0xFFF44336, // Red
        0xFF4CAF50, // Green
        0xFFFF9800, // Orange
        0xFF9C27B0, // Purple
        0xFF009688, // Teal
        0xFFE91E63  // Pink
    )

    /**
     * Extracts the appropriate text for the placeholder based on the station name.
     */
    fun extractPlaceholderText(name: String?): String {
        val input = name?.trim() ?: return "?"
        if (input.isEmpty()) return "?"

        // 1. Search for the first contiguous number
        val numberRegex = Regex("""(\d+)""")
        val numberMatch = numberRegex.find(input)
        if (numberMatch != null) {
            return numberMatch.value
        }

        // 2. Extraction of initials or word parts
        val words = input.split(Regex("""\s+""")).filter { it.isNotBlank() }
        
        return if (words.size >= 2) {
            // Initials of the first two words
            (words[0].take(1) + words[1].take(1)).uppercase(Locale.ROOT)
        } else {
            // First two letters of the single word
            input.take(2).uppercase(Locale.ROOT)
        }
    }

    /**
     * Determines a deterministic color based on the stationUuid.
     */
    fun getPlaceholderColor(stationUuid: String?): Long {
        if (stationUuid.isNullOrEmpty()) return MATERIAL_PALETTE[0]

        // Ensure a positive index using bitwise AND with 0x7FFFFFFF
        val hash = stationUuid.hashCode() and 0x7FFFFFFF
        val index = hash % MATERIAL_PALETTE.size
        
        return MATERIAL_PALETTE[index]
    }

    /**
     * Creates a placeholder bitmap with text and background color.
     */
    fun createPlaceholderBitmap(name: String, uuid: String, size: Int = 512): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Background color
        val color = getPlaceholderColor(uuid).toInt()
        canvas.drawColor(color)
        
        // Text
        val text = extractPlaceholderText(name)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            this.textSize = size / 2.5f
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, xPos, yPos, paint)
        
        return bitmap
    }
}
