package com.example.multivo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.multivo.data.AuthState
import com.example.multivo.ui.appContainer
import com.example.multivo.ui.auth.AuthRoute
import com.example.multivo.ui.chat.ChatRoute
import com.example.multivo.ui.group.NewGroupRoute
import com.example.multivo.ui.home.HomeScreen
import com.example.multivo.ui.theme.Motion
import com.example.multivo.ui.theme.MultivoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status/navigation bar icons on the dark purple screens.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MultivoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MultivoNavHost()
                }
            }
        }
    }
}

@Composable
fun MultivoNavHost() {
    val authState by appContainer().authState.collectAsStateWithLifecycle()

    when (authState) {
        AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        AuthState.SignedOut -> AuthRoute()
        is AuthState.SignedIn -> SignedInNavHost()
    }
}

@Composable
private fun SignedInNavHost() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "home",
        // Screens glide in and out slowly.
        enterTransition = { fadeIn(Motion.slower()) + slideInHorizontally(Motion.slower()) { it / 6 } },
        exitTransition = { fadeOut(Motion.slow()) },
        popEnterTransition = { fadeIn(Motion.slower()) },
        popExitTransition = { fadeOut(Motion.slow()) + slideOutHorizontally(Motion.slower()) { it / 6 } }
    ) {
        composable("home") {
            HomeScreen(
                onOpenChat = { id -> nav.navigate("chat/$id") },
                onNewGroup = { nav.navigate("new-group") }
            )
        }
        composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ChatRoute(conversationId = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable("new-group") {
            NewGroupRoute(
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate("chat/$id") { popUpTo("home") }
                }
            )
        }
    }
}
