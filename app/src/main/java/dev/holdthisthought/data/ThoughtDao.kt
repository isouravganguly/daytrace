package dev.holdthisthought.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThoughtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thought: Thought)

    @Query(
        """
        SELECT * FROM thoughts
        WHERE completedAt IS NULL
          AND (:query = '' OR body LIKE '%' || :query || '%')
          AND (:kind IS NULL OR kind = :kind)
        ORDER BY createdAt DESC
        """
    )
    fun observeVisible(query: String, kind: ThoughtKind?): Flow<List<Thought>>

    @Query("SELECT * FROM thoughts WHERE syncState = 'PENDING' ORDER BY createdAt")
    suspend fun pendingSync(): List<Thought>

    @Query("UPDATE thoughts SET syncState = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE thoughts SET kind = :kind WHERE id = :id")
    suspend fun updateKind(id: String, kind: ThoughtKind)

    @Query("SELECT * FROM thoughts WHERE kind = 'THOUGHT'")
    suspend fun genericThoughts(): List<Thought>
}
