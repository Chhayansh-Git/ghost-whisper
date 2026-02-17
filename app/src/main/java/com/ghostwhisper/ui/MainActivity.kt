package com.ghostwhisper.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ghostwhisper.data.db.KeyringDatabase
import com.ghostwhisper.data.repository.AuthRepository
import com.ghostwhisper.data.repository.KeyringRepository
import com.ghostwhisper.data.repository.SettingsRepository
import com.ghostwhisper.data.repository.UserRepository
import com.ghostwhisper.service.BiometricHelper
import com.ghostwhisper.ui.screens.*
import com.ghostwhisper.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Main Activity — single-activity architecture with Compose Navigation.
 *
 * Handles:
 * - Auth-gated access (Login → main app)
 * - Bottom navigation (Home, Channels, Settings, TestBench)
 * - Deep link imports (ghostwhisper://join?key=...&name=...)
 */
class MainActivity : FragmentActivity() {
    // State for pending invitation
    private var pendingInvitation by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            GhostWhisperTheme {
                AppLockWrapper {
                    GhostWhisperRoot(
                            pendingInvitation = pendingInvitation,
                            onAcceptInvitation = { name, key ->
                                importChannelAndNotify(name, key)
                                pendingInvitation = null
                            },
                            onRejectInvitation = { pendingInvitation = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val isCustomScheme = uri.scheme == "ghostwhisper" && uri.host == "join"
        // Also handle the https link in case App Links eventually work
        val isWebScheme = uri.scheme == "https" && uri.host == "chhayansh-git.github.io"

        if (isCustomScheme || isWebScheme) {
            val key = uri.getQueryParameter("key")
            val name = uri.getQueryParameter("name")
            if (key != null && name != null) {
                pendingInvitation = name to key
            }
        }
    }

    private fun importChannelAndNotify(name: String, key: String) {
        val repository = KeyringRepository(KeyringDatabase.getInstance(this).keyringDao())
        kotlinx.coroutines.GlobalScope.launch {
            try {
                repository.importChannel(name, key)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Joined \"$name\"! 🔒", Toast.LENGTH_SHORT)
                            .show()
                    // Prompt to notify inviter
                    promptToNotifyInviter(name)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG)
                            .show()
                }
            }
        }
    }

    private fun promptToNotifyInviter(channelName: String) {
        // We cannot know who sent the link, so we let the user pick the contact to reply to.
        val message =
                "🔒 I have accepted your invitation to join the Ghost Whisper channel \"$channelName\"."
        val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                }
        try {
            startActivity(Intent.createChooser(intent, "Notify Inviter via..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open WhatsApp to notify inviter.", Toast.LENGTH_SHORT)
                    .show()
        }
    }
}

@Composable
fun GhostWhisperRoot(
        pendingInvitation: Pair<String, String>? = null,
        onAcceptInvitation: (String, String) -> Unit = { _, _ -> },
        onRejectInvitation: () -> Unit = {}
) {
    val authRepository = remember { AuthRepository() }
    val userRepository = remember { UserRepository() }
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn) }

    if (pendingInvitation != null) {
        AlertDialog(
                onDismissRequest = onRejectInvitation,
                icon = { Icon(Icons.Filled.Mail, contentDescription = null, tint = GhostPurple) },
                title = { Text("Join Channel?") },
                text = {
                    Text(
                            "You have been invited to join the private channel:\n\n\"${pendingInvitation.first}\"\n\nDo you want to accept this key?"
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                onAcceptInvitation(
                                        pendingInvitation.first,
                                        pendingInvitation.second
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GhostPurple)
                    ) { Text("Accept & Join") }
                },
                dismissButton = {
                    TextButton(onClick = onRejectInvitation) {
                        Text("Reject", color = MaterialTheme.colorScheme.error)
                    }
                }
        )
    }

    if (!isLoggedIn || authRepository.currentUser == null) {
        LoginScreen(
                authRepository = authRepository,
                userRepository = userRepository,
                webClientId = stringResource(com.ghostwhisper.R.string.default_web_client_id),
                onLoginSuccess = { isLoggedIn = true }
        )
    } else {
        GhostWhisperMainApp(onSignOut = { isLoggedIn = false })
    }
}

@Composable
fun AppLockWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val settingsRepository = remember { SettingsRepository(context) }
    val biometricHelper = remember { BiometricHelper(context) }

    var isLocked by remember { mutableStateOf(false) }

    // Monitor lifecycle to lock on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (settingsRepository.appLockEnabled) {
                    isLocked = true
                    activity?.let {
                        biometricHelper.authenticate(
                                it,
                                onSuccess = { isLocked = false },
                                onFailure = { /* Keep locked */}
                        )
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isLocked) {
        Box(
                modifier = Modifier.fillMaxSize().background(DarkBackground),
                contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = GhostPurple,
                        modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                        "Ghost Whisper Locked",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                        onClick = {
                            activity?.let {
                                biometricHelper.authenticate(
                                        it,
                                        onSuccess = { isLocked = false },
                                        onFailure = {}
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GhostPurple)
                ) { Text("Unlock") }
            }
        }
    } else {
        content()
    }
}

// ─── Navigation ──────────────────────────────────────────────────

sealed class Screen(
        val route: String,
        val title: String,
        val icon: ImageVector,
        val selectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Channels : Screen("channels", "Channels", Icons.Outlined.Key, Icons.Filled.Key)
    data object Settings :
            Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
    data object TestBench :
            Screen("testbench", "Test", Icons.Outlined.Science, Icons.Filled.Science)
}

private val screens = listOf(Screen.Home, Screen.Channels, Screen.Settings, Screen.TestBench)

/** Root composable — checks auth state and routes accordingly. */
@Composable
fun GhostWhisperRoot() {
    val authRepository = remember { AuthRepository() }
    val userRepository = remember { UserRepository() }
    // Use rememberUpdatedState or side effect to check auth status if needed,
    // but mutableStateOf(authRepository.isLoggedIn) is fine for start.
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn) }

    if (!isLoggedIn || authRepository.currentUser == null) {
        LoginScreen(
                authRepository = authRepository,
                userRepository = userRepository,
                webClientId = stringResource(com.ghostwhisper.R.string.default_web_client_id),
                onLoginSuccess = { isLoggedIn = true }
        )
    } else {
        GhostWhisperMainApp(onSignOut = { isLoggedIn = false })
    }
}

@Composable
fun GhostWhisperMainApp(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    // Handle navigate_to intent from widget / deep links
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        val navigateTo = activity?.intent?.getStringExtra("navigate_to")
        if (navigateTo == "steganography") {
            navController.navigate("steganography") { launchSingleTop = true }
            // Clear so it doesn't re-trigger
            activity.intent?.removeExtra("navigate_to")
        }
    }

    Scaffold(
            containerColor = DarkBackground,
            bottomBar = {
                NavigationBar(containerColor = DarkSurface, contentColor = TextPrimary) {
                    screens.forEach { screen ->
                        val selected =
                                currentDestination?.hierarchy?.any { it.route == screen.route } ==
                                        true
                        NavigationBarItem(
                                icon = {
                                    Icon(
                                            if (selected) screen.selectedIcon else screen.icon,
                                            contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title) },
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors =
                                        NavigationBarItemDefaults.colors(
                                                selectedIconColor = GhostPurple,
                                                selectedTextColor = GhostPurple,
                                                unselectedIconColor = TextMuted,
                                                unselectedTextColor = TextMuted,
                                                indicatorColor = GhostPurple.copy(alpha = 0.15f)
                                        )
                        )
                    }
                }
            }
    ) { paddingValues ->
        NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize().padding(paddingValues).background(DarkBackground)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Channels.route) { ChannelScreen() }
            composable(Screen.Settings.route) { SettingsScreen(navController, onSignOut) }
            composable(Screen.TestBench.route) { TestBenchScreen() }
            composable("steganography") { SteganographyScreen(navController) }
        }
    }
}
