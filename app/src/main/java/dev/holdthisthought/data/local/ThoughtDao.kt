package dev.holdthisthought.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ThoughtDao {
    @Upsert
    suspend fun upsert(thought: Thought)

    @Upsert
    suspend fun upsertAll(thoughts: List<Thought>)

    @Query("SELECT * FROM thoughts WHERE id = :id")
    suspend fun get(id: String): Thought?

    @Query("SELECT * FROM thoughts WHERE id = :id")
    fun observeOne(id: String): Flow<Thought?>

    @Query("SELECT * FROM thoughts WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getVisible(ids: List<String>): List<Thought>

    @Query(
        """
        SELECT * FROM thoughts
        WHERE deletedAt IS NULL
          AND (:includeDone OR completedAt IS NULL)
          AND (:kind IS NULL OR kind = :kind)
          AND (:space IS NULL OR space = :space)
          AND (:query = '' OR body LIKE '%' || :query || '%'
               OR title LIKE '%' || :query || '%'
               OR tags LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
        """
    )
    fun observe(
        query: String,
        kind: ThoughtKind?,
        space: ThoughtSpace?,
        includeDone: Boolean,
    ): Flow<List<Thought>>

    @Query("SELECT * FROM thoughts WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun allVisible(): List<Thought>

    @Query("SELECT * FROM thoughts WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeAllVisible(): Flow<List<Thought>>

    @Query("SELECT * FROM thoughts WHERE syncState = 'PENDING' ORDER BY updatedAt ASC")
    suspend fun pending(): List<Thought>

    @Query("SELECT COUNT(*) FROM thoughts WHERE syncState = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    /** Only marks a row synced if it was not edited again while the upload was in flight. */
    @Query("UPDATE thoughts SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    @Query("UPDATE thoughts SET syncState = 'PENDING'")
    suspend fun markAllPending()

    @Query("DELETE FROM thoughts")
    suspend fun hardDeleteAll()

    @Query("DELETE FROM thoughts WHERE id = :id")
    suspend fun hardDelete(id: String)

    /** Moves a row to a new id, e.g. when its old id belongs to another cloud account. */
    @Transaction
    suspend fun rekey(oldId: String, moved: Thought) {
        hardDelete(oldId)
        upsert(moved)
    }

    @Transaction
    suspend fun replaceAll(thoughts: List<Thought>) {
        hardDeleteAll()
        upsertAll(thoughts)
    }
}
