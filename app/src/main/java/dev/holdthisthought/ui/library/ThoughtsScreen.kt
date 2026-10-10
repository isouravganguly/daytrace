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
import androidx.compose.foundation.layout.imePadding
import dev.holdthisthought.ui.capture.Composer
import dev.holdthisthought.ui.theme.Space
import dev.holdthisthought.ui.theme.popIn
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

/**
 * Thoughts: capture and find in one place. The composer sits on top, everything you've kept
 * scrolls underneath, newest first, with search and filters in between.
 */
@Composable
fun ThoughtsScreen(
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

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize().imePadding(),
    ) {
        item(key = "title") {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = 16.dp, bottom = 4.dp),
            ) {
                Text("Thoughts", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                Text(
                    "${thoughts.size} kept",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        item(key = "composer") {
            Composer(snackbar, Modifier.padding(horizontal = Space.gutter).popIn("thoughts", 0))
        }
        item(key = "search") {
            Column(Modifier.padding(top = 10.dp).popIn("thoughts", 1)) {
                SearchField(
                    query = filters.query,
                    onQueryChange = viewModel::setQuery,
                    modifier = Modifier.padding(horizontal = Space.gutter),
                )
                Spacer(Modifier.height(10.dp))
                FilterRow(
                    filters = filters,
                    onKind = viewModel::setKind,
                    onSpace = viewModel::setSpace,
                    onShowDone = viewModel::setShowDone,
                )
            }
        }

        val card: @Composable (Thought, Modifier) -> Unit = { thought, itemModifier ->
            ThoughtCard(
                thought = thought,
                onClick = { viewModel.open(thought) },
                onToggleDone = { viewModel.toggleDone(thought) },
                modifier = itemModifier.padding(horizontal = Space.gutter),
            )
        }
        if (thoughts.isEmpty() && related.isEmpty()) {
            item(key = "empty") { EmptyLibrary(filters) }
        } else if (searching) {
            if (thoughts.isNotEmpty()) {
                sectionHeader("Matches")
                items(thoughts, key = { it.id }) { card(it, Modifier.animateItem()) }
            }
            if (related.isNotEmpty()) {
                sectionHeader("Related by meaning", Icons.Outlined.AutoAwesome)
                items(related, key = { "related-${it.id}" }) { card(it, Modifier.animateItem()) }
            }
        } else {
            thoughts.groupBy { localDate(it.createdAt) }.forEach { (day, items) ->
                sectionHeader(dayLabel(day))
                items(items, key = { it.id }) { card(it, Modifier.animateItem()) }
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
            modifier = Modifier.padding(start = Space.gutter + 4.dp, top = 12.dp, bottom = 2.dp),
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
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
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
                    "Search your thoughts…",
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
        else -> "Nothing kept yet" to "Write or say your first thought above. It shows up here, newest first."
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp),
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
