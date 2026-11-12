package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.AstraRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.chat.ChatScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.doc.DocAnalyzerScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.image.ImageStudioScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.resume.ResumeBuilderScreen
import com.example.ui.scanner.ScannerScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.welcome.WelcomeScreen
import com.example.ui.writing.WritingToolsScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String, val baseRoute: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "home", "Home", Icons.Default.Home)
    data object Chat : Screen("chat?prompt={prompt}&convId={convId}", "chat", "Chat", Icons.Default.Chat)
    data object Tools : Screen("tools?tool={tool}", "tools", "Tools", Icons.Default.AutoAwesome)
    data object History : Screen("history", "history", "History", Icons.Default.History)
    data object Profile : Screen("profile", "profile", "Profile", Icons.Default.Person)

    // Sub-screens
    data object ImageStudio : Screen("image_studio", "image_studio", "Create", Icons.Default.AutoAwesome)
    data object Scanner : Screen("scanner", "scanner", "Scanner", Icons.Default.AutoAwesome)
    data object DocAnalyzer : Screen("doc_analyzer", "doc_analyzer", "Doc Analyzer", Icons.Default.AutoAwesome)
    data object ResumeBuilder : Screen("resume_builder", "resume_builder", "Resume", Icons.Default.AutoAwesome)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Chat,
    Screen.Tools,
    Screen.History,
    Screen.Profile
)

@Composable
fun AppNavigation() {
    var showSplash by remember { mutableStateOf(true) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var showAuthScreen by remember { mutableStateOf(false) }
    var authInitialTab by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val repository = remember { AstraRepository(context) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (showSplash) {
        SplashScreen(
            onSplashFinished = { showSplash = false }
        )
    } else if (currentUser == null) {
        if (!showAuthScreen) {
            WelcomeScreen(
                onGetStarted = {
                    authInitialTab = 1
                    showAuthScreen = true
                },
                onContinueWithGoogle = {
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        repository = repository,
                        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                        onAuthError = { showAuthScreen = true },
                        scope = scope
                    )
                },
                onLoginClick = {
                    authInitialTab = 0
                    showAuthScreen = true
                }
            )
        } else {
            AuthScreen(
                initialTab = authInitialTab,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    showAuthScreen = false
                }
            )
        }
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            MainScaffold(currentUserId = uid)
        }
    }
}

@Composable
fun MainScaffold(currentUserId: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showBottomBar = bottomNavItems.any { item ->
        currentRoute != null && (currentRoute == item.route || currentRoute.startsWith(item.baseRoute))
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        .testTag("bottom_navigation_bar")
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute != null && (
                            currentRoute == screen.route ||
                            currentRoute.startsWith(screen.baseRoute)
                        )

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                val destinationRoute = when (screen) {
                                    Screen.Chat -> "chat"
                                    Screen.Tools -> "tools?tool=email"
                                    else -> screen.route
                                }

                                navController.navigate(destinationRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label
                                )
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.surface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.baseRoute}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Home Dashboard
                composable(Screen.Home.route) {
                    DashboardScreen(
                        currentUserId = currentUserId,
                        onNavigateToChat = { prompt ->
                            if (!prompt.isNullOrBlank()) {
                                val encoded = URLEncoder.encode(prompt, "UTF-8")
                                navController.navigate("chat?prompt=$encoded")
                            } else {
                                navController.navigate("chat")
                            }
                        },
                        onNavigateToImage = {
                            navController.navigate(Screen.ImageStudio.route)
                        },
                        onNavigateToDoc = {
                            navController.navigate(Screen.DocAnalyzer.route)
                        },
                        onNavigateToScanner = {
                            navController.navigate(Screen.Scanner.route)
                        },
                        onNavigateToWriting = { tool ->
                            navController.navigate("tools?tool=$tool")
                        },
                        onNavigateToResume = {
                            navController.navigate(Screen.ResumeBuilder.route)
                        },
                        onNavigateToProfile = {
                            navController.navigate(Screen.Profile.route)
                        }
                    )
                }

                // 2. Chat Assistant
                composable(
                    route = Screen.Chat.route,
                    arguments = listOf(
                        navArgument("prompt") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("convId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val encodedPrompt = backStackEntry.arguments?.getString("prompt")
                    val initialPrompt = if (!encodedPrompt.isNullOrBlank()) {
                        try {
                            URLDecoder.decode(encodedPrompt, "UTF-8")
                        } catch (e: Exception) {
                            encodedPrompt
                        }
                    } else null

                    ChatScreen(
                        currentUserId = currentUserId,
                        initialPrompt = initialPrompt
                    )
                }

                // 3. Tools
                composable(
                    route = Screen.Tools.route,
                    arguments = listOf(
                        navArgument("tool") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = "email"
                        }
                    )
                ) { backStackEntry ->
                    val tool = backStackEntry.arguments?.getString("tool") ?: "email"
                    WritingToolsScreen(
                        currentUserId = currentUserId,
                        initialTool = tool
                    )
                }

                // 4. History Screen
                composable(Screen.History.route) {
                    HistoryScreen(
                        currentUserId = currentUserId,
                        onSelectConversation = { convId ->
                            navController.navigate("chat?convId=$convId") {
                                popUpTo(Screen.Home.route)
                                launchSingleTop = true
                            }
                        },
                        onNewChatClick = {
                            navController.navigate("chat") {
                                popUpTo(Screen.Home.route)
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // 5. Profile & Settings
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        currentUserId = currentUserId,
                        onSignOut = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                // Sub-destinations
                composable(Screen.ImageStudio.route) {
                    ImageStudioScreen(currentUserId = currentUserId)
                }

                composable(Screen.Scanner.route) {
                    ScannerScreen(currentUserId = currentUserId)
                }

                composable(Screen.DocAnalyzer.route) {
                    DocAnalyzerScreen(currentUserId = currentUserId)
                }

                composable(Screen.ResumeBuilder.route) {
                    ResumeBuilderScreen(currentUserId = currentUserId)
                }
            }
        }
    }
}
