package com.ounben.amaradio.ui

object SearchUtils {
    /**
     * Highly optimized similarity for names (stations, countries, tags).
     * Prioritizes character order and structural matches.
     * Returns a score where higher is better.
     */
    fun calculateScore(targetName: String, query: String): Double {
        val name = targetName.lowercase()
        val q = query.lowercase()
        
        if (name == q) return 1000.0 // Perfection
        
        var score = 0.0
        
        // 1. MASSIVE Bonus if the name actually starts with the query
        if (name.startsWith(q)) {
            score += 500.0
        }
        
        // 2. Word-Start Bonus
        val words = name.split(" ", "-", "_", ".", "(", ")", "/", "[", "]", "!", "?")
        if (words.any { it.startsWith(q) }) {
            score += 200.0
        }
        
        // 3. Sequential Bigram match (Dice's Coefficient logic)
        if (q.length >= 2) {
            val namePairs = name.windowed(2).toSet()
            val queryPairs = q.windowed(2).toSet()
            if (queryPairs.isNotEmpty()) {
                val intersection = namePairs.intersect(queryPairs).size
                score += (2.0 * intersection) / (namePairs.size + queryPairs.size) * 10.0
            }
        }
        
        // 4. Simple contains fallback
        if (name.contains(q)) {
            score += 1.0
        }
        
        return score
    }
}
