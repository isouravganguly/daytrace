package dev.holdthisthought.daytrace

/** A foreground-app event, reduced to what the builder needs. */
data class UsageSignal(val kind: Kind, val time: Long, val packageName: String? = null, val className: String? = null) {
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF }
}

/** The app in the foreground when a collection run ended. Persisted between runs. */
data class OpenInterval(
    val packageName: String,
    val className: String?,
    val start: Long,
    /** When its activity paused; it might still resume (an activity switch inside the app). */
    val pausedAt: Long?,
)

data class Span(val packageName: String, val start: Long, val end: Long)

/**
 * Turns a stream of usage events into foreground-app intervals. Pure, so it is unit-tested.
 *
 * - RESUMED(p) opens p, closing whatever was open.
 * - PAUSED(p) marks p as pausing; if p resumes within [continueWindowMs] the interval continues,
 *   so moving between activities of one app doesn't split it.
 * - Screen off or lock closes the open interval.
 * - Ignored packages (launcher, system UI) close the open interval without opening one.
 * - Intervals shorter than [minDurationMs] are dropped.
 */
class IntervalBuilder(
    private val ignored: Set<String> = emptySet(),
    private val minDurationMs: Long = 2_000,
    private val continueWindowMs: Long = 3_000,
) {
    data class Result(val spans: List<Span>, val open: OpenInterval?)

    fun build(start: OpenInterval?, signals: List<UsageSignal>, now: Long): Result {
        val spans = mutableListOf<Span>()
        var open = start

        fun close(at: Long) {
            open?.let { o ->
                val end = o.pausedAt ?: at
                if (end - o.start >= minDurationMs) spans += Span(o.packageName, o.start, end)
            }
            open = null
        }

        for (signal in signals.sortedBy { it.time }) {
            val t = signal.time
            when (signal.kind) {
                UsageSignal.Kind.RESUMED -> {
                    val pkg = signal.packageName ?: continue
                    val current = open
                    if (pkg in ignored) {
                        close(t)
                    } else if (current != null && current.packageName == pkg &&
                        (current.pausedAt == null || t - current.pausedAt <= continueWindowMs)
                    ) {
                        open = current.copy(className = signal.className, pausedAt = null)
                    } else {
                        close(t)
                        open = OpenInterval(pkg, signal.className, t, null)
                    }
                }
                UsageSignal.Kind.PAUSED -> {
                    val current = open
                    if (current != null && current.packageName == signal.packageName &&
                        current.pausedAt == null &&
                        (current.className == null || signal.className == null || current.className == signal.className)
                    ) {
                        open = current.copy(pausedAt = t)
                    }
                }
                UsageSignal.Kind.SCREEN_OFF -> close(t)
            }
        }

        val current = open
        if (current?.pausedAt != null && now - current.pausedAt > continueWindowMs) close(now)
        return Result(spans, open)
    }
}
