package dev.holdthisthought.export

import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ThoughtExporterTest {
    private fun thought(id: String, body: String, kind: ThoughtKind, at: Long, done: Long? = null) =
        Thought(
            id = id, body = body, kind = kind, space = ThoughtSpace.WORK,
            title = if (kind == ThoughtKind.LEARNING) "Flows cancel" else null,
            tags = if (kind == ThoughtKind.LEARNING) listOf("kotlin") else emptyList(),
            createdAt = at, updatedAt = at, completedAt = done,
        )

    @Test fun markdownGroupsByDayOldestFirstWithMetadata() {
        val day1 = 1_759_536_000_000L // 2025-10-04T00:00Z
        val md = ThoughtExporter.toMarkdown(
            listOf(
                thought("b", "ship it", ThoughtKind.TODO, day1 + 86_400_000, done = day1 + 90_000_000),
                thought("a", "TIL flows cancel", ThoughtKind.LEARNING, day1 + 3_600_000),
            ),
            ZoneOffset.UTC,
        )
        assertTrue(md.indexOf("TIL flows cancel") < md.indexOf("ship it"))
        assertTrue(md.contains("### 01:00 · Learning · work — Flows cancel"))
        assertTrue(md.contains("Tags: #kotlin"))
        assertTrue(md.contains("· Todo · work · done"))
        assertTrue(md.contains("## 2025-10-04"))
        assertTrue(md.contains("2 entries"))
    }

    @Test fun jsonContainsOriginalText() {
        val json = ThoughtExporter.toJson(listOf(thought("a", "keep \"quotes\"", ThoughtKind.THOUGHT, 0)))
        assertTrue(json.contains("keep \\\"quotes\\\""))
        assertTrue(json.contains("\"kind\": \"THOUGHT\""))
    }
}
