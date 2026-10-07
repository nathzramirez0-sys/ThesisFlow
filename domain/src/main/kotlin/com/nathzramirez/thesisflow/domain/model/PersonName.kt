package com.nathzramirez.thesisflow.domain.model

/**
 * Short forms of a display name. Advisers often include a title ("Prof. Liza
 * Reyes", "Engr. Ramon Cruz"), which shouldn't become a greeting or initials.
 */
object PersonName {

    /** Titles common in Philippine schools, compared without case or trailing dot. */
    private val TITLES = setOf(
        "prof", "professor", "dr", "doc", "engr", "atty", "arch", "mr", "mrs", "ms", "miss",
        "sir", "maam", "ma'am", "madam", "rev", "fr", "hon",
    )

    /** The name's words without leading titles; a name that is only a title is kept as typed. */
    fun words(name: String): List<String> {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.dropWhile { it.trimEnd('.', ',').lowercase() in TITLES }.ifEmpty { words }
    }

    /** "Prof. Liza Reyes" → "Liza". */
    fun firstName(name: String): String = words(name).firstOrNull().orEmpty()

    /** First and last name initials: "Prof. Liza Reyes" → "LR", "Juan Dela Cruz" → "JC". */
    fun initials(name: String): String {
        val words = words(name)
        val picked = if (words.size > 1) listOf(words.first(), words.last()) else words
        return picked.mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }
}
