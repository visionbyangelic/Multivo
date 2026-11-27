package com.example.multivo.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.multivo.ui.chats.ChatsRoute
import com.example.multivo.ui.contacts.ContactsRoute
import com.example.multivo.ui.profile.ProfileRoute
import com.example.multivo.ui.theme.Motion

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Chats", Icons.AutoMirrored.Outlined.Chat),
    Tab("People", Icons.Outlined.People),
    Tab("Profile", Icons.Outlined.AccountCircle)
)

@Composable
fun HomeScreen(onOpenChat: (String) -> Unit, onNewGroup: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        // Tabs fade into each other slowly.
        Crossfade(targetState = selected, animationSpec = Motion.slower(), label = "tab") { tab ->
            when (tab) {
                0 -> ChatsRoute(onOpenChat = onOpenChat, onNewGroup = onNewGroup, modifier = modifier)
                1 -> ContactsRoute(onOpenChat = onOpenChat, modifier = modifier)
                else -> ProfileRoute(modifier = modifier)
            }
        }
    }
}
