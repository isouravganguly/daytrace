package dev.holdthisthought.ui.library

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryFilters(
    val query: String = "",
    val kind: ThoughtKind? = null,
    val space: ThoughtSpace? = null,
    val showDone: Boolean = false,
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as ThoughtApplication).container
    private val repository = container.repository
    private val remote = container.remote

    private val _filters = MutableStateFlow(LibraryFilters())
    val filters: StateFlow<LibraryFilters> = _filters.asStateFlow()

    /** Literal matches from the device. Instant and offline. */
    val thoughts: StateFlow<List<Thought>> = _filters
        .flatMapLatest { f -> repository.observe(f.query, f.kind, f.space, f.showDone || f.query.isNotBlank()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Thoughts that match by meaning rather than exact words, from the cloud's embeddings.
     * Empty when offline, signed out, or the query is short.
     */
    val related: StateFlow<List<Thought>> = combine(
        _filters
            .mapLatest { it.query.trim() }
            .distinctUntilChanged()
            .debounce(500)
            .mapLatest(::semanticIds),
        thoughts,
    ) { ids, literal ->
        val literalIds = literal.mapTo(HashSet()) { it.id }
        ids.filterNot { it in literalIds }
    }
        .mapLatest { ids ->
            val byId = repository.getVisible(ids).associateBy { it.id }
            ids.mapNotNull(byId::get)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun semanticIds(query: String): List<String> {
        if (query.length < 3 || remote.currentUser() == null) return emptyList()
        return runCatching { remote.semanticSearch(query) }
            .onFailure { Log.w("Library", "Semantic search unavailable", it) }
            .getOrDefault(emptyList())
    }

    private val _selectedId = MutableStateFlow<String?>(null)
    val selected: StateFlow<Thought?> = _selectedId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeOne(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(query: String) = _filters.update { it.copy(query = query) }
    fun setKind(kind: ThoughtKind?) = _filters.update { it.copy(kind = kind) }
    fun setSpace(space: ThoughtSpace?) = _filters.update { it.copy(space = space) }
    fun setShowDone(show: Boolean) = _filters.update { it.copy(showDone = show) }

    fun open(thought: Thought) {
        _selectedId.value = thought.id
    }

    fun close() {
        _selectedId.value = null
    }

    fun saveEdits(id: String, body: String, kind: ThoughtKind, space: ThoughtSpace) {
        viewModelScope.launch {
            val current = repository.get(id) ?: return@launch
            if (body.trim() != current.body) repository.updateBody(id, body)
            if (kind != current.kind) repository.setKind(id, kind)
            if (space != current.space) repository.setSpace(id, space)
        }
    }

    fun toggleDone(thought: Thought) {
        viewModelScope.launch { repository.setDone(thought.id, !thought.isDone) }
    }

    fun delete(thought: Thought) {
        viewModelScope.launch { repository.delete(thought.id) }
    }

    fun restore(thought: Thought) {
        viewModelScope.launch { repository.restore(thought.id) }
    }

    private inline fun MutableStateFlow<LibraryFilters>.update(transform: (LibraryFilters) -> LibraryFilters) {
        value = transform(value)
    }
}
