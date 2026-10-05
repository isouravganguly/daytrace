package dev.holdthisthought.export

import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns thoughts into files that are pleasant for humans and easy for an LLM
 * (for example Claude Code) to read, summarise and search.
 */
object ThoughtExporter {
    private val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)", Locale.getDefault())
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun toMarkdown(thoughts: List<Thought>, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            val sorted = thoughts.sortedBy { it.createdAt }
            appendLine("# Hold This Thought — export")
            appendLine()
            appendLine("Exported ${Instant.now().atZone(zone).toLocalDate()} · ${sorted.size} entries.")
            appendLine("Kinds: THOUGHT (ideas, reflections), LEARNING (things learned worth remembering), TODO (actions).")
            appendLine()
            sorted.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
                .forEach { (day, items) ->
                    appendLine("## ${day.format(dayFormat)}")
                    appendLine()
                    items.forEach { thought ->
                        val time = Instant.ofEpochMilli(thought.createdAt).atZone(zone).format(timeFormat)
                        val status = if (thought.kind == ThoughtKind.TODO) {
                            if (thought.isDone) " · done" else " · open"
                        } else {
                            ""
                        }
                        append("### $time · ${thought.kind.label} · ${thought.space.name.lowercase()}$status")
                        thought.title?.let { append(" — $it") }
                        appendLine()
                        if (thought.tags.isNotEmpty()) {
                            appendLine("Tags: ${thought.tags.joinToString(" ") { "#$it" }}")
                            appendLine()
                        }
                        appendLine(thought.body)
                        appendLine()
                    }
                }
        }

    fun toJson(thoughts: List<Thought>): String =
        prettyJson.encodeToString(thoughts.sortedBy { it.createdAt }.map { it.toExport() })

    private val prettyJson = Json { prettyPrint = true }

    @Serializable
    private data class ExportedThought(
        val id: String,
        val body: String,
        val kind: String,
        val space: String,
        val title: String?,
        val tags: List<String>,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("completed_at") val completedAt: String?,
        val input: String,
    )

    private fun Thought.toExport() = ExportedThought(
        id = id,
        body = body,
        kind = kind.name,
        space = space.name,
        title = title,
        tags = tags,
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
        updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
        completedAt = completedAt?.let { Instant.ofEpochMilli(it).toString() },
        input = input.name,
    )

    private val ThoughtKind.label: String
        get() = when (this) {
            ThoughtKind.THOUGHT -> "Thought"
            ThoughtKind.LEARNING -> "Learning"
            ThoughtKind.TODO -> "Todo"
        }
}
