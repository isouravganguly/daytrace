package dev.holdthisthought.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json

class ThoughtConverters {
    @TypeConverter fun tagsToString(value: List<String>): String = Json.encodeToString(value)
    @TypeConverter fun stringToTags(value: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}

@Database(
    entities = [Thought::class, Entry::class, UsageInterval::class, Wakeup::class, HealthDay::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(ThoughtConverters::class)
abstract class ThoughtDatabase : RoomDatabase() {
    abstract fun thoughtDao(): ThoughtDao
    abstract fun traceDao(): TraceDao

    companion object {
        fun create(context: Context): ThoughtDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ThoughtDatabase::class.java,
                "hold-this-thought.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

/**
 * v1 → v2: NOTE becomes LEARNING, adds AI fields, soft delete and capture metadata.
 * Every row is re-marked PENDING so the new columns reach the cloud.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE thoughts_new (
                id TEXT NOT NULL PRIMARY KEY,
                body TEXT NOT NULL,
                kind TEXT NOT NULL,
                kindSource TEXT NOT NULL,
                space TEXT NOT NULL,
                input TEXT NOT NULL,
                title TEXT,
                tags TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                completedAt INTEGER,
                deletedAt INTEGER,
                syncState TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO thoughts_new
                (id, body, kind, kindSource, space, input, title, tags,
                 createdAt, updatedAt, completedAt, deletedAt, syncState)
            SELECT id, body,
                   CASE kind WHEN 'NOTE' THEN 'LEARNING' ELSE kind END,
                   'AUTO', space, 'TEXT', NULL, '[]',
                   createdAt, updatedAt, completedAt, NULL, 'PENDING'
            FROM thoughts
            """.trimIndent()
        )
        db.execSQL("DROP TABLE thoughts")
        db.execSQL("ALTER TABLE thoughts_new RENAME TO thoughts")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_thoughts_createdAt ON thoughts (createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_thoughts_syncState ON thoughts (syncState)")
    }
}

/** v2 → v3 (Daytrace): check-ins, app usage intervals and wake-ups. Purely additive. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS entries (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                value TEXT NOT NULL,
                note TEXT,
                occurredAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncState TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_entries_occurredAt ON entries (occurredAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_entries_syncState ON entries (syncState)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS usage_intervals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                packageName TEXT NOT NULL,
                app TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER NOT NULL,
                uploaded INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_usage_intervals_app_startedAt " +
                "ON usage_intervals (app, startedAt)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_intervals_uploaded ON usage_intervals (uploaded)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_intervals_startedAt ON usage_intervals (startedAt)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS wakeups (
                day TEXT NOT NULL PRIMARY KEY,
                tappedAt INTEGER NOT NULL,
                wokeAt INTEGER,
                greeting TEXT,
                state TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v3 → v4: daily Health Connect aggregates. Purely additive. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS health_days (
                day TEXT NOT NULL PRIMARY KEY,
                steps INTEGER NOT NULL,
                walkMinutes INTEGER NOT NULL,
                exerciseMinutes INTEGER NOT NULL,
                sleepMinutes INTEGER NOT NULL,
                sleepStart INTEGER,
                sleepEnd INTEGER,
                meals TEXT NOT NULL,
                calories INTEGER,
                updatedAt INTEGER NOT NULL,
                uploaded INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
