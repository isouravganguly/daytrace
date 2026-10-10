package dev.holdthisthought.daytrace

import dev.holdthisthought.data.local.TraceDao
import java.time.Instant

/** Drains the local interval queue to /ingest in batches. The server de-duplicates. */
class DaytraceUploader(
    private val dao: TraceDao,
    private val api: DaytraceApi,
    private val settings: DaytraceSettings,
) {
    sealed interface Outcome {
        data class Uploaded(val count: Int) : Outcome
        data object NotConfigured : Outcome
        data object TokenRejected : Outcome
        data class Failed(val error: Exception) : Outcome
    }

    /** Daily Health Connect totals; the server upserts by day, so re-sending is safe. */
    private suspend fun uploadHealth(): Int {
        val days = dao.healthToUpload()
        if (days.isEmpty()) return 0
        val startedAt = System.currentTimeMillis()
        api.health(
            days.map {
                DaytraceApi.HealthPayload(
                    day = it.day,
                    steps = it.steps,
                    walkMinutes = it.walkMinutes,
                    exerciseMinutes = it.exerciseMinutes,
                    sleepMinutes = it.sleepMinutes,
                    sleepStart = it.sleepStart?.let { t -> Instant.ofEpochMilli(t).toString() },
                    sleepEnd = it.sleepEnd?.let { t -> Instant.ofEpochMilli(t).toString() },
                    meals = it.meals,
                    calories = it.calories,
                )
            },
        )
        // Rows re-read after this upload started stay pending for the next run.
        dao.markHealthUploaded(days.map { it.day }, before = startedAt)
        return days.size
    }

    suspend fun upload(): Outcome {
        if (!settings.config.value.isComplete) return Outcome.NotConfigured
        var total = 0
        try {
            while (true) {
                val batch = dao.intervalsToUpload(DaytraceApi.MAX_BATCH)
                if (batch.isEmpty()) break
                api.ingest(
                    batch.map {
                        DaytraceApi.IntervalPayload(
                            app = it.app,
                            startedAt = Instant.ofEpochMilli(it.startedAt).toString(),
                            endedAt = Instant.ofEpochMilli(it.endedAt).toString(),
                        )
                    },
                )
                dao.markUploaded(batch.map { it.id })
                total += batch.size
                if (batch.size < DaytraceApi.MAX_BATCH) break
            }
            total += uploadHealth()
            settings.recordUpload(System.currentTimeMillis())
            return Outcome.Uploaded(total)
        } catch (e: DaytraceApi.UnauthorizedException) {
            settings.recordError("Token rejected. Check the device token.", tokenRejected = true)
            return Outcome.TokenRejected
        } catch (e: Exception) {
            settings.recordError(e.message ?: "Upload failed")
            return Outcome.Failed(e)
        }
    }
}
