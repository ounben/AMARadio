package com.ounben.amaradio.ui

object SearchUtils {
    /**
     * Highly optimized similarity for names (stations, countries, tags).
     * Prioritizes character order and structural matches.
     * Returns a score where higher is better.
     */
    fun calculateScore(targetName: String, query: String): Double {
        val name = targetName.lowercase().trim()
        val q = query.lowercase().trim()
        
        if (name == q) return 5000.0 // Perfection
        
        var score = 0.0
        
        // 1. Word Boundary Logic: Higher priority for words starting with query
        // Examples: "TL" matching "Radio Tlemcen" or "Tlemcen Live"
        val words = name.split(" ", "-", "_", ".", "(", ")", "/", "[", "]", "!", "?").filter { it.isNotEmpty() }
        
        words.forEachIndexed { index, word ->
            if (word.startsWith(q)) {
                // Word start match: 
                // - Highest if first word (1000)
                // - Still very high for other words (800)
                score += if (index == 0) 1000.0 else 800.0
            } else if (word.contains(q)) {
                // Contains within word: much lower priority (10)
                score += 10.0
            }
        }
        
        // 2. Acronym/Initial Logic (e.g., "RTL" contains "TL" but shouldn't win)
        // We penalize if the query is just a substring in the middle of a word
        if (!words.any { it.startsWith(q) } && name.contains(q)) {
            score += 1.0 // Minimal point for substring
        }

        // 3. Length Proximity: Closer length to query wins ties
        val lengthDiff = Math.abs(name.length - q.length)
        score += (1.0 / (lengthDiff + 1)) * 5.0
        
        return score
    }
}
