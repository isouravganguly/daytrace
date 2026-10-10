package dev.holdthisthought.data

import dev.holdthisthought.data.local.ThoughtKind

/**
 * Instant, offline first guess at a capture's kind. The cloud AI refines it later
 * unless the user picked the kind themselves.
 */
object LocalClassifier {
    private val taskVerbs = setOf(
        "buy", "call", "book", "check", "clean", "clear", "email", "finish", "fix", "get",
        "make", "message", "pay", "pick", "prepare", "read", "renew", "reply", "schedule",
        "send", "submit", "text", "update", "wash", "write",
    )

    private val learningOpeners = listOf(
        "til", "today i learned", "i learned", "i learnt", "learned", "learnt", "learning:",
        "lesson", "note", "remember that", "turns out", "apparently", "tip:", "fyi",
    )

    private val learningPhrases = listOf(
        "the trick is", "the reason is", "works by", "is because", "the way to",
        "you can use", "you have to", "always use", "never use", "don't forget",
    )

    private val desireOpeners = listOf(
        "i want", "i wish", "i'd love", "i would love", "someday", "one day i", "i dream",
        "i'd like to", "i would like to", "wish:", "desire:", "i hope to",
    )

    fun classify(text: String): ThoughtKind {
        val normalized = text.trim().lowercase()
        val firstWord = normalized.substringBefore(' ').trimEnd(',', ':', '.')
        return when {
            desireOpeners.any { normalized.startsWith(it) } -> ThoughtKind.DESIRE
            normalized.startsWith("todo") ||
                normalized.startsWith("to do") ||
                normalized.startsWith("remind me") ||
                normalized.contains("i need to") ||
                normalized.contains("i have to") ||
                normalized.contains("don't forget to") ||
                firstWord in taskVerbs -> ThoughtKind.TODO
            learningOpeners.any { normalized.startsWith(it) } ||
                learningPhrases.any { normalized.contains(it) } -> ThoughtKind.LEARNING
            else -> ThoughtKind.THOUGHT
        }
    }
}
