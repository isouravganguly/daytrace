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

@Database(entities = [Thought::class], version = 2, exportSchema = true)
@TypeConverters(ThoughtConverters::class)
abstract class ThoughtDatabase : RoomDatabase() {
    abstract fun thoughtDao(): ThoughtDao

    companion object {
        fun create(context: Context): ThoughtDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ThoughtDatabase::class.java,
                "hold-this-thought.db",
            )
                .addMigrations(MIGRATION_1_2)
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
