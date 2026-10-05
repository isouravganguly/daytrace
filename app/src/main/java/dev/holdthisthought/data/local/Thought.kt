package dev.holdthisthought.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What a capture is. LEARNING is the core of the app: things worth not relearning. */
enum class ThoughtKind { THOUGHT, LEARNING, TODO }

/** Whether [Thought.kind] was guessed (AI may refine it) or chosen by the user (AI must not touch it). */
enum class KindSource { AUTO, USER }

enum class ThoughtSpace { WORK, PERSONAL }

enum class CaptureInput { TEXT, VOICE }

/** PENDING means the row has local changes the cloud has not seen yet. */
enum class SyncState { PENDING, SYNCED }

@Entity(
    tableName = "thoughts",
    indices = [Index("createdAt"), Index("syncState")],
)
data class Thought(
    @PrimaryKey val id: String,
    /** The user's own words. Never rewritten by AI. */
    val body: String,
    val kind: ThoughtKind,
    val kindSource: KindSource = KindSource.AUTO,
    val space: ThoughtSpace,
    val input: CaptureInput = CaptureInput.TEXT,
    /** Short AI-generated headline; null until enriched. */
    val title: String? = null,
    /** AI-generated topic tags, lowercase. */
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    /** Soft delete so the deletion can sync to the cloud. */
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
) {
    val isDone: Boolean get() = completedAt != null
}
