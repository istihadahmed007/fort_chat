package com.fort.messenger.ui.screens.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.fort.messenger.ui.components.iceBlueLiquidBackground
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.ui.components.SovereignCard
import com.fort.messenger.ui.theme.EmeraldVerified
import com.fort.messenger.ui.theme.IceBlueBorder
import com.fort.messenger.ui.theme.IceBlueTint
import com.fort.messenger.ui.theme.RoseDestructive
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.viewmodel.FortMainViewModel

enum class AuthTab(val title: String) {
    SIGN_IN("Sign In"),
    REGISTER("Create Identity"),
    PHONE_OTP("Phone OTP")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: FortMainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val authScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AuthTab.SIGN_IN) }

    // Form inputs
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var recoveryEmail by remember { mutableStateOf("") }
    var showSetupGuideDialog by remember { mutableStateOf(false) }
    var recoveryBackupPassphrase by remember { mutableStateOf("") }
    var recoveryBackupCiphertext by remember { mutableStateOf("") }
    var showResetKeyConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isKeyRecoveryRequired) {
        if (!uiState.isKeyRecoveryRequired) {
            recoveryBackupPassphrase = ""
            recoveryBackupCiphertext = ""
            showResetKeyConfirmation = false
        }
    }

    val scrollState = rememberScrollState()

    val isDark = com.fort.messenger.ui.components.isAppDarkTheme()

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier
            .fillMaxSize()
            .iceBlueLiquidBackground(isDark)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Sovereign Brand Logo
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.fort.messenger.R.drawable.ic_fort_logo),
                contentDescription = "Fort Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "FORT",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Sovereign Identity & Private Messaging",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RoyalBluePrimary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                AuthTab.values().forEach { tab ->
                    val isSelected = tab == selectedTab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error alert banner if any
            if (uiState.authErrorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEE2E2))
                        .border(1.dp, RoseDestructive, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = RoseDestructive,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.authErrorMessage!!,
                            color = Color(0xFF991B1B),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Tab Content
            when (selectedTab) {
                AuthTab.SIGN_IN -> {
                    SovereignCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = "AUTHENTICATE SOVEREIGN ENCLAVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalBluePrimary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Email, contentDescription = null, tint = RoyalBluePrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = RoyalBluePrimary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                },
                                singleLine = true,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Forgot password / Recover account",
                                    fontSize = 11.sp,
                                    color = RoyalBluePrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { showForgotPasswordDialog = true }
                                        .padding(vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.login(email, password) },
                                enabled = !uiState.isAuthLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                            ) {
                                if (uiState.isAuthLoading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                AuthTab.REGISTER -> {
                    SovereignCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = "INITIALIZE SOVEREIGN IDENTITY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalBluePrimary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Display Name") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Person, contentDescription = null, tint = RoyalBluePrimary)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Email, contentDescription = null, tint = RoyalBluePrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Master Password (min 8 chars)") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = RoyalBluePrimary)
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.register(email, password, displayName) },
                                enabled = !uiState.isAuthLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                            ) {
                                if (uiState.isAuthLoading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Create Sovereign Identity", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                AuthTab.PHONE_OTP -> {
                    SovereignCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = "SECURE PHONE OTP AUTHENTICATION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalBluePrimary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Phone Number (+1..., +880...)") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Phone, contentDescription = null, tint = RoyalBluePrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    val activity = context.findActivity()
                                    if (activity == null) {
                                        viewModel.showAuthError("Phone verification needs an active app screen. Reopen sign-in and try again.")
                                    } else {
                                        viewModel.sendPhoneOtp(phoneNumber, activity)
                                    }
                                },
                                enabled = !uiState.isAuthLoading && phoneNumber.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Send Verification OTP", fontSize = 12.sp)
                            }

                            if (uiState.authVerificationId != null) {
                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { otpCode = it },
                                    label = { Text("6-Digit OTP Code") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Pin, contentDescription = null, tint = RoyalBluePrimary)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { viewModel.verifyPhoneOtp(otpCode, displayName.ifBlank { "Phone User" }) },
                                    enabled = !uiState.isAuthLoading && otpCode.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVerified)
                                ) {
                                    Text("Verify & Sign In", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Federated / Third-Party Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                Text(
                    text = "  OR CONNECT WITH  ",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Sign In Button
            OutlinedButton(
                onClick = {
                    val activity = context.findActivity()
                    if (activity == null) {
                        viewModel.showAuthError("Google sign-in needs an active app screen. Reopen sign-in and try again.")
                    } else {
                        val clientIdResource = activity.resources.getIdentifier(
                            "default_web_client_id",
                            "string",
                            activity.packageName
                        )
                        val serverClientId = if (clientIdResource != 0) {
                            activity.getString(clientIdResource)
                        } else {
                            "259638681713-nfiq3nu83pet376kd1q7hu4rav5lg64s.apps.googleusercontent.com"
                        }
                        authScope.launch {
                            try {
                                val option = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(serverClientId)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(option)
                                        .build()
                                    val result = CredentialManager.create(activity).getCredential(activity, request)
                                    val credential = result.credential as? CustomCredential
                                        ?: throw IllegalStateException("Google did not return an ID token credential.")
                                    if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        throw IllegalStateException("Google returned an unsupported credential.")
                                    }
                                    val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                                    viewModel.loginWithGoogle(googleId.idToken, googleId.displayName.orEmpty())
                                } catch (error: GetCredentialCancellationException) {
                                    // User explicitly dismissed Google picker; do not show error banner
                                    android.util.Log.d("FortAuth", "User dismissed Google credential selector.")
                                } catch (error: GetCredentialException) {
                                    android.util.Log.e("FortAuth", "CredentialManager failed: ${error.type} - ${error.message}", error)
                                    viewModel.showAuthError(error.message ?: "Google sign-in was cancelled or unavailable.")
                                } catch (error: Exception) {
                                    android.util.Log.e("FortAuth", "Google sign-in failed", error)
                                    viewModel.showAuthError(error.message ?: "Google sign-in failed.")
                                }
                            }
                        }
                    },
                enabled = !uiState.isAuthLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = "Google",
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Backend Configuration Info Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSetupGuideDialog = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = RoyalBluePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Backend Setup & Security Architecture Guide",
                    fontSize = 11.sp,
                    color = RoyalBluePrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Existing remote identity recovery after signing in on a new device
    if (uiState.isKeyRecoveryRequired) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissIdentityKeyRecovery() },
            title = { Text("Restore your Fort identity", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "This account already has identity keys. Restore the encrypted backup to keep the same identity on this device.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Without a backup, you can reset the keys. Older encrypted messages will no longer be decryptable.",
                        fontSize = 12.sp,
                        color = RoseDestructive
                    )
                    OutlinedTextField(
                        value = recoveryBackupPassphrase,
                        onValueChange = { recoveryBackupPassphrase = it },
                        label = { Text("Backup passphrase (12+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recoveryBackupCiphertext,
                        onValueChange = { recoveryBackupCiphertext = it },
                        label = { Text("Encrypted identity backup") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.identityBackupError != null) {
                        Text(
                            uiState.identityBackupError!!,
                            color = RoseDestructive,
                            fontSize = 12.sp
                        )
                    }
                    if (uiState.isAuthLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreIdentityKeyBackup(recoveryBackupPassphrase, recoveryBackupCiphertext)
                    },
                    enabled = !uiState.isAuthLoading
                        && recoveryBackupPassphrase.length >= 12
                        && recoveryBackupCiphertext.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Restore backup")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { viewModel.dismissIdentityKeyRecovery() },
                        enabled = !uiState.isAuthLoading
                    ) { Text("Cancel") }
                    TextButton(
                        onClick = { showResetKeyConfirmation = true },
                        enabled = !uiState.isAuthLoading
                    ) { Text("Reset keys", color = RoseDestructive) }
                }
            }
        )
    }

    if (showResetKeyConfirmation && uiState.isKeyRecoveryRequired) {
        AlertDialog(
            onDismissRequest = { showResetKeyConfirmation = false },
            title = { Text("Reset identity keys?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This replaces the keys registered to your account. You will lose access to older messages encrypted to the previous keys. Continue only if you cannot restore a backup.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetKeyConfirmation = false
                        viewModel.confirmIdentityKeyReset()
                    },
                    enabled = !uiState.isAuthLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDestructive)
                ) { Text("Reset and continue") }
            },
            dismissButton = {
                TextButton(onClick = { showResetKeyConfirmation = false }) { Text("Keep current keys") }
            }
        )
    }

    // Password Reset Modal
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text("Account Recovery", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column {
                    Text(
                        text = "Enter your registered email address to dispatch password recovery instructions.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = { recoveryEmail = it },
                        label = { Text("Account Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendPasswordReset(recoveryEmail)
                        showForgotPasswordDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Send Recovery Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Backend Setup Guide Modal
    if (showSetupGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSetupGuideDialog = false },
            title = {
                Text("Fort Production Backend Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "FORT enforces authentic server authorization and zero-knowledge ciphertext relay.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. Firebase Configuration:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Place your live google-services.json in the Fort/app/ directory.\nEnable Cloud Firestore & Firebase Auth in your Google Cloud / Firebase console.",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "2. Security Rules Enforcement:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Deploy firestore.rules using:\nfirebase deploy --only firestore:rules\nThis enforces atomic single-use passes, room admin controls, and audience-restricted mood decays.",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSetupGuideDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Got It")
                }
            }
        )
    }
}


private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
