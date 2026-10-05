package dev.holdthisthought.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import dev.holdthisthought.ui.components.KindPill
import dev.holdthisthought.ui.components.Pill
import dev.holdthisthought.ui.components.ThoughtCard
import dev.holdthisthought.ui.components.dayLabel
import dev.holdthisthought.ui.components.label
import dev.holdthisthought.ui.components.localDate
import dev.holdthisthought.ui.components.pluralLabel
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val thoughts by viewModel.thoughts.collectAsStateWithLifecycle()
    val related by viewModel.related.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val searching = filters.query.isNotBlank()

    fun deleteWithUndo(thought: Thought) {
        viewModel.close()
        viewModel.delete(thought)
        scope.launch {
            val result = snackbar.showSnackbar("Deleted", "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(thought)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp),
            ) {
                Text(
                    "Library",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${thoughts.size} ${if (thoughts.size == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            SearchField(query = filters.query, onQueryChange = viewModel::setQuery)
        }
        Spacer(Modifier.height(12.dp))
        FilterRow(
            filters = filters,
            onKind = viewModel::setKind,
            onSpace = viewModel::setSpace,
            onShowDone = viewModel::setShowDone,
        )
        Spacer(Modifier.height(4.dp))

        if (thoughts.isEmpty() && related.isEmpty()) {
            EmptyLibrary(filters)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val card: @Composable (Thought) -> Unit = { thought ->
                    ThoughtCard(
                        thought = thought,
                        onClick = { viewModel.open(thought) },
                        onToggleDone = { viewModel.toggleDone(thought) },
                    )
                }
                if (searching) {
                    if (thoughts.isNotEmpty()) {
                        sectionHeader("Matches")
                        items(thoughts, key = { it.id }) { card(it) }
                    }
                    if (related.isNotEmpty()) {
                        sectionHeader("Related by meaning", Icons.Outlined.AutoAwesome)
                        items(related, key = { "related-${it.id}" }) { card(it) }
                    }
                } else {
                    thoughts.groupBy { localDate(it.createdAt) }.forEach { (day, items) ->
                        sectionHeader(dayLabel(day))
                        items(items, key = { it.id }) { card(it) }
                    }
                }
            }
        }
    }

    selected?.let { thought ->
        ThoughtSheet(
            thought = thought,
            onDismiss = viewModel::close,
            onSave = viewModel::saveEdits,
            onToggleDone = { viewModel.toggleDone(thought) },
            onDelete = { deleteWithUndo(thought) },
        )
    }
}

private fun LazyListScope.sectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    item(key = "header-$title") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Icon(
            Icons.Rounded.Search, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search what you remember…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Rounded.Close, "Clear search", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FilterRow(
    filters: LibraryFilters,
    onKind: (ThoughtKind?) -> Unit,
    onSpace: (ThoughtSpace?) -> Unit,
    onShowDone: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Pill("All", selected = filters.kind == null, onClick = { onKind(null) })
        ThoughtKind.entries.forEach { kind ->
            KindPill(
                kind = kind,
                text = kind.pluralLabel,
                selected = filters.kind == kind,
                onClick = { onKind(if (filters.kind == kind) null else kind) },
            )
        }
        VerticalDivider(Modifier.height(20.dp))
        ThoughtSpace.entries.forEach { space ->
            Pill(
                space.label,
                selected = filters.space == space,
                onClick = { onSpace(if (filters.space == space) null else space) },
            )
        }
        if (filters.kind == null || filters.kind == ThoughtKind.TODO) {
            Pill(
                "Done",
                icon = Icons.Outlined.CheckCircle,
                selected = filters.showDone,
                onClick = { onShowDone(!filters.showDone) },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(filters: LibraryFilters) {
    val (title, body) = when {
        filters.query.isNotBlank() -> "Nothing found" to
            "Try different words. Once cloud backup is on, search also finds thoughts by meaning."
        filters.kind == ThoughtKind.LEARNING -> "No learnings yet" to
            "When you figure something out, capture it. Future you will thank you."
        filters.kind == ThoughtKind.TODO -> "All clear" to "No open todos."
        else -> "Your library is empty" to "Everything you keep shows up here, newest first."
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(40.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
