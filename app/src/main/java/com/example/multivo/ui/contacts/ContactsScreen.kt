package com.example.multivo.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.multivo.data.ConnectionRequest
import com.example.multivo.data.PublicUser
import com.example.multivo.ui.components.Avatar
import com.example.multivo.ui.components.EmptyState
import com.example.multivo.ui.components.GlowCard
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

@Composable
fun ContactsRoute(onOpenChat: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm = containerViewModel { ContactsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.openChat.collect(onOpenChat) }
    ContactsScreen(
        state = state,
        onQuery = vm::onQuery,
        onConnect = vm::connect,
        onAccept = vm::accept,
        onReject = vm::reject,
        onMessage = vm::message,
        onDismissMessage = vm::dismissMessage,
        modifier = modifier
    )
}

@Composable
fun ContactsScreen(
    state: ContactsUiState,
    onQuery: (String) -> Unit,
    onConnect: (PublicUser) -> Unit,
    onAccept: (ConnectionRequest) -> Unit,
    onReject: (ConnectionRequest) -> Unit,
    onMessage: (PublicUser) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    val tracker = rememberRevealTracker()
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }
    val searchMode = state.query.trim().length >= 2

    Box(modifier.fillMaxSize().screenBackdrop()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") {
                Column {
                    Reveal(key = "title", tracker = tracker) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp)) {
                            Text("People", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Find friends anywhere — you each read in your own language.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Reveal(key = "search", tracker = tracker, index = 1) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQuery,
                            placeholder = { Text("Search by name, username or email") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Clear search") }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            colors = multivoFieldColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        )
                    }
                    if (state.searching || state.loading) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 10.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }

            if (searchMode) {
                item(key = "results-title") { Section("Search results", Modifier.animateItem(placementSpec = Motion.slow())) }
                if (!state.searching && state.results.isEmpty()) {
                    item(key = "results-empty") {
                        EmptyState("Nobody found", "Check the spelling, or ask your friend for their username.")
                    }
                }
                itemsIndexed(state.results, key = { _, item -> "s" + item.id }) { index, user ->
                    Reveal(
                        index = index, key = "s" + user.id, tracker = tracker,
                        modifier = Modifier.animateItem(placementSpec = Motion.slow(), fadeOutSpec = Motion.slow())
                    ) {
                        PersonRow(user) {
                            when (user.relationship) {
                                "contact" -> SmallGradientButton("Message") { onMessage(user) }
                                "request_sent" -> StatusPill("Requested")
                                "request_received" -> SmallGradientButton("Accept") { onConnect(user) }
                                else -> SmallGradientButton("Connect", icon = true) { onConnect(user) }
                            }
                        }
                    }
                }
            } else {
                if (state.requests.isNotEmpty()) {
                    item(key = "requests-title") {
                        Section("Requests · ${state.requests.size}", Modifier.animateItem(placementSpec = Motion.slow()))
                    }
                    itemsIndexed(state.requests, key = { _, item -> "r" + item.id }) { index, request ->
                        Reveal(
                            index = index, key = "r" + request.id, tracker = tracker,
                            modifier = Modifier.animateItem(placementSpec = Motion.slow(), fadeOutSpec = Motion.slow())
                        ) {
                            RequestCard(request, onAccept = { onAccept(request) }, onReject = { onReject(request) })
                        }
                    }
                }
                item(key = "contacts-title") {
                    Section("Your contacts", Modifier.animateItem(placementSpec = Motion.slow()))
                }
                if (!state.loading && state.contacts.isEmpty()) {
                    item(key = "contacts-empty") {
                        EmptyState("No contacts yet", "Search above to find friends and send them a request.")
                    }
                }
                itemsIndexed(state.contacts, key = { _, item -> "c" + item.id }) { index, user ->
                    Reveal(
                        index = index + state.requests.size, key = "c" + user.id, tracker = tracker,
                        modifier = Modifier.animateItem(placementSpec = Motion.slow(), fadeOutSpec = Motion.slow())
                    ) {
                        PersonRow(user) {
                            IconButton(onClick = { onMessage(user) }) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = "Message ${user.fullName}",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(48.dp)) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Section(text: String, modifier: Modifier = Modifier) {
    SectionLabel(text, modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp))
}

@Composable
private fun PersonRow(user: PublicUser, action: @Composable () -> Unit) {
    GlowCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        contentPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.fullName, online = user.isOnline)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    LanguageChip(user.preferredLanguage)
                }
            }
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
private fun SmallGradientButton(label: String, icon: Boolean = false, onClick: () -> Unit) {
    GradientButton(onClick = onClick, modifier = Modifier.height(40.dp)) {
        if (icon) {
            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label)
    }
}

@Composable
private fun StatusPill(label: String) {
    Box(
        Modifier.clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RequestCard(request: ConnectionRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            .border(1.dp, MultivoBrush.accentSoft, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(request.sender.fullName, online = request.sender.isOnline, ring = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(request.sender.fullName, style = MaterialTheme.typography.titleMedium)
                Text("@${request.sender.username} wants to connect", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            LanguageChip(request.sender.preferredLanguage)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) { Text("Decline") }
            GradientButton(onClick = onAccept, modifier = Modifier.weight(1f).height(46.dp)) { Text("Accept") }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ContactsPreview() = MultivoTheme {
    ContactsScreen(
        ContactsUiState(loading = false, requests = SampleData.requests, contacts = SampleData.contacts),
        {}, {}, {}, {}, {}, {}
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 600)
@Composable
private fun SearchPreview() = MultivoTheme {
    ContactsScreen(
        ContactsUiState(loading = false, query = "tu", results = SampleData.searchResults),
        {}, {}, {}, {}, {}, {}
    )
}
