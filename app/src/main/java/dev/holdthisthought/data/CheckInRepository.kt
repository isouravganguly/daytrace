package dev.holdthisthought.data

import dev.holdthisthought.data.local.Entry
import dev.holdthisthought.data.local.EntryType
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.data.local.TraceDao
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Mood, meals, walks and the day's verdict. Local for now; cloud sync is phase 2. */
class CheckInRepository(private val dao: TraceDao) {
    val entries: Flow<List<Entry>> = dao.observeEntries()

    suspend fun allEntries(): List<Entry> = dao.allEntries()

    suspend fun logMood(score: Int, note: String? = null) = log(EntryType.MOOD, score.coerceIn(1, 5).toString(), note)

    suspend fun logMeal(slot: MealSlot, note: String? = null) = log(EntryType.MEAL, slot.name, note)

    suspend fun logWalk(minutes: Int = 0, note: String? = null) = log(EntryType.WALK, minutes.toString(), note)

    suspend fun logVerdict(score: Int, note: String?) = log(EntryType.VERDICT, score.coerceIn(1, 5).toString(), note)

    suspend fun logBedtime() = log(EntryType.BEDTIME, "", null)

    /** Soft delete, so undo is just [restore]. */
    suspend fun delete(id: String) = edit(id) { it.copy(deletedAt = System.currentTimeMillis()) }

    suspend fun restore(id: String) = edit(id) { it.copy(deletedAt = null) }

    private suspend fun log(type: EntryType, value: String, note: String?): Entry {
        val now = System.currentTimeMillis()
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            type = type,
            value = value,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertEntry(entry)
        return entry
    }

    private suspend fun edit(id: String, transform: (Entry) -> Entry) {
        val entry = dao.entry(id) ?: return
        dao.upsertEntry(transform(entry).copy(updatedAt = System.currentTimeMillis()))
    }
}
