package dev.holdthisthought.data

import dev.holdthisthought.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

@Serializable
private data class RemoteThought(
    val id: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    val kind: String,
    val space: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("completed_at") val completedAt: String?,
)

@Serializable
private data class RemoteKind(
    val id: String,
    val kind: String,
)

data class RemoteSyncResult(
    val completed: Boolean,
    val kinds: Map<String, ThoughtKind> = emptyMap(),
)

class SupabaseRemoteDataSource private constructor(
    private val client: SupabaseClient?,
) {
    val isConfigured: Boolean get() = client != null
    val sessionStatus: StateFlow<SessionStatus> =
        client?.auth?.sessionStatus ?: MutableStateFlow(SessionStatus.NotAuthenticated())

    suspend fun signIn(email: String, password: String) {
        val supabase = requireConfiguredClient()
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String): Boolean {
        val supabase = requireConfiguredClient()
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return supabase.auth.currentUserOrNull()?.isAnonymous == false
    }

    suspend fun continuePrivately() {
        val supabase = requireConfiguredClient()
        if (supabase.auth.currentSessionOrNull() == null) {
            supabase.auth.signInAnonymously()
        }
    }

    suspend fun signOut() {
        client?.auth?.signOut()
    }

    suspend fun sync(thoughts: List<Thought>): RemoteSyncResult {
        val supabase = client ?: return RemoteSyncResult(completed = false)
        if (supabase.auth.currentSessionOrNull() == null) {
            return RemoteSyncResult(completed = false)
        }
        val userId = requireNotNull(supabase.auth.currentUserOrNull()?.id) {
            "Supabase authentication did not produce a user."
        }

        if (thoughts.isNotEmpty()) {
            val remote = thoughts.map {
                RemoteThought(
                    id = it.id,
                    userId = userId,
                    body = it.body,
                    kind = it.kind.name,
                    space = it.space.name,
                    createdAt = Instant.ofEpochMilli(it.createdAt).toString(),
                    updatedAt = Instant.ofEpochMilli(it.updatedAt).toString(),
                    completedAt = it.completedAt?.let(Instant::ofEpochMilli)?.toString(),
                )
            }
            supabase.from("thoughts").upsert(remote)

            thoughts.forEach { thought ->
                supabase.functions.invoke(
                    function = "thought-machine",
                    body = buildJsonObject {
                        put("action", "enrich")
                        put("thoughtId", thought.id)
                    },
                    headers = headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    },
                )
            }
        }

        val kinds = supabase.from("thoughts")
            .select(columns = Columns.list("id", "kind"))
            .decodeList<RemoteKind>()
            .mapNotNull { remoteKind ->
                runCatching {
                    remoteKind.id to ThoughtKind.valueOf(remoteKind.kind)
                }.getOrNull()
            }
            .toMap()
        return RemoteSyncResult(completed = true, kinds = kinds)
    }

    private fun requireConfiguredClient(): SupabaseClient =
        requireNotNull(client) { "Supabase is not configured." }

    companion object {
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
