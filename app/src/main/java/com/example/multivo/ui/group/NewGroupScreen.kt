package com.example.multivo.ui.group

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.multivo.data.AppContainer
import com.example.multivo.data.PublicUser
import com.example.multivo.ui.components.Avatar
import com.example.multivo.ui.components.EmptyState
import com.example.multivo.ui.components.GradientButton
import com.example.multivo.ui.components.LanguageChip
import com.example.multivo.ui.components.Reveal
import com.example.multivo.ui.components.SectionLabel
import com.example.multivo.ui.components.multivoFieldColors
import com.example.multivo.ui.components.rememberRevealTracker
import com.example.multivo.ui.components.screenBackdrop
import com.example.multivo.ui.containerViewModel
import com.example.multivo.ui.preview.SampleData
import com.example.multivo.ui.theme.Motion
import com.example.multivo.ui.theme.MultivoBrush
import com.example.multivo.ui.theme.MultivoTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewGroupUiState(
    val name: String = "",
    val contacts: List<PublicUser> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val creating: Boolean = false,
    val error: String? = null
) {
    val canCreate get() = name.isNotBlank() && selected.isNotEmpty() && !creating
}

class NewGroupViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(NewGroupUiState())
    val state: StateFlow<NewGroupUiState> = _state
    val created = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            runCatching { container.api.contacts() }
                .onSuccess { list -> _state.update { it.copy(contacts = list, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v.take(100), error = null) }
    fun toggle(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun create() {
        val s = _state.value
        if (!s.canCreate) return
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            runCatching { container.api.createGroup(s.name.trim(), s.selected.toList()) }
                .onSuccess { created.emit(it.id) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(creating = false) }
        }
    }
}

@Composable
fun NewGroupRoute(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val vm = containerViewModel { NewGroupViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.created.collect(onCreated) }
    NewGroupScreen(state, onBack, vm::onName, vm::toggle, vm::create)
}

@Composable
fun NewGroupScreen(
    state: NewGroupUiState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onToggle: (String) -> Unit,
    onCreate: () -> Unit
) {
    val tracker = rememberRevealTracker()
    // Keep the contact order for the selected strip so it doesn't jump around.
    val selectedUsers = state.contacts.filter { it.id in state.selected }

    Scaffold(
        modifier = Modifier.screenBackdrop(),
        containerColor = Color.Transparent,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f)) {
                    Text("New group", style = MaterialTheme.typography.titleLarge)
                    AnimatedContent(
                        targetState = state.selected.size,
                        transitionSpec = {
                            (fadeIn(Motion.slow()) + slideInVertically(Motion.slow()) { it / 2 }) togetherWith
                                (fadeOut(Motion.slow()) + slideOutVertically(Motion.slow()) { -it / 2 })
                        },
                        label = "selectedCount"
                    ) { count ->
                        Text(
                            when (count) {
                                0 -> "Pick at least one person"
                                1 -> "1 person selected"
                                else -> "$count people selected"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                GradientButton(
                    onClick = onCreate,
                    enabled = state.canCreate,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.creating) "Creating…" else "Create group (${state.selected.size})")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "name") {
                Reveal(key = "name", tracker = tracker) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onName,
                            label = { Text("Group name") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            colors = multivoFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AnimatedVisibility(
                            visible = state.error != null,
                            enter = fadeIn(Motion.slow()) + expandVertically(Motion.slow()),
                            exit = fadeOut(Motion.slow()) + shrinkVertically(Motion.slow())
                        ) {
                            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }

            item(key = "selected-strip") {
                AnimatedVisibility(
                    visible = selectedUsers.isNotEmpty(),
                    enter = fadeIn(Motion.slow()) + expandVertically(Motion.slow()),
                    exit = fadeOut(Motion.slow()) + shrinkVertically(Motion.slow())
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(selectedUsers, key = { it.id }) { user ->
                            Column(
                                Modifier.widthIn(max = 64.dp).animateItem(
                                    fadeInSpec = Motion.slow(), placementSpec = Motion.slow(), fadeOutSpec = Motion.slow()
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Avatar(user.fullName, size = 52.dp, ring = true)
                                Spacer(Modifier.height(4.dp))
                                Text(user.fullName.substringBefore(' '), style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            item(key = "members-title") {
                SectionLabel("Pick members", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp))
            }
            if (state.loading) {
                item(key = "loading") {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 8.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }
            if (!state.loading && state.contacts.isEmpty()) {
                item(key = "empty") { EmptyState("No contacts yet", "Connect with people first, then add them to a group.") }
            }
            itemsIndexed(state.contacts, key = { _, user -> user.id }) { index, user ->
                Reveal(
                    index = index, key = user.id, tracker = tracker,
                    modifier = Modifier.animateItem(placementSpec = Motion.slow())
                ) {
                    MemberRow(user, selected = user.id in state.selected, onToggle = { onToggle(user.id) })
                }
            }
        }
    }
}

@Composable
private fun MemberRow(user: PublicUser, selected: Boolean, onToggle: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        Motion.slow(), label = "rowFill"
    )
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant,
        Motion.slow(), label = "rowBorder"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(user.fullName, online = user.isOnline)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(user.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LanguageChip(user.preferredLanguage)
        }
        SelectCircle(selected)
    }
}

@Composable
private fun SelectCircle(selected: Boolean) {
    val fillAlpha by animateFloatAsState(if (selected) 1f else 0f, Motion.slow(), label = "checkFill")
    val checkScale by animateFloatAsState(if (selected) 1f else 0.4f, Motion.slow(), label = "checkScale")
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(28.dp).graphicsLayer { alpha = fillAlpha }.clip(CircleShape).background(MultivoBrush.accent))
        Icon(
            Icons.Filled.Check, contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp).graphicsLayer { alpha = fillAlpha; scaleX = checkScale; scaleY = checkScale }
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun NewGroupPreview() = MultivoTheme {
    NewGroupScreen(
        NewGroupUiState(name = "Weekend trip", contacts = SampleData.contacts, selected = setOf("u1", "u3"), loading = false),
        {}, {}, {}, {}
    )
}
