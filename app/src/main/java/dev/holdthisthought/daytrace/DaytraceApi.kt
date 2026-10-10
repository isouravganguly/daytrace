package dev.holdthisthought.daytrace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** The `daytrace` Edge Function. Every call carries the per-device token. */
class DaytraceApi(private val settings: DaytraceSettings) {

    @Serializable
    data class WakeupResult(
        val greeting: String,
        @SerialName("woke_at") val wokeAt: String,
        @SerialName("already_logged") val alreadyLogged: Boolean,
    ) {
        val wokeAtMillis: Long get() = Instant.parse(wokeAt).toEpochMilli()
    }

    @Serializable
    data class IntervalPayload(
        val app: String,
        @SerialName("started_at") val startedAt: String,
        @SerialName("ended_at") val endedAt: String,
    )

    @Serializable
    private data class IngestBody(val intervals: List<IntervalPayload>)

    @Serializable
    data class HealthPayload(
        val day: String,
        val steps: Int,
        @SerialName("walk_minutes") val walkMinutes: Int,
        @SerialName("exercise_minutes") val exerciseMinutes: Int,
        @SerialName("sleep_minutes") val sleepMinutes: Int,
        @SerialName("sleep_start") val sleepStart: String?,
        @SerialName("sleep_end") val sleepEnd: String?,
        val meals: List<String>,
        val calories: Int?,
    )

    @Serializable
    private data class HealthBody(val days: List<HealthPayload>)

    @Serializable
    private data class IngestResult(val ok: Boolean = false, val stored: Int = 0)

    /** The server refused the device token. Retrying won't help until it changes. */
    class UnauthorizedException : IOException("Device token rejected")

    class HttpException(val code: Int, message: String) : IOException("HTTP $code: $message")

    /** [tappedAt]: when "Good morning" was tapped, so an offline tap keeps its real time. */
    suspend fun wakeup(tappedAt: Long?): WakeupResult {
        val body = tappedAt?.let { """{"at":"${Instant.ofEpochMilli(it)}"}""" }
        return json.decodeFromString(post("wakeup", body))
    }

    suspend fun ingest(intervals: List<IntervalPayload>): Int {
        require(intervals.size <= MAX_BATCH)
        val body = json.encodeToString(IngestBody.serializer(), IngestBody(intervals))
        return json.decodeFromString<IngestResult>(post("ingest", body)).stored
    }

    suspend fun health(days: List<HealthPayload>): Int {
        val body = json.encodeToString(HealthBody.serializer(), HealthBody(days))
        return json.decodeFromString<IngestResult>(post("health", body)).stored
    }

    @Serializable
    private data class RegisterBody(
        @SerialName("install_id") val installId: String,
        val name: String,
        val platform: String,
        val kind: String,
    )

    @Serializable
    private data class RegisterResult(val token: String)

    /** Exchanges the owner's Supabase session for this device's own token. */
    suspend fun register(accessToken: String, installId: String, name: String, kind: String): String =
        withContext(Dispatchers.IO) {
            val endpoint = settings.config.value.endpoint
            val connection = URL("$endpoint/register").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                val body = json.encodeToString(RegisterBody.serializer(), RegisterBody(installId, name, "android", kind))
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw HttpException(code, text.take(200))
                json.decodeFromString(RegisterResult.serializer(), text).token
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun post(route: String, body: String?): String = withContext(Dispatchers.IO) {
        val config = settings.config.value
        if (!config.isComplete) throw IOException("Daytrace device token not set")
        val connection = URL("${config.endpoint}/$route").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("x-device-token", config.token)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write((body ?: "").toByteArray()) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code == 401 -> throw UnauthorizedException()
                code !in 200..299 -> throw HttpException(code, text.take(200))
                else -> text
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MAX_BATCH = 1000
        private val json = Json { ignoreUnknownKeys = true }
    }
}
