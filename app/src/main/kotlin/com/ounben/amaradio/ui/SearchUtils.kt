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
        
        if (name == q) return 10000.0 // Absolute Perfektion
        
        var score = 0.0

        // 1. FULL START BONUS: Der gesamte Name fängt mit der Suche an
        if (name.startsWith(q)) {
            score += 2000.0
        }
        
        // 2. Word Boundary Logic: Höchste Priorität für Wortanfänge
        val words = name.split(" ", "-", "_", ".", "(", ")", "/", "[", "]", "!", "?").filter { it.isNotEmpty() }
        
        words.forEachIndexed { index, word ->
            if (word.startsWith(q)) {
                // Wortanfang Treffer:
                // - Erstes Wort bekommt massiven Bonus (1000)
                // - Folgeworte bekommen hohen Bonus (500)
                score += if (index == 0) 1000.0 else 500.0
            } else if (word.contains(q)) {
                // Treffer mitten im Wort: Sehr geringe Priorität (maximal 5 Punkte)
                // Wir bestrafen die Position: Je später im Wort, desto weniger Punkte.
                val position = word.indexOf(q)
                score += (1.0 / (position + 1)) * 5.0
            }
        }
        
        // 3. Length Proximity: Bei Punktegleichstand gewinnt das kürzere (präzisere) Wort
        val lengthDiff = Math.abs(name.length - q.length)
        score += (1.0 / (lengthDiff + 1)) * 10.0
        
        return score
    }
}
