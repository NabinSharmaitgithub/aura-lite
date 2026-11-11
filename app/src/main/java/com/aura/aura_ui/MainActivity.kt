package com.aura.aura_ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.navigation.AuraRoot
import com.aura.aura_ui.presentation.navigation.Routes
import com.aura.aura_ui.ui.theme.AuraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: AuraViewModel by viewModels()

    /** Deep links are a local route hint, never a remote control channel. */
    private var pendingDeepLink by mutableStateOf<String?>(null)

    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.handleConsentResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingDeepLink = routeFor(intent)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val consentRequested by vm.consentRequested.collectAsStateWithLifecycle()

            AuraTheme(darkTheme = settings.darkMode) {
                LaunchedEffect(consentRequested) {
                    if (consentRequested > 0) {
                        runCatching { captureConsent.launch(vm.consentIntent()) }
                    }
                }
                AuraRoot(
                    vm = vm,
                    onboardingComplete = settings.onboardingComplete,
                    deepLinkRoute = pendingDeepLink,
                    onDeepLinkConsumed = { pendingDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = routeFor(intent)
    }

    private fun routeFor(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return when {
            data.scheme == "aura" && data.host == "mcp" && data.path.orEmpty().startsWith("/oauth") ->
                Routes.SETTINGS_TRUSTED_DEVICES
            else -> null
        }
    }
}
