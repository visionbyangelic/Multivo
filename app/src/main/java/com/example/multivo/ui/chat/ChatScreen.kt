package com.example.multivo.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.multivo.data.ChatMessage
import com.example.multivo.data.Languages
import com.example.multivo.ui.components.Avatar
import com.example.multivo.ui.components.EmptyState
import com.example.multivo.ui.components.Reveal
import com.example.multivo.ui.components.RevealFrom
import com.example.multivo.ui.components.TypingDots
import com.example.multivo.ui.components.clockTime
import com.example.multivo.ui.components.multivoFieldColors
import com.example.multivo.ui.components.rememberRevealTracker
import com.example.multivo.ui.components.screenBackdrop
import com.example.multivo.ui.containerViewModel
import com.example.multivo.ui.preview.SampleData
import com.example.multivo.ui.theme.Motion
import com.example.multivo.ui.theme.MultivoBrush
import com.example.multivo.ui.theme.MultivoTheme
import com.example.multivo.ui.theme.Online

@Composable
fun ChatRoute(conversationId: String, onBack: () -> Unit) {
    val vm = containerViewModel(key = conversationId) { ChatViewModel(it, conversationId) }
    val state by vm.state.collectAsStateWithLifecycle()
    ChatScreen(
        state = state,
        myId = vm.myId,
        onBack = onBack,
        onDraft = vm::onDraft,
        onSend = vm::send,
        onToggleOriginal = vm::toggleOriginal,
        onDismissError = vm::dismissError
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    myId: String,
    onBack: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onToggleOriginal: (String) -> Unit,
    onDismissError: () -> Unit
) {
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val tracker = rememberRevealTracker()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); onDismissError() }
    }

    val conversation = state.conversation
    val others = conversation?.participants?.filter { it.userId != myId }.orEmpty()
    val typing = state.typingNames.isNotEmpty()
    val subtitle = when {
        typing -> state.typingNames.joinToString() + " typing"
        conversation?.isGroup == true -> "${conversation.participants.size} members"
        others.firstOrNull()?.isOnline == true -> "Online · reads ${Languages.nameOf(others.first().preferredLanguage)}"
        others.isNotEmpty() -> "Reads ${Languages.nameOf(others.first().preferredLanguage)}"
        else -> ""
    }

    Scaffold(
        modifier = Modifier.screenBackdrop(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ChatHeader(
                title = conversation?.title.orEmpty(),
                subtitle = subtitle,
                typing = typing,
                isGroup = conversation?.isGroup == true,
                online = others.firstOrNull()?.isOnline,
                onBack = onBack
            )
        },
        bottomBar = { Composer(state.draft, state.sending, onDraft, onSend) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
            if (!state.loading && state.messages.isEmpty()) {
                EmptyState(
                    "Start the conversation",
                    "Write in your own language — everyone here reads it in theirs.",
                    Modifier.align(Alignment.Center)
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    val mine = message.senderId == myId
                    Reveal(
                        key = message.id,
                        tracker = tracker,
                        from = if (mine) RevealFrom.End else RevealFrom.Start,
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = Motion.slow(),
                            fadeOutSpec = Motion.slow()
                        )
                    ) {
                        MessageBubble(
                            message = message,
                            mine = mine,
                            showSender = conversation?.isGroup == true,
                            showOriginal = message.id in state.showOriginal,
                            onToggleOriginal = { onToggleOriginal(message.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    title: String,
    subtitle: String,
    typing: Boolean,
    isGroup: Boolean,
    online: Boolean?,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Reveal {
                Avatar(title, size = 42.dp, online = if (isGroup) null else online, ring = true)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AnimatedContent(
                    targetState = subtitle to typing,
                    transitionSpec = { fadeIn(Motion.slow()) togetherWith fadeOut(Motion.slow()) },
                    label = "subtitle"
                ) { (text, isTyping) ->
                    if (text.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isTyping -> MaterialTheme.colorScheme.secondary
                                    text.startsWith("Online") -> Online
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isTyping) {
                                Spacer(Modifier.width(6.dp))
                                TypingDots(MaterialTheme.colorScheme.secondary, dot = 4.dp)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    showSender: Boolean,
    showOriginal: Boolean,
    onToggleOriginal: () -> Unit
) {
    val translated = !mine && message.translatedText != null
    val textColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if (mine) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    val bubble = if (mine) {
        Modifier.clip(shape).background(MultivoBrush.accent)
    } else {
        Modifier.clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .then(bubble)
                .animateContentSize(Motion.slow())
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showSender && !mine) {
                Text(message.senderName, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary)
            }
            Text(if (translated) message.translatedText!! else message.originalText,
                style = MaterialTheme.typography.bodyLarge, color = textColor)

            AnimatedVisibility(
                visible = translated && showOriginal,
                enter = fadeIn(Motion.slow()) + expandVertically(Motion.slow()),
                exit = fadeOut(Motion.slow()) + shrinkVertically(Motion.slow())
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
                        .padding(10.dp)
                ) {
                    Text(message.originalText, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (translated) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onToggleOriginal)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(4.dp))
                        Text(
                            (if (showOriginal) "Hide original" else "From ${Languages.nameOf(message.originalLanguage)} · See original"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.weight(1f, fill = false))
                Text(clockTime(message.createdAt), style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun Composer(draft: String, sending: Boolean, onDraft: (String) -> Unit, onSend: () -> Unit) {
    val canSend = draft.isNotBlank() && !sending
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.86f, Motion.slow(), label = "sendScale")
    val sendAlpha by animateFloatAsState(if (canSend) 1f else 0.45f, Motion.slow(), label = "sendAlpha")

    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(30.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = { Text("Write in your language…") },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            colors = multivoFieldColors().copy(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer { scaleX = sendScale; scaleY = sendScale; alpha = sendAlpha }
                .clip(CircleShape)
                .background(MultivoBrush.accent)
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ChatPreview() = MultivoTheme {
    ChatScreen(
        ChatUiState(
            conversation = SampleData.conversations.first(),
            messages = SampleData.messages,
            loading = false,
            draft = "",
            showOriginal = setOf("m3")
        ),
        myId = "me", onBack = {}, onDraft = {}, onSend = {}, onToggleOriginal = {}, onDismissError = {}
    )
}
