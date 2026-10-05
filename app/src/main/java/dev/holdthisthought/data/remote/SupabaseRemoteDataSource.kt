package dev.holdthisthought.data.remote

import dev.holdthisthought.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Columns the device owns. AI columns are deliberately absent so a push never overwrites them. */
@Serializable
data class RemoteThoughtPush(
    val id: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    val kind: String,
    @SerialName("kind_source") val kindSource: String,
    val space: String,
    val input: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("completed_at") val completedAt: String?,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class RemoteThought(
    val id: String,
    val body: String,
    val kind: String,
    @SerialName("kind_source") val kindSource: String = "AUTO",
    val space: String,
    val input: String = "TEXT",
    val title: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("server_updated_at") val serverUpdatedAt: String,
)

@Serializable
private data class SearchHit(val id: String, val similarity: Double)

@Serializable
private data class SearchResponse(val results: List<SearchHit> = emptyList())

/** Thin wrapper over Supabase. Knows nothing about Room. */
class SupabaseRemoteDataSource private constructor(
    private val client: SupabaseClient?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = client != null

    val sessionStatus: StateFlow<SessionStatus> =
        client?.auth?.sessionStatus ?: MutableStateFlow(SessionStatus.NotAuthenticated(false))

    fun currentUser(): UserInfo? = client?.auth?.currentUserOrNull()

    suspend fun signIn(email: String, password: String) {
        requireClient().auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /** Returns true when the new account is immediately signed in (email confirmation off). */
    suspend fun signUp(email: String, password: String): Boolean {
        val auth = requireClient().auth
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return auth.currentUserOrNull()?.isAnonymous == false
    }

    /** Turns an anonymous session into a permanent account, keeping its user id and data. */
    suspend fun upgradeAnonymous(email: String, password: String) {
        requireClient().auth.updateUser {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        client?.auth?.signOut()
    }

    suspend fun push(rows: List<RemoteThoughtPush>) {
        if (rows.isEmpty()) return
        rows.chunked(200).forEach { requireClient().from(TABLE).upsert(it) }
    }

    /** Rows changed on the server at or after [since], oldest first. */
    suspend fun pullChanges(since: String?, limit: Long): List<RemoteThought> =
        requireClient().from(TABLE).select {
            filter { if (since != null) gte("server_updated_at", since) }
            order("server_updated_at", Order.ASCENDING)
            limit(limit)
        }.decodeList()

    /** Asks the server to enrich (classify, title, tag, embed) anything it has not processed yet. */
    suspend fun enrichPending() {
        invoke(buildJsonObject { put("action", "enrich_pending") })
    }

    /** Ids of thoughts semantically close to [query], best first. */
    suspend fun semanticSearch(query: String): List<String> {
        val text = invoke(
            buildJsonObject {
                put("action", "search")
                put("query", query)
            }
        )
        return json.decodeFromString<SearchResponse>(text).results.map { it.id }
    }

    private suspend fun invoke(body: JsonObject): String =
        requireClient().functions.invoke(
            function = FUNCTION,
            body = body,
            headers = headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            },
        ).bodyAsText()

    private fun requireClient(): SupabaseClient =
        requireNotNull(client) { "Cloud backup is not configured in this build." }

    companion object {
        private const val TABLE = "thoughts"
        private const val FUNCTION = "thought-machine"

        fun create(): SupabaseRemoteDataSource {
            val url = BuildConfig.SUPABASE_URL
            val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
            if (url.isBlank() || key.isBlank()) return SupabaseRemoteDataSource(null)
            return SupabaseRemoteDataSource(
                createSupabaseClient(url, key) {
                    install(Auth)
                    install(Functions)
                    install(Postgrest)
                }
            )
        }
    }
}
