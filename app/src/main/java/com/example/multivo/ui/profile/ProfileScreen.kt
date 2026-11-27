package com.example.multivo.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.multivo.ui.components.Avatar
import com.example.multivo.ui.components.GlowCard
import com.example.multivo.ui.components.GradientButton
import com.example.multivo.ui.components.LanguagePicker
import com.example.multivo.ui.components.PulsingDot
import com.example.multivo.ui.components.Reveal
import com.example.multivo.ui.components.SectionLabel
import com.example.multivo.ui.components.multivoFieldColors
import com.example.multivo.ui.components.screenBackdrop
import com.example.multivo.ui.containerViewModel
import com.example.multivo.ui.preview.SampleData
import com.example.multivo.ui.theme.Motion
import com.example.multivo.ui.theme.MultivoBrush
import com.example.multivo.ui.theme.MultivoTheme
import com.example.multivo.ui.theme.Online

@Composable
fun ProfileRoute(modifier: Modifier = Modifier) {
    val vm = containerViewModel { ProfileViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    ProfileScreen(state, vm::onName, vm::onBio, vm::onLanguage, vm::save, vm::signOut, vm::dismissMessage, modifier)
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onName: (String) -> Unit,
    onBio: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onSave: () -> Unit,
    onSignOut: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onDismissMessage() }
    }

    Box(modifier.fillMaxSize().screenBackdrop()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Reveal { Text("Profile", style = MaterialTheme.typography.headlineMedium) }

            Reveal(index = 1) { IdentityCard(state) }

            Reveal(index = 2) {
                GlowCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                    SectionLabel("About you")
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        state.fullName, onName, label = { Text("Full name") }, singleLine = true,
                        shape = MaterialTheme.shapes.small, colors = multivoFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        state.bio, onBio, label = { Text("Bio") }, minLines = 2, maxLines = 4,
                        shape = MaterialTheme.shapes.small, colors = multivoFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Reveal(index = 3) {
                GlowCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                    SectionLabel("Language")
                    Spacer(Modifier.height(14.dp))
                    LanguagePicker(state.language, onLanguage)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Everything people send you is translated into this language.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Reveal(index = 4) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientButton(
                        onClick = onSave,
                        enabled = state.dirty && !state.saving,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        AnimatedContent(
                            targetState = when {
                                state.saving -> "Saving…"
                                state.dirty -> "Save changes"
                                else -> "All changes saved"
                            },
                            transitionSpec = { fadeIn(Motion.slow()) togetherWith fadeOut(Motion.slow()) },
                            label = "saveLabel"
                        ) { Text(it) }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Sign out")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun IdentityCard(state: ProfileUiState) {
    val shape = MaterialTheme.shapes.large
    val statusColor by animateColorAsState(
        if (state.connected) Online else MaterialTheme.colorScheme.outline, Motion.slow(), label = "status"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MultivoBrush.card)
            .border(1.dp, MultivoBrush.accentSoft, shape)
            .padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(MultivoBrush.accentSoft))
            Box(Modifier.size(108.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer))
            Avatar(state.fullName.ifBlank { "?" }, size = 92.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            state.user?.fullName.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(Icons.Outlined.AlternateEmail, state.user?.username.orEmpty())
            InfoPill(Icons.Outlined.Email, state.user?.email.orEmpty(), Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.connected) PulsingDot(statusColor) else Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = state.connected,
                transitionSpec = { fadeIn(Motion.slow()) togetherWith fadeOut(Motion.slow()) },
                label = "liveLabel"
            ) { connected ->
                Text(
                    if (connected) "Live updates on" else "Connecting…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ProfilePreview() = MultivoTheme {
    val u = SampleData.me
    ProfileScreen(
        ProfileUiState(user = u, fullName = u.fullName, bio = u.bio.orEmpty(), language = "yo", connected = true),
        {}, {}, {}, {}, {}, {}
    )
}
