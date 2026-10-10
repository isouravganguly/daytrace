package dev.holdthisthought.ui.today

import dev.holdthisthought.game.GameState
import dev.holdthisthought.game.Nudge
import dev.holdthisthought.ui.components.BlipMood

/** What Blip feels and says, from the state of the day. Friendly, never judgy. */
object BlipVoice {
    fun mood(state: GameState, nudge: Nudge?, wokeUp: Boolean, hour: Int, excited: Boolean): BlipMood = when {
        excited -> BlipMood.EXCITED
        !wokeUp && nudge == Nudge.WakeUp -> BlipMood.SLEEPY
        nudge == Nudge.GoodNight -> BlipMood.SLEEPY
        state.today.perfect || state.streak >= 7 -> BlipMood.PROUD
        nudge != null -> BlipMood.CURIOUS
        else -> BlipMood.CHEERFUL
    }

    fun line(state: GameState, nudge: Nudge?, wokeUp: Boolean, hour: Int): String = when {
        state.today.perfect -> "PERFECT DAY! I'm so proud of us 💎"
        nudge == Nudge.WakeUp -> "Zzz… tap Good morning when you're up ☀️"
        nudge is Nudge.Meal -> "${nudge.slot.label} time? I'm hungry just thinking about it ${nudge.slot.emoji}"
        nudge == Nudge.Mood -> "How are we feeling right now? 💭"
        nudge == Nudge.Capture -> "Got a thought for me to hold? 💡"
        nudge == Nudge.Walk -> {
            val left = (state.stepGoal - state.today.steps).coerceAtLeast(0)
            if (state.today.steps > 0) "%,d steps to go. Walk with me? 👟".format(left)
            else "Fancy a little walk? I'll tag along 🥾"
        }
        nudge == Nudge.Verdict -> "Day's winding down. How was it? ⚖️"
        nudge == Nudge.GoodNight -> "Bedtime soon. A book instead of the feed? 📖🌙"
        state.streak >= 7 -> "${state.streak} days in a row. We're unstoppable 🔥"
        state.today.counts -> "Streak safe! Anything else is bonus XP ✨"
        else -> "All caught up. Enjoy the moment ✨"
    }
}
