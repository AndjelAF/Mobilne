package com.example.mapmyst.presentation.ui.profile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mapmyst.R
import com.example.mapmyst.data.model.Cache
import com.example.mapmyst.data.model.User
import com.example.mapmyst.presentation.ui.components.ImagePickerBottomSheet
import com.example.mapmyst.presentation.ui.components.ProfileImageOptionsBottomSheet
import com.example.mapmyst.presentation.viewmodel.CacheUiState
import com.example.mapmyst.presentation.viewmodel.CacheViewModel
import com.example.mapmyst.presentation.viewmodel.ProfileState
import com.example.mapmyst.presentation.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    userViewModel: UserViewModel = hiltViewModel(),
    cacheViewModel: CacheViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLeaderboard: () -> Unit = {},
    onCreateCache: () -> Unit = {},
    onProfileUpdate: ((User) -> Unit)? = null
) {
    val profileState by userViewModel.profileState.collectAsStateWithLifecycle()
    val currentUser by userViewModel.currentUser.collectAsStateWithLifecycle()
    val isLoading by userViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by userViewModel.errorMessage.collectAsStateWithLifecycle()
    val isEditing by userViewModel.isEditing.collectAsStateWithLifecycle()
    val cacheUiState by cacheViewModel.uiState.collectAsStateWithLifecycle()

    var editUsername by remember { mutableStateOf("") }
    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh profil i ucitaj keševe
    LaunchedEffect(key1 = Unit) {
        userViewModel.refreshProfile()
        currentUser?.let { user ->
            cacheViewModel.loadUserCreatedCaches(user.id)
            cacheViewModel.loadUserFoundCaches(user.id)
        }
    }

    // Inicijalizacija edit polja kada se podaci promene
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            editUsername = user.username
            editFirstName = user.firstName
            editLastName = user.lastName
            editPhone = user.phone
        }
    }

    // Handle error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            userViewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Navigiraj na kreiranje cache-a
                    // IPAK KREIRAMO SAMO SA MAPE!!!
                    android.util.Log.d("ProfileScreen", "🎯 FAB Create Cache clicked from Profile!")
                    // onCreateCache() // Dodaj ovaj callback u ProfileScreen parametre
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create Cache",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { paddingValues ->
        val state = profileState
        when (state) {
            is ProfileState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }
            is ProfileState.Success -> {
                ProfileContent(
                    modifier = Modifier.padding(paddingValues),
                    user = state.user,
                    isEditing = isEditing,
                    isLoading = isLoading,
                    editUsername = editUsername,
                    editFirstName = editFirstName,
                    editLastName = editLastName,
                    editPhone = editPhone,
                    onUsernameChange = { editUsername = it },
                    onFirstNameChange = { editFirstName = it },
                    onLastNameChange = { editLastName = it },
                    onPhoneChange = { editPhone = it },
                    onEditClick = { userViewModel.setEditingMode(true) },
                    onSaveClick = {
                        userViewModel.updateUserProfile(
                            username = editUsername,
                            firstName = editFirstName,
                            lastName = editLastName,
                            phone = editPhone
                        ) { updatedUser ->
                            // Propagiramo promenu ka AuthViewModel
                            onProfileUpdate?.invoke(updatedUser)
                        }
                    },
                    onCancelClick = {
                        userViewModel.setEditingMode(false)
                        currentUser?.let { user ->
                            editUsername = user.username
                            editFirstName = user.firstName
                            editLastName = user.lastName
                            editPhone = user.phone
                        }
                    },
                    onNavigateToLeaderboard = onNavigateToLeaderboard,
                    // Callback za profile picture
                    onProfilePictureUpdate = { uri ->
                        userViewModel.updateProfilePicture(uri) { updatedUser ->
                            onProfileUpdate?.invoke(updatedUser)
                        }
                    },
                    onRemoveProfilePicture = {
                        userViewModel.removeProfilePicture { updatedUser ->
                            onProfileUpdate?.invoke(updatedUser)
                        }
                    },

                    cacheUiState = cacheUiState,
                    cacheViewModel = cacheViewModel
                )
            }
            is ProfileState.Error -> {
                ErrorContent(
                    modifier = Modifier.padding(paddingValues),
                    message = state.message,
                    onRetry = { userViewModel.loadUserProfile() }
                )
            }
        }
    }
}


@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading profile...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProfileContent(
    modifier: Modifier = Modifier,
    user: User,
    isEditing: Boolean,
    isLoading: Boolean,
    editUsername: String,
    editFirstName: String,
    editLastName: String,
    editPhone: String,
    onUsernameChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onProfilePictureUpdate: (Uri) -> Unit,
    onRemoveProfilePicture: () -> Unit,
    cacheUiState: CacheUiState,
    cacheViewModel: CacheViewModel
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Profile Header
        ProfileHeader(
            user = user,
            onNavigateToLeaderboard = onNavigateToLeaderboard,
            onProfilePictureUpdate = onProfilePictureUpdate,
            onRemoveProfilePicture = onRemoveProfilePicture
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Information Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Personal Information",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (!isEditing) {
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isEditing) {
                    EditProfileForm(
                        username = editUsername,
                        firstName = editFirstName,
                        lastName = editLastName,
                        phone = editPhone,
                        email = user.email,
                        onUsernameChange = onUsernameChange,
                        onFirstNameChange = onFirstNameChange,
                        onLastNameChange = onLastNameChange,
                        onPhoneChange = onPhoneChange,
                        isLoading = isLoading,
                        onSaveClick = onSaveClick,
                        onCancelClick = onCancelClick
                    )
                } else {
                    ProfileInfoDisplay(user = user)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Statistics Card
        StatisticsCard(user = user)

        Spacer(modifier = Modifier.height(16.dp))

        // Cache History sekcija
        CacheHistorySection(
            createdCaches = cacheUiState.userCreatedCaches,
            foundCaches = cacheUiState.userFoundCaches,
            cacheViewModel = cacheViewModel
        )
    }
}

@Composable
private fun ProfileHeader(
    user: User,
    onNavigateToLeaderboard: () -> Unit,
    onProfilePictureUpdate: (Uri) -> Unit,
    onRemoveProfilePicture: () -> Unit
) {
    var showImagePicker by remember { mutableStateOf(false) }
    var showImageOptions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Picture
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(user.profilePicture ?: "https://via.placeholder.com/120/CCCCCC/FFFFFF?text=User")
                        .crossfade(true)
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .build(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showImageOptions = true },
                    contentScale = ContentScale.Crop
                )

                // Edit button overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { showImagePicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Profile Picture",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User Name
            Text(
                text = "${user.firstName} ${user.lastName}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Score with Leaderboard Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Score: ${user.score}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(onClick = onNavigateToLeaderboard) {
                    Text("View Leaderboard")
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Image Picker Bottom Sheet
    ImagePickerBottomSheet(
        isVisible = showImagePicker,
        onDismiss = { showImagePicker = false },
        onImageSelected = onProfilePictureUpdate
    )

    // Image Options Bottom Sheet (View/Change/Remove)
    if (showImageOptions) {
        ProfileImageOptionsBottomSheet(
            user = user,
            onDismiss = { showImageOptions = false },
            onChangeImage = {
                showImageOptions = false
                showImagePicker = true
            },
            onRemoveImage = {
                onRemoveProfilePicture()
                showImageOptions = false
            }
        )
    }
}

@Composable
private fun EditProfileForm(
    username: String,
    firstName: String,
    lastName: String,
    phone: String,
    email: String,
    onUsernameChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    isLoading: Boolean,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true
        )

        OutlinedTextField(
            value = firstName,
            onValueChange = onFirstNameChange,
            label = { Text("First Name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true
        )

        OutlinedTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            label = { Text("Last Name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true
        )

        OutlinedTextField(
            value = email,
            onValueChange = { /* Email is read-only */ },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            singleLine = true
        )

        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancelClick,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onSaveClick,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoDisplay(user: User) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileInfoItem(
            label = "Username",
            value = "@${user.username}",
            icon = Icons.Default.Person
        )

        ProfileInfoItem(
            label = "First Name",
            value = user.firstName,
            icon = Icons.Default.Person
        )

        ProfileInfoItem(
            label = "Last Name",
            value = user.lastName,
            icon = Icons.Default.Person
        )

        ProfileInfoItem(
            label = "Email",
            value = user.email,
            icon = Icons.Default.Email
        )

        ProfileInfoItem(
            label = "Phone",
            value = user.phone,
            icon = Icons.Default.Phone
        )
    }
}

@Composable
private fun ProfileInfoItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun StatisticsCard(user: User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    title = "Caches Found",
                    value = user.foundCaches.size.toString(),
                    icon = Icons.Default.Search,
                    color = MaterialTheme.colorScheme.primary
                )

                StatisticItem(
                    title = "Caches Created",
                    value = user.createdCaches.size.toString(),
                    icon = Icons.Default.Add,
                    color = MaterialTheme.colorScheme.secondary
                )

                StatisticItem(
                    title = "Total Score",
                    value = user.score.toString(),
                    icon = Icons.Default.Star,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )

        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}


// Cache History

@Composable
private fun CacheHistorySection(
    createdCaches: List<Cache>,
    foundCaches: List<Cache>,
    cacheViewModel: CacheViewModel
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Created", "Found")

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cache History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = {
                            Text(
                                text = "$title (${if (index == 0) createdCaches.size else foundCaches.size})"
                            )
                        },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                when (selectedTab) {
                    0 -> CacheList(
                        caches = createdCaches,
                        cacheViewModel = cacheViewModel,
                        isCreatedByUser = true
                    )
                    1 -> CacheList(
                        caches = foundCaches,
                        cacheViewModel = cacheViewModel,
                        isCreatedByUser = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheList(
    caches: List<Cache>,
    cacheViewModel: CacheViewModel,
    isCreatedByUser: Boolean
) {
    if (caches.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (isCreatedByUser) Icons.Default.Add else Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isCreatedByUser) "No caches created yet" else "No caches found yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(caches) { cache ->
                CacheHistoryItem(
                    cache = cache,
                    cacheViewModel = cacheViewModel,
                    isCreatedByUser = isCreatedByUser
                )
            }
        }
    }
}

@Composable
private fun CacheHistoryItem(
    cache: Cache,
    cacheViewModel: CacheViewModel,
    isCreatedByUser: Boolean
) {
    val isExpired = cache.expires < System.currentTimeMillis()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpired)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cacheViewModel.getCacheIcon(cache.category),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cacheViewModel.getCategoryText(cache.category),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = cache.description.take(80) + if (cache.description.length > 80) "..." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    // Status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isExpired) Icons.Default.Schedule else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isExpired)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isExpired) "Expired" else "Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isExpired)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${cache.value} pts",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Additional info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "D: ${cache.difficulty}/5, T: ${cache.terrain}/5",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isCreatedByUser && !isExpired) {
                    Text(
                        text = "Found by: ${cache.foundByUsers.size} users",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
