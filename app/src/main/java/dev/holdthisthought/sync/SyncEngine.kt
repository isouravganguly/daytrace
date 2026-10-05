package dev.holdthisthought.sync

import android.util.Log
import dev.holdthisthought.data.AppPreferences
import dev.holdthisthought.data.local.CaptureInput
import dev.holdthisthought.data.local.KindSource
import dev.holdthisthought.data.local.SyncState
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtDao
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import dev.holdthisthought.data.remote.RemoteThought
import dev.holdthisthought.data.remote.RemoteThoughtPush
import dev.holdthisthought.data.remote.SupabaseRemoteDataSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Two-way sync between Room and Supabase:
 * 1. push local PENDING rows (user-owned columns only),
 * 2. ask the server to enrich new rows with AI (best effort, never fails the sync),
 * 3. pull everything that changed on the server since the last cursor.
 *
 * Conflict rule: a local row with unsynced edits keeps the user's fields and only
 * adopts AI fields (title, tags, auto kind) from the server.
 */
class SyncEngine(
    private val dao: ThoughtDao,
    private val remote: SupabaseRemoteDataSource,
    private val prefs: AppPreferences,
) {
    private val mutex = Mutex()

    sealed interface Outcome {
        data object Skipped : Outcome
        data object Synced : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    suspend fun sync(): Outcome = mutex.withLock {
        if (!remote.isConfigured) return Outcome.Skipped
        val user = remote.currentUser() ?: return Outcome.Skipped

        runCatching {
            adoptLocalDataIfAccountChanged(user.id)
            push(user.id)
            runCatching { remote.enrichPending() }
                .onFailure { Log.w(TAG, "AI enrichment skipped", it) }
            pull()
            prefs.recordSyncSuccess(System.currentTimeMillis())
        }.fold(
            onSuccess = { Outcome.Synced },
            onFailure = {
                Log.w(TAG, "Sync failed", it)
                prefs.recordSyncError(it.message ?: it::class.java.simpleName)
                Outcome.Failed(it)
            },
        )
    }

    /**
     * Rows on this device that were synced to a different account get new ids, so they
     * become fresh rows in the account now signed in instead of clashing with the old ones.
     */
    private suspend fun adoptLocalDataIfAccountChanged(userId: String) {
        val previous = prefs.syncedUserId
        if (previous == userId) return
        if (previous != null) {
            val rows = dao.allVisible()
            dao.replaceAll(rows.map { it.copy(id = UUID.randomUUID().toString(), syncState = SyncState.PENDING) })
        } else {
            dao.markAllPending()
        }
        prefs.pullCursor = null
        prefs.syncedUserId = userId
    }

    private suspend fun push(userId: String) {
        val pending = dao.pending()
        if (pending.isEmpty()) return
        try {
            remote.push(pending.map { it.toPush(userId) })
            pending.forEach { dao.markSynced(it.id, it.updatedAt) }
        } catch (e: Exception) {
            if (!e.isOwnershipConflict()) throw e
            pushOneByOne(pending, userId)
        }
    }

    /**
     * Some rows already exist in the cloud under another account (for example an anonymous
     * session that was lost), so Row Level Security rejects them. Push rows individually and
     * give each rejected row a new id, so it becomes this account's own copy.
     */
    private suspend fun pushOneByOne(rows: List<Thought>, userId: String) {
        rows.forEach { row ->
            val pushed = try {
                remote.push(listOf(row.toPush(userId)))
                row
            } catch (e: Exception) {
                if (!e.isOwnershipConflict()) throw e
                val copy = row.copy(id = UUID.randomUUID().toString())
                dao.rekey(row.id, copy)
                remote.push(listOf(copy.toPush(userId)))
                copy
            }
            dao.markSynced(pushed.id, pushed.updatedAt)
        }
    }

    private fun Throwable.isOwnershipConflict(): Boolean =
        message?.contains("row-level security", ignoreCase = true) == true

    private suspend fun pull() {
        var cursor = prefs.pullCursor
        while (true) {
            val page = remote.pullChanges(cursor, PAGE_SIZE)
            page.forEach { merge(it) }
            val newCursor = page.lastOrNull()?.serverUpdatedAt ?: break
            prefs.pullCursor = newCursor
            if (page.size < PAGE_SIZE || newCursor == cursor) break
            cursor = newCursor
        }
    }

    private suspend fun merge(row: RemoteThought) {
        val incoming = row.toLocal() ?: return
        val local = dao.get(row.id)
        val merged = when {
            local == null -> incoming
            local.syncState == SyncState.PENDING -> local.copy(
                title = incoming.title,
                tags = incoming.tags,
                kind = if (local.kindSource == KindSource.AUTO && incoming.kindSource == KindSource.AUTO) {
                    incoming.kind
                } else {
                    local.kind
                },
            )
            else -> incoming
        }
        if (merged != local) dao.upsert(merged)
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val PAGE_SIZE = 500L
    }
}

private fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

private fun String.toEpochMillis(): Long = OffsetDateTime.parse(this).toInstant().toEpochMilli()

private fun Thought.toPush(userId: String) = RemoteThoughtPush(
    id = id,
    userId = userId,
    body = body,
    kind = kind.name,
    kindSource = kindSource.name,
    space = space.name,
    input = input.name,
    createdAt = createdAt.toIso(),
    updatedAt = updatedAt.toIso(),
    completedAt = completedAt?.toIso(),
    deletedAt = deletedAt?.toIso(),
)

/** Null when the server row uses a value this app version does not understand. */
private fun RemoteThought.toLocal(): Thought? = runCatching {
    Thought(
        id = id,
        body = body,
        kind = ThoughtKind.valueOf(kind),
        kindSource = KindSource.valueOf(kindSource),
        space = ThoughtSpace.valueOf(space),
        input = CaptureInput.valueOf(input),
        title = title,
        tags = tags,
        createdAt = createdAt.toEpochMillis(),
        updatedAt = updatedAt.toEpochMillis(),
        completedAt = completedAt?.toEpochMillis(),
        deletedAt = deletedAt?.toEpochMillis(),
        syncState = SyncState.SYNCED,
    )
}.getOrNull()
