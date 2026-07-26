package dev.holdthisthought.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ThoughtRepository(
    private val dao: ThoughtDao,
    private val scheduleSync: () -> Unit = {},
) {
    fun observe(query: String = "", kind: ThoughtKind? = null): Flow<List<Thought>> =
        dao.observeVisible(query.trim(), kind)

    suspend fun capture(body: String, space: ThoughtSpace) {
        val cleaned = body.trim()
        if (cleaned.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsert(
            Thought(
                id = UUID.randomUUID().toString(),
                body = cleaned,
                kind = classifyLocally(cleaned),
                space = space,
                createdAt = now,
                updatedAt = now,
            )
        )
        scheduleSync()
    }

    suspend fun pendingSync(): List<Thought> = dao.pendingSync()

    fun requestSync() = scheduleSync()

    suspend fun markSynced(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markSynced(ids)
    }

    suspend fun updateKinds(kinds: Map<String, ThoughtKind>) {
        kinds.forEach { (id, kind) -> dao.updateKind(id, kind) }
    }

    suspend fun repairLocalClassifications() {
        dao.genericThoughts().forEach { thought ->
            val improvedKind = classifyLocally(thought.body)
            if (improvedKind != ThoughtKind.THOUGHT) {
                dao.updateKind(thought.id, improvedKind)
            }
        }
    }

    private fun classifyLocally(text: String): ThoughtKind {
        val normalized = text.lowercase()
        return when {
            normalized.startsWith("todo") ||
                normalized.startsWith("to do") ||
                normalized.startsWith("remind me") ||
                normalized.contains("i need to") ||
                normalized.startsWithTaskVerb() -> ThoughtKind.TODO
            normalized.startsWith("note") ||
                normalized.startsWith("remember that") -> ThoughtKind.NOTE
            else -> ThoughtKind.THOUGHT
        }
    }

    private fun String.startsWithTaskVerb(): Boolean {
        val firstWord = substringBefore(' ')
        return firstWord in setOf(
            "buy", "call", "clean", "clear", "email", "finish", "fix", "get", "make",
            "pay", "pick", "prepare", "read", "reply", "schedule", "send",
            "submit", "update", "wash", "write",
        )
    }
}
