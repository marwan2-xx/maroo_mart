package com.example.talabatmart

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.talabatmart.ui.theme.TalabatMartTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status-bar icons, since the header behind them is dark maroon
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        setContent {
            TalabatMartTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigator(
                        auth = auth,
                        database = database,
                        // Only bottom padding: the header draws behind the status bar itself
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
            }
        }
    }
}

// Simple screen state to switch between Login / Signup / Profile
enum class Screen { LOGIN, SIGNUP, PROFILE }

// Allowed account types (must match the database rules)
object UserRole {
    const val BUYER = "buyer"
    const val SELLER = "seller"
}

// Data model for the user's profile
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val role: String = UserRole.BUYER
)

@Composable
fun AppNavigator(auth: FirebaseAuth, database: FirebaseDatabase, modifier: Modifier = Modifier) {
    // Start on Profile if already logged in, otherwise Login
    var currentScreen by remember {
        mutableStateOf(if (auth.currentUser != null) Screen.PROFILE else Screen.LOGIN)
    }

    when (currentScreen) {
        Screen.LOGIN -> LoginScreen(
            auth = auth,
            onLoginSuccess = { currentScreen = Screen.PROFILE },
            onNavigateToSignup = { currentScreen = Screen.SIGNUP },
            modifier = modifier
        )
        Screen.SIGNUP -> SignupScreen(
            auth = auth,
            database = database,
            onSignupSuccess = { currentScreen = Screen.PROFILE },
            onNavigateToLogin = { currentScreen = Screen.LOGIN },
            modifier = modifier
        )
        Screen.PROFILE -> ProfileScreen(
            auth = auth,
            database = database,
            onSignOut = { currentScreen = Screen.LOGIN },
            modifier = modifier
        )
    }
}

@Composable
fun LoginScreen(
    auth: FirebaseAuth,
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        BrandHeader(subtitle = "Welcome back! Log in to continue")

        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FormCard {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                AppTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    icon = Icons.Filled.Email,
                    keyboardType = KeyboardType.Email
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    icon = Icons.Filled.Lock,
                    keyboardType = KeyboardType.Password,
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage.isNotEmpty()) {
                    MessageText(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }

                PrimaryButton(
                    text = if (isLoading) "Logging in..." else "Login",
                    isLoading = isLoading,
                    enabled = !isLoading,
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please fill in all fields"
                            return@PrimaryButton
                        }
                        errorMessage = ""
                        isLoading = true
                        auth.signInWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    onLoginSuccess()
                                } else {
                                    errorMessage = task.exception?.message ?: "Login failed"
                                }
                            }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onNavigateToSignup) {
                Text("Don't have an account? Sign up")
            }
        }
    }
}

@Composable
fun SignupScreen(
    auth: FirebaseAuth,
    database: FirebaseDatabase,
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.BUYER) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val emailInvalid = email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        BrandHeader(subtitle = "Create your account")

        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FormCard {
                Text(
                    text = "I want to join as a",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RoleCard(
                        title = "Buyer",
                        description = "Order products",
                        icon = Icons.Filled.ShoppingCart,
                        selected = selectedRole == UserRole.BUYER,
                        onSelect = { selectedRole = UserRole.BUYER },
                        modifier = Modifier.weight(1f)
                    )
                    RoleCard(
                        title = "Seller",
                        description = "Sell products",
                        icon = Icons.Filled.Home,
                        selected = selectedRole == UserRole.SELLER,
                        onSelect = { selectedRole = UserRole.SELLER },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Full Name",
                    icon = Icons.Filled.Person
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    icon = Icons.Filled.Email,
                    keyboardType = KeyboardType.Email,
                    isError = emailInvalid,
                    supportingText = if (emailInvalid) "Enter a valid email address" else null
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Phone Number",
                    icon = Icons.Filled.Phone,
                    keyboardType = KeyboardType.Phone
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Address",
                    icon = Icons.Filled.Place,
                    singleLine = false
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    icon = Icons.Filled.Lock,
                    keyboardType = KeyboardType.Password,
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm Password",
                    icon = Icons.Filled.Lock,
                    keyboardType = KeyboardType.Password,
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage.isNotEmpty()) {
                    MessageText(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }

                PrimaryButton(
                    text = if (isLoading) "Creating account..." else "Sign Up",
                    isLoading = isLoading,
                    enabled = !isLoading,
                    onClick = {
                        val cleanEmail = email.trim()

                        if (name.isBlank() || phone.isBlank() || address.isBlank()) {
                            errorMessage = "Please fill in your name, phone and address"
                            return@PrimaryButton
                        }
                        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                            errorMessage = "Please enter a valid email address"
                            return@PrimaryButton
                        }
                        if (!Patterns.PHONE.matcher(phone.trim()).matches()) {
                            errorMessage = "Please enter a valid phone number"
                            return@PrimaryButton
                        }
                        if (password.length < 6) {
                            errorMessage = "Password must be at least 6 characters"
                            return@PrimaryButton
                        }
                        if (password != confirmPassword) {
                            errorMessage = "Passwords do not match"
                            return@PrimaryButton
                        }

                        errorMessage = ""
                        isLoading = true
                        auth.createUserWithEmailAndPassword(cleanEmail, password)
                            .addOnCompleteListener { task ->
                                if (!task.isSuccessful) {
                                    isLoading = false
                                    errorMessage = task.exception?.message ?: "Signup failed"
                                    return@addOnCompleteListener
                                }

                                val user = auth.currentUser
                                if (user == null) {
                                    isLoading = false
                                    errorMessage = "Signup failed"
                                    return@addOnCompleteListener
                                }

                                // Save the full profile under users/{uid}
                                val profile = UserProfile(
                                    name = name.trim(),
                                    email = user.email ?: cleanEmail,
                                    phone = phone.trim(),
                                    address = address.trim(),
                                    role = selectedRole
                                )

                                database.getReference("users").child(user.uid)
                                    .setValue(profile)
                                    .addOnCompleteListener { dbTask ->
                                        // Send verification email (result doesn't block the flow)
                                        user.sendEmailVerification()

                                        isLoading = false
                                        if (!dbTask.isSuccessful) {
                                            errorMessage = "Account created, but saving profile failed: " +
                                                    (dbTask.exception?.message ?: "unknown error")
                                        }
                                        onSignupSuccess()
                                    }
                            }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onNavigateToLogin) {
                Text("Already have an account? Login")
            }
        }
    }
}

@Composable
fun ProfileScreen(
    auth: FirebaseAuth,
    database: FirebaseDatabase,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userId = auth.currentUser?.uid ?: ""
    val userRef = remember { database.getReference("users").child(userId) }

    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch existing profile data in real time when the screen loads
    DisposableEffect(userId) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(UserProfile::class.java)
                if (profile != null) {
                    name = profile.name
                    address = profile.address
                    phone = profile.phone
                    role = profile.role
                }
                isLoading = false
            }

            override fun onCancelled(error: DatabaseError) {
                statusMessage = "Failed to load profile: ${error.message}"
                isLoading = false
            }
        }
        userRef.addValueEventListener(listener)

        onDispose {
            userRef.removeEventListener(listener)
        }
    }

    val userEmail = auth.currentUser?.email ?: ""
    val initial = (name.ifBlank { userEmail }).firstOrNull()?.uppercase() ?: "?"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        GradientHeader {
            // Avatar with the user's initial
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = name.ifBlank { "My Profile" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            if (role.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    Text(
                        text = role.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
            } else {
                FormCard {
                    Text(
                        text = "Personal info",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name",
                        icon = Icons.Filled.Person
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    AppTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Address",
                        icon = Icons.Filled.Place,
                        singleLine = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    AppTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Phone Number",
                        icon = Icons.Filled.Phone,
                        keyboardType = KeyboardType.Phone
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (statusMessage.isNotEmpty()) {
                        MessageText(text = statusMessage, color = MaterialTheme.colorScheme.primary)
                    }

                    PrimaryButton(
                        text = "Save Changes",
                        onClick = {
                            // updateChildren only touches these fields, so email and role are preserved
                            val updates = mapOf<String, Any>(
                                "name" to name.trim(),
                                "address" to address.trim(),
                                "phone" to phone.trim()
                            )
                            userRef.updateChildren(updates)
                                .addOnSuccessListener {
                                    statusMessage = "Profile updated successfully"
                                }
                                .addOnFailureListener { e ->
                                    statusMessage = "Update failed: ${e.message}"
                                }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        auth.signOut()
                        onSignOut()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}