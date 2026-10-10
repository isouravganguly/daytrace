package dev.holdthisthought.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Quick check-ins. New types are additive; [Entry.value] is interpreted per type. */
enum class EntryType {
    /** value: 1 (low) … 5 (great). */
    MOOD,

    /** value: a [MealSlot] name. */
    MEAL,

    /** value: minutes walked, or "0" when only "I walked" was ticked. */
    WALK,

    /** The day's verdict. value: 1 … 5; note: one line about the day. */
    VERDICT,

    /** "Good night" tapped: going to sleep now. value: "". */
    BEDTIME,
}

enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

@Entity(
    tableName = "entries",
    indices = [Index("occurredAt"), Index("syncState")],
)
data class Entry(
    @PrimaryKey val id: String,
    val type: EntryType,
    val value: String,
    val note: String? = null,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

/** One stretch of time with one app in the foreground. Also the upload queue for /ingest. */
@Entity(
    tableName = "usage_intervals",
    indices = [
        Index(value = ["app", "startedAt"], unique = true),
        Index("uploaded"),
        Index("startedAt"),
    ],
)
data class UsageInterval(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    /** Human label ("YouTube"), which is what the server stores. */
    val app: String,
    val startedAt: Long,
    val endedAt: Long,
    val uploaded: Boolean = false,
)

enum class WakeupState { PENDING, LOGGED }

/** At most one per local day. PENDING = tapped but the server hasn't confirmed yet. */
@Entity(tableName = "wakeups")
data class Wakeup(
    /** Local date, ISO yyyy-MM-dd. */
    @PrimaryKey val day: String,
    val tappedAt: Long,
    /** Server-confirmed wake time. */
    val wokeAt: Long? = null,
    val greeting: String? = null,
    val state: WakeupState = WakeupState.PENDING,
) {
    /** Best known wake time: the server's once confirmed, the tap time until then. */
    val time: Long get() = wokeAt ?: tappedAt
}

/**
 * One day of Health Connect aggregates (from Google Fit, Mi Health, MyFitnessPal, ...).
 * Re-read for the last few days on every sync; [uploaded] is reset whenever values change.
 */
@Entity(tableName = "health_days")
data class HealthDay(
    /** Local date, ISO yyyy-MM-dd. */
    @PrimaryKey val day: String,
    val steps: Int = 0,
    /** Walking, hiking and running sessions. */
    val walkMinutes: Int = 0,
    /** All exercise sessions. */
    val exerciseMinutes: Int = 0,
    /** Sleep sessions that ended on this day. */
    val sleepMinutes: Int = 0,
    val sleepStart: Long? = null,
    val sleepEnd: Long? = null,
    /** [MealSlot] names logged in a nutrition app. */
    val meals: List<String> = emptyList(),
    val calories: Int? = null,
    val updatedAt: Long = 0,
    val uploaded: Boolean = false,
) {
    /** Same values, ignoring bookkeeping fields. */
    fun sameDataAs(other: HealthDay) =
        copy(updatedAt = 0, uploaded = false) == other.copy(updatedAt = 0, uploaded = false)
}
