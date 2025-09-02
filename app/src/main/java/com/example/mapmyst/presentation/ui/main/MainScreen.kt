package com.example.mapmyst.presentation.ui.main

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mapmyst.presentation.ui.profile.ProfileScreen
import com.example.mapmyst.presentation.viewmodel.AuthViewModel
import com.example.mapmyst.presentation.viewmodel.UserViewModel

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem(
        route = "home_tab",
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object Profile : BottomNavItem(
        route = "profile_tab",
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onCreateCache: () -> Unit,
    authViewModel: AuthViewModel,
    onNavigateToLeaderboard: () -> Unit = {},
    userViewModel: UserViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(BottomNavItem.Home.route) }

    val TAG = "MainScreen"

    val authState by authViewModel.authState.collectAsState()
    val currentAuthUser by authViewModel.currentUser.collectAsState()
    val currentUserViewModelUser by userViewModel.currentUser.collectAsState()

    LaunchedEffect(currentAuthUser) {
        currentAuthUser?.let { user ->
            Log.d(TAG, "🔄 AuthViewModel changed: ${user.username}")
            if (currentUserViewModelUser?.id != user.id || currentUserViewModelUser != user) {
                userViewModel.updateUserFromExternal(user)
            } else {
                Log.d(TAG, "ℹ️ No sync needed AuthVM -> UserVM")
            }
        }
    }

    LaunchedEffect(currentUserViewModelUser) {
        currentUserViewModelUser?.let { user ->
            if (currentAuthUser?.id == user.id && currentAuthUser != user) {
                Log.d(TAG, "🔄 Syncing UserVM -> AuthVM: ${user.username}")
                authViewModel.updateCurrentUser(user)
            } else if (currentAuthUser?.id == user.id) {
                Log.d(TAG, "ℹ️ No sync needed UserVM -> AuthVM")
            }
        }
    }

    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Profile
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item.route,
                        onClick = { selectedTab = item.route },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == item.route) {
                                    item.selectedIcon
                                } else {
                                    item.unselectedIcon
                                },
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },

    ) { paddingValues ->
        when (selectedTab) {
            BottomNavItem.Home.route -> {
                HomeScreen(
                    onLogout = onLogout,
                    authViewModel = authViewModel,
                    onCreateCache = onCreateCache,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            BottomNavItem.Profile.route -> {
                ProfileScreen(
                    userViewModel = userViewModel,
                    onCreateCache = onCreateCache,
                    onNavigateToLeaderboard = onNavigateToLeaderboard,
                    onNavigateBack = {
                        selectedTab = BottomNavItem.Home.route
                    },
                    onNavigateToSettings = {
                        // TODO: Implementirati navigaciju do Settings screen-a
                    },
                    onProfileUpdate = { updatedUser ->
                        Log.d(TAG, "📤 Profile updated callback: ${updatedUser.username}")
                        authViewModel.updateCurrentUser(updatedUser)
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
