package dev.holdthisthought.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TraceDao {
    // ---------- check-ins ----------

    @Upsert
    suspend fun upsertEntry(entry: Entry)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: String): Entry?

    @Query("SELECT * FROM entries WHERE deletedAt IS NULL ORDER BY occurredAt")
    fun observeEntries(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE deletedAt IS NULL ORDER BY occurredAt")
    suspend fun allEntries(): List<Entry>

    // ---------- app usage ----------

    /** Duplicates (same app and start) are ignored, so re-processing events is safe. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIntervals(intervals: List<UsageInterval>)

    @Query("SELECT * FROM usage_intervals WHERE uploaded = 0 ORDER BY startedAt LIMIT :limit")
    suspend fun intervalsToUpload(limit: Int): List<UsageInterval>

    @Query("UPDATE usage_intervals SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM usage_intervals WHERE uploaded = 0")
    fun observeUploadQueueSize(): Flow<Int>

    @Query("SELECT * FROM usage_intervals WHERE endedAt > :from AND startedAt < :to ORDER BY startedAt")
    fun observeIntervals(from: Long, to: Long): Flow<List<UsageInterval>>

    @Query("SELECT * FROM usage_intervals WHERE endedAt > :from AND startedAt < :to ORDER BY startedAt")
    suspend fun intervalsBetween(from: Long, to: Long): List<UsageInterval>

    @Query("DELETE FROM usage_intervals WHERE uploaded = 1 AND endedAt < :before")
    suspend fun pruneUploaded(before: Long)

    // ---------- wake-ups ----------

    @Upsert
    suspend fun upsertWakeup(wakeup: Wakeup)

    @Query("SELECT * FROM wakeups WHERE day = :day")
    suspend fun wakeup(day: String): Wakeup?

    @Query("SELECT * FROM wakeups ORDER BY day")
    fun observeWakeups(): Flow<List<Wakeup>>

    @Query("SELECT * FROM wakeups ORDER BY day")
    suspend fun allWakeups(): List<Wakeup>

    // ---------- health (Health Connect) ----------

    @Upsert
    suspend fun upsertHealth(days: List<HealthDay>)

    @Query("SELECT * FROM health_days ORDER BY day")
    fun observeHealth(): Flow<List<HealthDay>>

    @Query("SELECT * FROM health_days ORDER BY day")
    suspend fun allHealth(): List<HealthDay>

    @Query("SELECT * FROM health_days WHERE uploaded = 0 ORDER BY day")
    suspend fun healthToUpload(): List<HealthDay>

    @Query("UPDATE health_days SET uploaded = 1 WHERE day IN (:days) AND updatedAt <= :before")
    suspend fun markHealthUploaded(days: List<String>, before: Long)
}
