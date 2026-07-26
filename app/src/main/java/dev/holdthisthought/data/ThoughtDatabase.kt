package dev.holdthisthought.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class ThoughtConverters {
    @TypeConverter fun kindToString(value: ThoughtKind) = value.name
    @TypeConverter fun stringToKind(value: String) = ThoughtKind.valueOf(value)
    @TypeConverter fun spaceToString(value: ThoughtSpace) = value.name
    @TypeConverter fun stringToSpace(value: String) = ThoughtSpace.valueOf(value)
    @TypeConverter fun syncToString(value: SyncState) = value.name
    @TypeConverter fun stringToSync(value: String) = SyncState.valueOf(value)
}

@Database(entities = [Thought::class], version = 1, exportSchema = false)
@TypeConverters(ThoughtConverters::class)
abstract class ThoughtDatabase : RoomDatabase() {
    abstract fun thoughtDao(): ThoughtDao

    companion object {
        fun create(context: Context): ThoughtDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ThoughtDatabase::class.java,
                "hold-this-thought.db",
            ).build()
    }
}
