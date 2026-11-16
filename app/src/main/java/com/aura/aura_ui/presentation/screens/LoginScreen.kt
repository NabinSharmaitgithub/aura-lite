package com.aura.aura_ui.presentation.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.remote.AuraSignIn
import com.aura.aura_ui.remote.SignInOutcome
import com.aura.aura_ui.ui.components.AuraTextField
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    vm: AuraViewModel,
    onNavigateBack: () -> Unit,
    onSuccess: () -> Unit = onNavigateBack,
) {
    val mono = LocalMonoScheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by vm.firebaseBusy.collectAsStateWithLifecycle()
    val email by vm.firebaseEmail.collectAsStateWithLifecycle()
    val message by vm.firebaseMessage.collectAsStateWithLifecycle()

    var isSignUp by remember { mutableStateOf(false) }
    var fieldEmail by remember(email) { mutableStateOf(email ?: "") }
    var password by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    val activity = context as? Activity
    val signIn = remember(context) { AuraSignIn(context) }
    val canGoogleSignIn = remember(activity) {
        activity != null && runCatching {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            id != 0 && context.getString(id).isNotBlank()
        }.getOrDefault(false)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(mono.canvas)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        item {
            // Header with back navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = mono.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (email != null) "Cloud Account" else if (isSignUp) "Create Account" else "Sign In",
                    style = MaterialTheme.typography.titleLarge,
                    color = mono.textPrimary,
                )
            }
        }

        item {
            if (!vm.firebaseAvailable) {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Cloud,
                            contentDescription = null,
                            tint = mono.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Firebase Not Configured",
                            style = MaterialTheme.typography.titleMedium,
                            color = mono.textPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This build does not have app/google-services.json configured. AURA operates in 100% offline-first mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                }
                return@item
            }

            if (email != null) {
                // Already signed in state
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = mono.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Signed In",
                                style = MaterialTheme.typography.titleMedium,
                                color = mono.textPrimary,
                            )
                            Text(
                                email ?: "",
                                style = MonoStyleSmall,
                                color = mono.textSecondary,
                            )
                        }
                        Badge("linked", tone = ChipTone.Live)
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Your learned facts, reminders, and user skills automatically synchronize with your Firebase Cloud Firestore and Realtime Database instances.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )

                    Spacer(Modifier.height(16.dp))
                    InkButton(
                        label = "Continue to App →",
                        onClick = onSuccess,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InkButton(
                            label = "Sync Now",
                            onClick = { vm.firebaseSyncNow() },
                            modifier = Modifier.weight(1f),
                            leading = Icons.Outlined.Sync,
                            enabled = !busy,
                        )
                        SecondaryButton(
                            label = "Sign Out",
                            onClick = { vm.firebaseSignOut() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                // Sign in / Sign up form
                SurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InkButton(
                            label = "Sign In",
                            onClick = { isSignUp = false },
                            modifier = Modifier.weight(1f),
                            enabled = isSignUp,
                        )
                        SecondaryButton(
                            label = "New Account",
                            onClick = { isSignUp = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isSignUp,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = if (isSignUp) "Create your AURA cloud account" else "Sign in with your email",
                        style = MaterialTheme.typography.titleMedium,
                        color = mono.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Optional. Keeps your facts and skills synced across devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text("EMAIL", style = MonoStyleSmall, color = mono.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    AuraTextField(
                        value = fieldEmail,
                        onValueChange = { fieldEmail = it },
                        placeholder = "you@example.com",
                        textStyle = MonoStyle,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("PASSWORD", style = MonoStyleSmall, color = mono.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    AuraTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = if (isSignUp) "At least 6 characters" else "••••••••",
                        textStyle = MonoStyle,
                        visualTransformation = PasswordVisualTransformation(),
                    )

                    if (!isSignUp) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = MonoStyleSmall,
                                color = mono.textSecondary,
                                modifier = Modifier
                                    .clickable {
                                        if (fieldEmail.isNotBlank()) {
                                            vm.firebaseSendPasswordReset(fieldEmail)
                                        } else {
                                            vm.showNotice("Enter your email above to receive a reset link.")
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    InkButton(
                        label = if (busy) "Processing…" else if (isSignUp) "Create Account" else "Sign In",
                        onClick = {
                            if (isSignUp) {
                                vm.firebaseCreateAccount(fieldEmail, password)
                            } else {
                                vm.firebaseSignIn(fieldEmail, password)
                            }
                        },
                        enabled = fieldEmail.isNotBlank() && password.length >= 6 && !busy,
                        leading = Icons.Outlined.Check,
                    )

                    if (canGoogleSignIn) {
                        Spacer(Modifier.height(12.dp))
                        GhostButton(
                            label = "Continue with Google",
                            onClick = {
                                val act = activity ?: run {
                                    vm.showNotice("Google sign-in requires an Activity context")
                                    return@GhostButton
                                }
                                scope.launch {
                                    when (val outcome = signIn.signIn(act)) {
                                        is SignInOutcome.Success -> {
                                            vm.refreshFirebaseState()
                                            vm.showNotice("Linked ${outcome.email ?: "Google account"}. Sync is ready.")
                                            onSuccess()
                                        }
                                        SignInOutcome.Cancelled -> Unit
                                        SignInOutcome.NoGoogleAccount ->
                                            vm.showNotice("No Google account found on this device.")
                                        is SignInOutcome.Misconfigured ->
                                            vm.showNotice(outcome.message)
                                        is SignInOutcome.Failed ->
                                            vm.showNotice("Google sign-in failed: ${outcome.message}")
                                    }
                                }
                            },
                        )
                    }
                }
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                SurfaceCard {
                    Text(
                        text = message,
                        style = MonoStyleSmall,
                        color = mono.textPrimary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = mono.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Privacy & Storage Policy",
                        style = MaterialTheme.typography.titleSmall,
                        color = mono.textPrimary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "• AURA operates 100% locally by default without requiring an account.\n" +
                    "• LLM API keys stay encrypted in Android Keystore on this phone.\n" +
                    "• Session logs and file storage use your configured Backblaze B2 bucket (aura-lite-storage).",
                    style = MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }

            Spacer(Modifier.height(16.dp))

            GhostButton(
                label = "Continue Offline",
                onClick = onNavigateBack,
            )
        }
    }
}
