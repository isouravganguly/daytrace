package dev.holdthisthought.notify

import dev.holdthisthought.game.GameState

/**
 * Blip's lines for each nudge. Several per nudge, rotated by day, so they stay fresh instead of
 * fading into background noise. Friendly and curious, never guilt.
 */
object NudgeCopy {
    data class Text(val title: String, val body: String)

    fun of(push: Push, game: GameState, epochDay: Long): Text {
        val lines = when (push) {
            Push.WAKE -> listOf(
                Text("☀️ Rise and shine!", "Blip's awake. Are you? Tap \"I'm up\" for +20 XP."),
                Text("Good morning! 🌤️", "One tap starts today's quests."),
                Text("Psst… it's a new day", "Log your wake-up and Blip does a little dance."),
            )
            Push.BREAKFAST -> listOf(
                Text("Breakfast? 🥐", "Had something? One tap, +5 XP."),
                Text("Fuel check ⛽", "Blip's tummy is rumbling. Did you eat yet?"),
            )
            Push.LUNCH -> listOf(
                Text("Lunch o'clock 🍜", "Tap \"Ate it\" and the Fuel ring fills up."),
                Text("Midday refuel? 🥗", "Quick tap, quick XP."),
                Text("Blip packed a sandwich 🥪", "Did you have lunch?"),
            )
            Push.DINNER -> listOf(
                Text("Dinner time 🍝", "Log it in one tap. Nearly done for the day!"),
                Text("What's for dinner? 🍲", "Tap once to log it, +5 XP."),
            )
            Push.MOOD -> listOf(
                Text("Quick vibe check 💭", "How's it going right now? Pick a face."),
                Text("Blip wants to know 🫶", "How are you feeling? One tap."),
                Text("Weather report: you ☁️☀️", "What's your inner weather like?"),
            )
            Push.WALK -> {
                val left = (game.stepGoal - game.today.steps).coerceAtLeast(0)
                if (game.today.steps > 0 && left > 0) listOf(
                    Text("%,d steps to go 👟".format(left), "About ${(left / 110).coerceAtLeast(5)} minutes of walking finishes your goal."),
                    Text("You're %d%% there 🚶".format(game.today.steps * 100 / game.stepGoal), "A quick walk now and Move is done. Blip's coming too."),
                ) else listOf(
                    Text("Walkies? 🥾", "A short walk counts. Blip's bringing snacks."),
                    Text("Leg stretch time 🚶", "Tap if you walked today, +15 XP."),
                )
            }
            Push.CLOSE_DAY -> listOf(
                Text("Close your day ⚖️", "How was today? Two taps and you're done."),
                Text("Day's almost over 🌙", "Give today a verdict. +20 XP."),
            )
            Push.STREAK -> listOf(
                Text("🔥 ${game.streak}-day streak!", "${3 - game.today.doneCount} quick taps keep it alive."),
                Text("Keep the flame going 🔥", "You're ${3 - game.today.doneCount} quests from a safe streak."),
            )
            Push.GOOD_NIGHT -> listOf(
                Text("Time to wind down 🌙", "Tap Good night and put the phone away. Blip's already yawning."),
                Text("Bedtime, sleepyhead 😴", "One tap: Good night. Tomorrow-you says thanks."),
                Text("Lights out soon? 🌙", "Say good night to Blip, +10 XP, then a book instead of the feed?"),
            )
            Push.ALMOST -> {
                val c = game.weekly.firstOrNull { !it.done && it.challenge.target - it.progress == 1 }
                listOf(Text("So close! ${c?.challenge?.emoji ?: "🏁"}", "One more for \"${c?.challenge?.title}\" and +100 XP."))
            }
        }
        return lines[Math.floorMod(epochDay + push.ordinal, lines.size.toLong()).toInt()]
    }

    private val rewards = listOf(
        "Blip is dancing 🕺", "Nice one! ✨", "Look at you go 🚀", "Blip's so proud 💙", "Ding! Quest progress 🔔",
    )

    fun reward(xp: Int): String = "+$xp XP · ${rewards.random()}"
}
