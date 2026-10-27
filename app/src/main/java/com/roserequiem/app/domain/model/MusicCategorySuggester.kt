package com.roserequiem.app.domain.model

/**
 * Explainable, on-device category suggestion for a local audio track.
 * The reason is shown to the user so this remains a suggestion, not an opaque decision.
 */
data class MusicCategorySuggestion(
    val category: String,
    val reason: String,
    val confidence: Float,
)

object MusicCategorySuggester {
    private data class Rule(
        val category: String,
        val reason: String,
        val keywords: Set<String>,
        val confidence: Float,
    )

    private val rules = listOf(
        Rule("Focus", "Title or file name suggests study, focus, ambient, piano, or lo-fi music.",
            setOf("study", "focus", "ambient", "piano", "lofi", "lo-fi", "instrumental", "soundtrack"), 0.82f),
        Rule("Relax", "Title or file name suggests calm, sleep, rain, meditation, or relaxation.",
            setOf("calm", "relax", "sleep", "rain", "meditation", "meditate", "peaceful", "soft"), 0.82f),
        Rule("Workout", "Title or file name suggests high energy, training, running, or workout music.",
            setOf("workout", "training", "run", "running", "energy", "power", "pump", "fast", "remix"), 0.78f),
        Rule("Romance", "Title or file name suggests love, romance, or a ballad.",
            setOf("love", "lover", "heart", "romance", "romantic", "kiss", "forever", "ballad"), 0.74f),
        Rule("Night", "Title or file name suggests night, midnight, rain, or a melancholic mood.",
            setOf("night", "midnight", "moon", "dark", "blue", "alone", "goodbye", "sad"), 0.70f),
    )

    fun suggest(title: String, artist: String, fileName: String): MusicCategorySuggestion? {
        val searchable = listOf(title, artist, fileName)
            .joinToString(" ")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        if (searchable.isBlank()) return null

        val words = searchable.split(Regex("\\s+")).toSet()
        return rules.firstNotNullOfOrNull { rule ->
            val matched = rule.keywords.firstOrNull { keyword ->
                if (keyword.contains('-')) searchable.contains(keyword)
                else keyword in words
            }
            if (matched == null) null
            else MusicCategorySuggestion(
                category = rule.category,
                reason = "${rule.reason} Matched keyword: “$matched”.",
                confidence = rule.confidence,
            )
        }
    }
}
