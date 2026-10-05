package dev.holdthisthought.data

import dev.holdthisthought.data.local.CaptureInput
import dev.holdthisthought.data.local.KindSource
import dev.holdthisthought.data.local.SyncState
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtDao
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The only way the UI reads or writes thoughts. Every write lands in Room first and
 * is marked PENDING; cloud sync happens later and never blocks the user.
 */
class ThoughtRepository(
    private val dao: ThoughtDao,
    private val requestSync: () -> Unit,
) {
    fun observe(
        query: String = "",
        kind: ThoughtKind? = null,
        space: ThoughtSpace? = null,
        includeDone: Boolean = false,
    ): Flow<List<Thought>> = dao.observe(query.trim(), kind, space, includeDone)

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun get(id: String): Thought? = dao.get(id)

    fun observeOne(id: String): Flow<Thought?> = dao.observeOne(id)

    suspend fun getVisible(ids: List<String>): List<Thought> =
        if (ids.isEmpty()) emptyList() else dao.getVisible(ids)

    suspend fun allVisible(): List<Thought> = dao.allVisible()

    /** Saves a new capture. [kind] null means "let the app decide". */
    suspend fun capture(
        body: String,
        space: ThoughtSpace,
        kind: ThoughtKind? = null,
        input: CaptureInput = CaptureInput.TEXT,
    ): Thought? {
        val cleaned = body.trim()
        if (cleaned.isEmpty()) return null
        val now = System.currentTimeMillis()
        val thought = Thought(
            id = UUID.randomUUID().toString(),
            body = cleaned,
            kind = kind ?: LocalClassifier.classify(cleaned),
            kindSource = if (kind != null) KindSource.USER else KindSource.AUTO,
            space = space,
            input = input,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(thought)
        requestSync()
        return thought
    }

    suspend fun edit(id: String, transform: (Thought) -> Thought) {
        val current = dao.get(id) ?: return
        val updated = transform(current)
        if (updated == current) return
        dao.upsert(
            updated.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING,
            )
        )
        requestSync()
    }

    suspend fun updateBody(id: String, body: String) {
        val cleaned = body.trim()
        if (cleaned.isEmpty()) return
        edit(id) { it.copy(body = cleaned) }
    }

    suspend fun setKind(id: String, kind: ThoughtKind) =
        edit(id) { it.copy(kind = kind, kindSource = KindSource.USER) }

    suspend fun setSpace(id: String, space: ThoughtSpace) = edit(id) { it.copy(space = space) }

    suspend fun setDone(id: String, done: Boolean) = edit(id) {
        it.copy(completedAt = if (done) it.completedAt ?: System.currentTimeMillis() else null)
    }

    suspend fun delete(id: String) = edit(id) { it.copy(deletedAt = System.currentTimeMillis()) }

    suspend fun restore(id: String) = edit(id) { it.copy(deletedAt = null) }
}
