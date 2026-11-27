package com.example.multivo.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.multivo.data.ConversationSummary
import com.example.multivo.ui.appContainer
import com.example.multivo.ui.components.Avatar
import com.example.multivo.ui.components.CountBadge
import com.example.multivo.ui.components.EmptyState
import com.example.multivo.ui.components.screenBackdrop
import com.example.multivo.ui.components.shortTime
import com.example.multivo.ui.containerViewModel
import com.example.multivo.ui.preview.SampleData
import com.example.multivo.ui.theme.MultivoTheme

@Composable
fun ChatsRoute(onOpenChat: (String) -> Unit, onNewGroup: () -> Unit, modifier: Modifier = Modifier) {
    val vm = containerViewModel { ChatsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val myId = appContainer().currentUser?.id.orEmpty()
    ChatsScreen(state, myId, onOpenChat, onNewGroup, onRefresh = { vm.refresh() }, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    state: ChatsUiState,
    myId: String,
    onOpenChat: (String) -> Unit,
    onNewGroup: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize().screenBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Chats",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
            )
            if (state.loading && state.conversations.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
            state.error?.let { ErrorBanner(it, onRefresh) }

            PullToRefreshBox(isRefreshing = state.loading && state.conversations.isNotEmpty(), onRefresh = onRefresh) {
                if (!state.loading && state.conversations.isEmpty() && state.error == null) {
                    EmptyState(
                        "No chats yet",
                        "Find people in the People tab, connect, and start a conversation in any language."
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        ConversationRow(conversation, myId) { onOpenChat(conversation.id) }
                        HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewGroup,
            icon = { Icon(Icons.Outlined.GroupAdd, contentDescription = null) },
            text = { Text("New group") },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSummary, myId: String, onClick: () -> Unit) {
    val other = conversation.participants.firstOrNull { it.userId != myId }
    val last = conversation.lastMessage
    val preview = when {
        last == null -> if (conversation.isGroup) "${conversation.participants.size} members" else "Say hello 👋"
        else -> {
            val text = last.translatedText ?: last.originalText
            val who = when {
                last.senderId == myId -> "You: "
                conversation.isGroup -> last.senderName.substringBefore(' ') + ": "
                else -> ""
            }
            who + text
        }
    }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(conversation.title, online = if (conversation.isGroup) null else other?.isOnline)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    shortTime(last?.createdAt ?: conversation.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.unreadCount > 0) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                CountBadge(conversation.unreadCount)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun ChatsPreview() = MultivoTheme {
    ChatsScreen(ChatsUiState(loading = false, conversations = SampleData.conversations), "me", {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun ChatsEmptyPreview() = MultivoTheme {
    ChatsScreen(ChatsUiState(loading = false), "me", {}, {}, {})
}
