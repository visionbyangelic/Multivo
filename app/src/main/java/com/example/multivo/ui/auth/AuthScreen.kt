package com.example.multivo.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.multivo.data.Languages
import com.example.multivo.ui.components.GlowCard
import com.example.multivo.ui.components.GradientButton
import com.example.multivo.ui.components.LanguagePicker
import com.example.multivo.ui.components.multivoFieldColors
import com.example.multivo.ui.components.screenBackdrop
import com.example.multivo.ui.containerViewModel
import com.example.multivo.ui.theme.Motion
import com.example.multivo.ui.theme.MultivoTheme

/** Signed-out flow: the Sign in page, with its own separate Create account page. */
@Composable
fun AuthRoute() {
    val vm = containerViewModel { AuthViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var onSignUp by rememberSaveable { mutableStateOf(false) }

    fun goTo(signUp: Boolean) {
        vm.clearForScreenChange()
        onSignUp = signUp
    }

    // Phone back button on Create account returns to Sign in instead of closing the app.
    BackHandler(enabled = onSignUp) { goTo(false) }

    AnimatedContent(
        targetState = onSignUp,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (fadeIn(Motion.slower()) + slideInHorizontally(Motion.slower()) { direction * it / 6 })
                .togetherWith(fadeOut(Motion.slow()))
        },
        label = "authPage"
    ) { signUp ->
        if (signUp) {
            SignUpScreen(state = state, onChange = vm::update, onSubmit = vm::signUp, onGoToSignIn = { goTo(false) })
        } else {
            SignInScreen(state = state, onChange = vm::update, onSubmit = vm::signIn, onGoToSignUp = { goTo(true) })
        }
    }
}

@Composable
fun SignInScreen(
    state: AuthUiState,
    onChange: ((AuthUiState) -> AuthUiState) -> Unit,
    onSubmit: () -> Unit,
    onGoToSignUp: () -> Unit
) {
    AuthPage {
        Brand(title = "Welcome back", subtitle = "Sign in to keep chatting in your language.")

        Field("Username or email", state.identifier, KeyboardType.Email) { v -> onChange { it.copy(identifier = v) } }
        PasswordField(state.password) { v -> onChange { it.copy(password = v) } }

        ErrorBox(state.error)
        SubmitButton(label = "Sign in", loading = state.loading, onClick = onSubmit)

        SwitchLink(question = "New to Multivo?", action = "Create an account", onClick = onGoToSignUp)
    }
}

@Composable
fun SignUpScreen(
    state: AuthUiState,
    onChange: ((AuthUiState) -> AuthUiState) -> Unit,
    onSubmit: () -> Unit,
    onGoToSignIn: () -> Unit
) {
    AuthPage {
        Brand(title = "Create your account", subtitle = "Talk in your language.\nThey read it in theirs.")

        Field("Full name", state.fullName) { v -> onChange { it.copy(fullName = v) } }
        Field("Username", state.username) { v -> onChange { it.copy(username = v) } }
        Field("Email", state.email, KeyboardType.Email) { v -> onChange { it.copy(email = v) } }
        PasswordField(state.password, hint = "At least 8 characters") { v -> onChange { it.copy(password = v) } }

        AutoTranslateCard(
            language = state.language,
            changing = state.changingLanguage,
            onToggleChange = { onChange { it.copy(changingLanguage = !it.changingLanguage) } },
            onSelected = { v -> onChange { it.copy(language = v, changingLanguage = false) } }
        )

        ErrorBox(state.error)
        SubmitButton(label = "Create account", loading = state.loading, onClick = onSubmit)

        SwitchLink(question = "Already have an account?", action = "Sign in", onClick = onGoToSignIn)
    }
}

/** Shows the language picked from the phone; messages are translated into it automatically. */
@Composable
private fun AutoTranslateCard(
    language: String,
    changing: Boolean,
    onToggleChange: () -> Unit,
    onSelected: (String) -> Unit
) {
    GlowCard(Modifier.fillMaxWidth(), highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Auto-translate is on", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Every message you get is translated into ${Languages.nameOf(language)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onToggleChange) { Text(if (changing) "Done" else "Change") }
        }
        AnimatedVisibility(
            visible = changing,
            enter = fadeIn(Motion.slow()) + expandVertically(Motion.slow()),
            exit = fadeOut(Motion.slow()) + shrinkVertically(Motion.slow())
        ) {
            LanguagePicker(selected = language, onSelected = onSelected, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun AuthPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackdrop()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun Brand(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
            }
            Text("Multivo", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        colors = multivoFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(value: String, hint: String? = null, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text("Password") },
        supportingText = hint?.let { h -> @Composable { Text(h) } },
        singleLine = true,
        colors = multivoFieldColors(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ErrorBox(error: String?) {
    error ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Text(
            error,
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SubmitButton(label: String, loading: Boolean, onClick: () -> Unit) {
    GradientButton(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(label)
        }
    }
}

@Composable
private fun SwitchLink(question: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(question, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignInPreview() = MultivoTheme {
    SignInScreen(AuthUiState(identifier = "ada"), {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun SignUpPreview() = MultivoTheme {
    SignUpScreen(
        AuthUiState(fullName = "Ada Obi", username = "ada", email = "ada@example.com", language = "yo"),
        {}, {}, {}
    )
}
