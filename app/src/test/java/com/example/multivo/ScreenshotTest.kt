package com.example.multivo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.example.multivo.ui.auth.SignInScreen
import com.example.multivo.ui.auth.SignUpScreen
import com.example.multivo.ui.auth.AuthUiState
import com.example.multivo.ui.chat.ChatScreen
import com.example.multivo.ui.chat.ChatUiState
import com.example.multivo.ui.chats.ChatsScreen
import com.example.multivo.ui.chats.ChatsUiState
import com.example.multivo.ui.contacts.ContactsScreen
import com.example.multivo.ui.contacts.ContactsUiState
import com.example.multivo.ui.group.NewGroupScreen
import com.example.multivo.ui.group.NewGroupUiState
import com.example.multivo.ui.preview.SampleData
import com.example.multivo.ui.profile.ProfileScreen
import com.example.multivo.ui.profile.ProfileUiState
import com.example.multivo.ui.theme.MultivoTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen on the JVM (no emulator needed).
 *   ./gradlew :app:recordRoborazziDebug   -> PNGs in app/build/outputs/roborazzi
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test fun signIn() {
        var openedSignUp = false
        compose.setContent { MultivoTheme { SignInScreen(AuthUiState(identifier = "ada"), {}, {}, { openedSignUp = true }) } }
        // Sign in has no server address box and no sign-up fields.
        compose.onNodeWithText("Server address").assertDoesNotExist()
        compose.onNodeWithText("Full name").assertDoesNotExist()
        compose.onNodeWithText("Create an account").performClick()
        assertTrue(openedSignUp)
        shot("01_sign_in")
    }

    @Test fun signUp() {
        compose.setContent {
            MultivoTheme {
                SignUpScreen(AuthUiState(fullName = "Ada Obi", username = "ada", email = "ada@example.com",
                    password = "password1", language = "yo"), {}, {}, {})
            }
        }
        compose.onNodeWithText("Auto-translate is on").assertIsDisplayed()
        compose.onNodeWithText("Every message you get is translated into Yoruba.").assertIsDisplayed()
        compose.onNodeWithText("Server address").assertDoesNotExist()
        shot("02_create_account")
    }

    @Test fun chats() {
        compose.setContent { MultivoTheme { ChatsScreen(ChatsUiState(false, SampleData.conversations), "me", {}, {}, {}) } }
        shot("03_chats")
    }

    @Test fun people() {
        compose.setContent {
            MultivoTheme {
                ContactsScreen(ContactsUiState(loading = false, requests = SampleData.requests, contacts = SampleData.contacts),
                    {}, {}, {}, {}, {}, {})
            }
        }
        shot("04_people")
    }

    @Test fun search() {
        compose.setContent {
            MultivoTheme {
                ContactsScreen(ContactsUiState(loading = false, query = "tu", results = SampleData.searchResults),
                    {}, {}, {}, {}, {}, {})
            }
        }
        shot("05_search")
    }

    @Test fun chatWithTranslationToggle() {
        compose.setContent {
            var shown by remember { mutableStateOf(emptySet<String>()) }
            MultivoTheme {
                ChatScreen(
                    ChatUiState(conversation = SampleData.conversations.first(), messages = SampleData.messages,
                        loading = false, showOriginal = shown),
                    myId = "me", onBack = {}, onDraft = {}, onSend = {},
                    onToggleOriginal = { id -> shown = if (id in shown) shown - id else shown + id },
                    onDismissError = {}
                )
            }
        }
        // The French message is shown translated; tapping reveals the original.
        compose.onNodeWithText("Perfect, I'll book a table near the harbour.").assertIsDisplayed()
        compose.onAllNodesWithText("From French · See original")[1].performClick()
        compose.onNodeWithText("Parfait, je réserve une table près du port.").assertIsDisplayed()
        shot("06_chat")
    }

    @Test fun newGroup() {
        compose.setContent {
            MultivoTheme {
                NewGroupScreen(NewGroupUiState(name = "Weekend trip", contacts = SampleData.contacts,
                    selected = setOf("u1", "u3"), loading = false), {}, {}, {}, {})
            }
        }
        shot("07_new_group")
    }

    @Test fun profile() {
        compose.setContent {
            val u = SampleData.me
            MultivoTheme {
                ProfileScreen(ProfileUiState(user = u, fullName = u.fullName, bio = u.bio.orEmpty(), language = "yo",
                    connected = true), {}, {}, {}, {}, {}, {})
            }
        }
        shot("08_profile")
    }
}
