package com.ounben.amaradio.ui

object SearchUtils {
    /**
     * Calculates a relevance score for a target name based on multiple query words.
     * Prioritizes word starts and exact matches.
     */
    fun calculateMultiWordScore(targetName: String, queryWords: List<String>): Double {
        if (queryWords.isEmpty()) return 0.0
        val name = targetName.lowercase().trim()
        val wordsInName = name.split(Regex("""[\s\-_.\(\)/\[\]!?]+""")).filter { it.isNotEmpty() }
        
        var totalScore = 0.0

        for (q in queryWords) {
            val query = q.lowercase().trim()
            if (query.isEmpty()) continue

            var wordScore = 0.0
            
            // 1. MASSIVE BONUS: Wort fängt exakt so an wie ein Wort im Namen
            // Das sorgt dafür, dass "tle" -> "Tlemcen" (Wortanfang) gewinnt
            wordsInName.forEachIndexed { index, word ->
                if (word.startsWith(query)) {
                    wordScore += if (index == 0) 5000.0 else 2000.0
                    // Bonus für exakte Wort-Übereinstimmung
                    if (word == query) wordScore += 1000.0
                } else if (word.contains(query)) {
                    // Treffer mitten im Wort (z.B. "tle" in "Beatle")
                    // Bekommt nur sehr wenig Punkte (max 100)
                    val pos = word.indexOf(query)
                    wordScore += (1.0 / (pos + 1)) * 100.0
                }
            }

            // 2. GLOBAL START BONUS: Der gesamte String fängt so an
            if (name.startsWith(query)) {
                wordScore += 3000.0
            }

            totalScore += wordScore
        }

        // Tie-Breaker: Kürzere Namen sind bei gleicher Punktzahl relevanter
        totalScore += (1.0 / (name.length + 1)) * 10.0

        return totalScore
    }

    /** Legacy support for single string queries */
    fun calculateScore(targetName: String, query: String): Double {
        val words = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        return calculateMultiWordScore(targetName, words)
    }
}
