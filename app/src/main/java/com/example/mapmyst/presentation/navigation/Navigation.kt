package com.example.mapmyst.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mapmyst.presentation.ui.auth.AuthScreen
import com.example.mapmyst.presentation.ui.auth.LoginScreen
import com.example.mapmyst.presentation.ui.auth.RegisterScreen
import com.example.mapmyst.presentation.ui.cache.CacheCreationScreen
import com.example.mapmyst.presentation.ui.leaderboard.LeaderboardScreen
import com.example.mapmyst.presentation.ui.main.MainScreen
import com.example.mapmyst.presentation.viewmodel.AuthState
import com.example.mapmyst.presentation.viewmodel.AuthViewModel


object NavigationRoutes {
    const val AUTH = "auth"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val CACHE_CREATION = "cache_creation"
    const val LEADERBOARD = "leaderboard"
}

@Composable
fun MapMystNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // Observe auth state
    val authState by authViewModel.authState.collectAsState()

    // Navigacija na osnovu auth state
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                //  Proveravamo da li smo već na authenticated screen
                val currentRoute = navController.currentDestination?.route
                val authenticatedRoutes = listOf(
                    NavigationRoutes.MAIN,
                    NavigationRoutes.CACHE_CREATION,
                    NavigationRoutes.LEADERBOARD // NOVO
                )

                if (currentRoute !in authenticatedRoutes) {
                    navController.navigate(NavigationRoutes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                if (navController.currentDestination?.route != NavigationRoutes.AUTH) {
                    navController.navigate(NavigationRoutes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {
                // Initial ili Error state - ostanemo na trenutnoj destinaciji
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavigationRoutes.AUTH,
        modifier = modifier
    ) {
        // Auth Screen
        composable(NavigationRoutes.AUTH) {
            AuthScreen(
                onNavigateToLogin = {
                    navController.navigate(NavigationRoutes.LOGIN)
                },
                onNavigateToRegister = {
                    navController.navigate(NavigationRoutes.REGISTER)
                }
            )
        }

        // Login Screen
        composable(NavigationRoutes.LOGIN) {
            LoginScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRegister = {
                    navController.navigate(NavigationRoutes.REGISTER) {
                        popUpTo(NavigationRoutes.AUTH)
                    }
                },
                onLoginSuccess = {
                    // LaunchedEffect će automatski navigirati kada se authState promeni
                },
                authViewModel = authViewModel
            )
        }

        // Reg Screen
        composable(NavigationRoutes.REGISTER) {
            RegisterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(NavigationRoutes.LOGIN) {
                        popUpTo(NavigationRoutes.AUTH)
                    }
                },
                onRegisterSuccess = {
                    // LaunchedEffect će automatski navigirati kada se authState promeni
                },
                authViewModel = authViewModel
            )
        }

        // Main Screen
        composable(NavigationRoutes.MAIN) {
            MainScreen(
                onLogout = {
                    authViewModel.signOut()
                    // LaunchedEffect će automatski navigirati kada se authState promeni
                },
                onCreateCache = {
                    navController.navigate(NavigationRoutes.CACHE_CREATION)
                },
                onNavigateToLeaderboard = {
                    navController.navigate(NavigationRoutes.LEADERBOARD)
                },
                authViewModel = authViewModel
            )
        }

        // Cache Creation Screen
        composable(NavigationRoutes.CACHE_CREATION) {
            CacheCreationScreen(
                onNavigateBack = {
                    navController.popBackStack(
                        route = NavigationRoutes.MAIN,
                        inclusive = false
                    )
                },
                authViewModel = authViewModel
            )
        }

        //  Leaderboard Screen
        composable(NavigationRoutes.LEADERBOARD) {
            val currentUser = (authState as? AuthState.Authenticated)?.user

            LeaderboardScreen(
                currentUser = currentUser,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}