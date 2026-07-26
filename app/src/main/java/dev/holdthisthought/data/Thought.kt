package dev.holdthisthought.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ThoughtKind { NOTE, TODO, THOUGHT }
enum class ThoughtSpace { PERSONAL, WORK }
enum class SyncState { PENDING, SYNCED }

@Entity(tableName = "thoughts")
data class Thought(
    @PrimaryKey val id: String,
    val body: String,
    val kind: ThoughtKind,
    val space: ThoughtSpace,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)
