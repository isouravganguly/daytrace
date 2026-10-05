package dev.holdthisthought.data

import dev.holdthisthought.data.local.ThoughtKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalClassifierTest {
    private fun kind(text: String) = LocalClassifier.classify(text)

    @Test fun todos() {
        assertEquals(ThoughtKind.TODO, kind("Todo renew passport"))
        assertEquals(ThoughtKind.TODO, kind("remind me to call mum"))
        assertEquals(ThoughtKind.TODO, kind("Email Priya about the offsite"))
        assertEquals(ThoughtKind.TODO, kind("I need to fix the CI cache"))
    }

    @Test fun learnings() {
        assertEquals(ThoughtKind.LEARNING, kind("TIL Postgres partial indexes need the same WHERE"))
        assertEquals(ThoughtKind.LEARNING, kind("Today I learned that HNSW trades memory for speed"))
        assertEquals(ThoughtKind.LEARNING, kind("Turns out adb needs run-as for app data"))
        assertEquals(ThoughtKind.LEARNING, kind("The trick is to debounce before the network call"))
    }

    @Test fun thoughts() {
        assertEquals(ThoughtKind.THOUGHT, kind("humanity needs courage"))
        assertEquals(ThoughtKind.THOUGHT, kind("What if onboarding were just one screen?"))
    }
}
