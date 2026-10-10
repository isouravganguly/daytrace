package dev.holdthisthought.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.holdthisthought.data.local.HealthDay
import dev.holdthisthought.data.local.MealSlot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** Read-only access to Health Connect: availability, permissions and one day's aggregates. */
class HealthConnectSource(private val context: Context) {

    enum class Availability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_UPDATE
        else -> Availability.UNAVAILABLE
    }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    val exercisePermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val nutritionPermission = HealthPermission.getReadPermission(NutritionRecord::class)

    /** Everything Daytrace asks for in one sheet. */
    val requested: Set<String> = setOf(stepsPermission, exercisePermission, sleepPermission, nutritionPermission)

    /** Lets the 15-minute worker read while the app is closed (Android 14+). */
    val backgroundPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    suspend fun granted(): Set<String> =
        if (availability() == Availability.AVAILABLE) client.permissionController.getGrantedPermissions() else emptySet()

    fun supportsBackgroundReads(): Boolean = runCatching {
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    /** Reads one local day. Types without permission are left at zero. */
    suspend fun readDay(day: LocalDate, granted: Set<String>, zone: ZoneId = ZoneId.systemDefault()): HealthDay {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(start, end)

        val steps = if (stepsPermission in granted) {
            // Android's aggregate de-duplicates across apps, but only counts apps on Health
            // Connect's priority list, so it can miss e.g. Google Fit. Taking the larger of the
            // aggregate and the single biggest source avoids both undercounting and double counting.
            val aggregate = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] ?: 0L
            val bySource = readAll(StepsRecord::class, range)
                .groupBy { it.metadata.dataOrigin.packageName }
                .mapValues { (_, records) -> records.sumOf { it.count } }
            maxOf(aggregate, bySource.values.maxOrNull() ?: 0L)
        } else 0L

        var walk = Duration.ZERO
        var exercise = Duration.ZERO
        if (exercisePermission in granted) {
            readAll(ExerciseSessionRecord::class, range).forEach { session ->
                val clipped = Duration.between(maxOf(session.startTime, start), minOf(session.endTime, end))
                if (clipped.isNegative) return@forEach
                exercise += clipped
                if (session.exerciseType in WALK_TYPES) walk += clipped
            }
        }

        // Sleep that *ended* on this day: look from 18:00 the evening before.
        var sleep = Duration.ZERO
        var sleepStart: Instant? = null
        var sleepEnd: Instant? = null
        if (sleepPermission in granted) {
            val evening = day.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            readAll(SleepSessionRecord::class, TimeRangeFilter.between(evening, end))
                .filter { !it.endTime.isBefore(start) && it.endTime.isBefore(end) }
                .forEach { s ->
                    sleep += Duration.between(s.startTime, s.endTime)
                    sleepStart = minOf(sleepStart ?: s.startTime, s.startTime)
                    sleepEnd = maxOf(sleepEnd ?: s.endTime, s.endTime)
                }
        }

        val meals = mutableSetOf<String>()
        var calories: Double? = null
        if (nutritionPermission in granted) {
            readAll(NutritionRecord::class, range).forEach { n ->
                slotOf(n.mealType)?.let { meals += it.name }
                n.energy?.inKilocalories?.let { calories = (calories ?: 0.0) + it }
            }
        }

        return HealthDay(
            day = day.toString(),
            steps = steps.toInt(),
            walkMinutes = walk.toMinutes().toInt(),
            exerciseMinutes = exercise.toMinutes().toInt(),
            sleepMinutes = sleep.toMinutes().toInt(),
            sleepStart = sleepStart?.toEpochMilli(),
            sleepEnd = sleepEnd?.toEpochMilli(),
            meals = MealSlot.entries.map { it.name }.filter { it in meals },
            calories = calories?.toInt(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var page: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, pageToken = page))
            out += response.records
            page = response.pageToken
        } while (!page.isNullOrEmpty())
        return out
    }

    private fun slotOf(mealType: Int): MealSlot? = when (mealType) {
        MealType.MEAL_TYPE_BREAKFAST -> MealSlot.BREAKFAST
        MealType.MEAL_TYPE_LUNCH -> MealSlot.LUNCH
        MealType.MEAL_TYPE_DINNER -> MealSlot.DINNER
        MealType.MEAL_TYPE_SNACK -> MealSlot.SNACK
        else -> null
    }

    private companion object {
        val WALK_TYPES = setOf(
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        )
    }
}
