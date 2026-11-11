package com.aura.aura_ui.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.onboarding.OnboardingScreen
import com.aura.aura_ui.presentation.screens.ActivityScreen
import com.aura.aura_ui.presentation.screens.AgentHubScreen
import com.aura.aura_ui.presentation.screens.AuraHomeScreen
import com.aura.aura_ui.presentation.screens.CompanionScreen
import com.aura.aura_ui.presentation.screens.EvalSuiteScreen
import com.aura.aura_ui.presentation.screens.HooksScreen
import com.aura.aura_ui.presentation.screens.LicensesScreen
import com.aura.aura_ui.presentation.screens.MemoryScreen
import com.aura.aura_ui.presentation.screens.MonoSettingsScreen
import com.aura.aura_ui.presentation.screens.PermissionsHealthScreen
import com.aura.aura_ui.presentation.screens.B2Screen
import com.aura.aura_ui.presentation.screens.FirebaseScreen
import com.aura.aura_ui.presentation.screens.PrivacyPolicyScreen
import com.aura.aura_ui.presentation.screens.ProviderSettingsScreen
import com.aura.aura_ui.presentation.screens.RestrictedAppsScreen
import com.aura.aura_ui.presentation.screens.RetentionScreen
import com.aura.aura_ui.presentation.screens.SessionDetailScreen
import com.aura.aura_ui.presentation.screens.SkillsScreen
import com.aura.aura_ui.presentation.screens.TavilyKeyScreen
import com.aura.aura_ui.presentation.screens.TermsScreen
import com.aura.aura_ui.presentation.screens.ToolsScreen
import com.aura.aura_ui.presentation.screens.TrustedDevicesScreen
import com.aura.aura_ui.presentation.screens.VoiceSettingsScreen
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyleSmall

@Composable
fun AuraRoot(
    vm: AuraViewModel,
    onboardingComplete: Boolean,
    deepLinkRoute: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    if (!onboardingComplete) {
        OnboardingScreen(vm) {
            // The settings revision flips and the root recomposes into the shell.
        }
    } else {
        AuraAppShell(vm = vm, deepLinkRoute = deepLinkRoute, onDeepLinkConsumed = onDeepLinkConsumed)
    }
}

@Composable
fun AuraAppShell(
    vm: AuraViewModel,
    deepLinkRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val mono = LocalMonoScheme.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val topLevel = route == null || route in Routes.topLevel

    LaunchedEffect(deepLinkRoute) {
        if (!deepLinkRoute.isNullOrBlank()) {
            navController.navigate(deepLinkRoute) { launchSingleTop = true }
            onDeepLinkConsumed()
        }
    }

    val navigate: (String) -> Unit = { target ->
        if (target in Routes.topLevel) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(target) { launchSingleTop = true }
        }
    }

    val openSession: (String) -> Unit = { id -> navigate(Routes.session(id)) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = mono.canvas,
        contentColor = mono.textPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            CapsuleBar(
                route = route,
                onNavigate = navigate,
                onBack = {
                    if (!navController.popBackStack()) {
                        navigate(Routes.HOME)
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                AuraHomeScreen(vm, onNavigate = navigate, onOpenSession = openSession)
            }
            composable(Routes.AGENT) {
                AgentHubScreen(vm, onNavigate = navigate)
            }
            composable(Routes.ACTIVITY) {
                ActivityScreen(vm, onOpenSession = openSession, onNavigate = navigate)
            }
            composable(Routes.SETTINGS) {
                MonoSettingsScreen(vm, onNavigate = navigate)
            }

            composable(Routes.AGENT_BRAIN) {
                ProviderSettingsScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.AGENT_TOOLS) {
                ToolsScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.AGENT_HOOKS) {
                HooksScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.AGENT_SKILLS) {
                SkillsScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.AGENT_MEMORY) {
                MemoryScreen(vm, onNavigate = navigate)
            }
            composable(Routes.AGENT_COMPANION) {
                CompanionScreen(vm)
            }
            composable(Routes.AGENT_VOICE) {
                VoiceSettingsScreen(vm)
            }

            composable(Routes.SETTINGS_B2) {
                B2Screen(vm)
            }
            composable(Routes.SETTINGS_FIREBASE) {
                FirebaseScreen(vm)
            }
            composable(Routes.SETTINGS_TAVILY) {
                TavilyKeyScreen(vm)
            }
            composable(Routes.SETTINGS_TRUSTED_DEVICES) {
                TrustedDevicesScreen(vm)
            }
            composable(Routes.SETTINGS_RESTRICTED_APPS) {
                RestrictedAppsScreen(vm)
            }
            composable(Routes.SETTINGS_PERMISSIONS) {
                PermissionsHealthScreen(vm)
            }
            composable(Routes.SETTINGS_PRIVACY) {
                PrivacyPolicyScreen(vm) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
            }
            composable(Routes.SETTINGS_TERMS) {
                TermsScreen()
            }
            composable(Routes.SETTINGS_LICENSES) {
                LicensesScreen()
            }

            composable(Routes.EVAL_SUITE) {
                EvalSuiteScreen(vm)
            }
            composable(Routes.ACTIVITY_RETENTION) {
                RetentionScreen(vm)
            }
            composable(
                route = Routes.ACTIVITY_SESSION,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                SessionDetailScreen(vm, sessionId)
            }
        }
    }
}

@Composable
private fun CapsuleBar(
    route: String?,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val mono = LocalMonoScheme.current
    val topLevel = route == null || route in Routes.topLevel

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (topLevel) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(CircleShape)
                    .background(mono.ink)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsuleTab(Icons.Outlined.Home, "Home", route == Routes.HOME || route == null) { onNavigate(Routes.HOME) }
                CapsuleTab(Icons.Outlined.Psychology, "Agent", route == Routes.AGENT) { onNavigate(Routes.AGENT) }
                CapsuleTab(Icons.Outlined.History, "Activity", route == Routes.ACTIVITY) { onNavigate(Routes.ACTIVITY) }
                CapsuleTab(Icons.Outlined.Settings, "Settings", route == Routes.SETTINGS) { onNavigate(Routes.SETTINGS) }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(CircleShape)
                    .background(mono.ink)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = mono.textOnInk, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    Routes.titleFor(route),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = mono.textOnInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    route?.substringBefore('/')?.uppercase().orEmpty(),
                    style = MonoStyleSmall,
                    color = mono.textOnInkDim,
                )
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun CapsuleTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val mono = LocalMonoScheme.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) mono.inkPressed else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) mono.textOnInk else mono.textOnInkDim,
            modifier = Modifier.size(22.dp),
        )
    }
}
