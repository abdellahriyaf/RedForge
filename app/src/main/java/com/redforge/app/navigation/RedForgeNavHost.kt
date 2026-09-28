package com.redforge.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.redforge.app.data.datastore.ForgeSettings
import com.redforge.app.ui.screens.home.HomeScreen
import com.redforge.app.ui.screens.history.HistoryScreen
import com.redforge.app.ui.screens.onboarding.PrivacyPolicyScreen
import com.redforge.app.ui.screens.onboarding.SplashScreen
import com.redforge.app.ui.screens.onboarding.TutorialScreen
import com.redforge.app.ui.screens.progress.BodyMeasurementScreen
import com.redforge.app.ui.screens.progress.PhotoTrackingScreen
import com.redforge.app.ui.screens.progress.ProgressDashboardScreen
import com.redforge.app.ui.screens.progress.ExerciseProgressDetailScreen
import com.redforge.app.ui.screens.history.HistoryDetailScreen
import com.redforge.app.ui.screens.settings.SettingsScreen
import com.redforge.app.ui.screens.share.ShareResultScreen
import com.redforge.app.ui.screens.splits.ExerciseEditorScreen
import com.redforge.app.ui.screens.splits.ExerciseDetailScreen
import com.redforge.app.ui.screens.splits.ExerciseLibraryScreen
import com.redforge.app.ui.screens.splits.SplitDayEditorScreen
import com.redforge.app.ui.screens.splits.SplitEditorScreen
import com.redforge.app.ui.screens.splits.SplitListScreen
import com.redforge.app.ui.screens.workout.ActiveWorkoutScreen
import com.redforge.app.viewmodel.OnboardingViewModel
import com.redforge.app.viewmodel.ShareScope
import com.redforge.app.viewmodel.redForgeViewModel

private val bottomNavRoutes = setOf(
    NavRoutes.HOME,
    NavRoutes.SETTINGS
)

@Composable
fun RedForgeApp(openWorkoutOnLaunch: Boolean = false) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                RedForgeBottomBar(navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.SPLASH,
            modifier = Modifier.padding(if (currentRoute in bottomNavRoutes) padding else PaddingValues(0.dp))
        ) {
            composable(NavRoutes.SPLASH) {
                val vm: OnboardingViewModel = redForgeViewModel { app -> OnboardingViewModel(app.settingsDataStore) }
                val settings by vm.forgeSettings.collectAsState()
                SplashScreen(onFinished = {
                    val destination = navigateAfterSplash(navController, settings)
                    if (openWorkoutOnLaunch && destination == NavRoutes.HOME) {
                        navController.navigate(NavRoutes.ACTIVE_WORKOUT)
                    }
                })
            }
            composable(NavRoutes.PRIVACY_POLICY) {
                val vm: OnboardingViewModel = redForgeViewModel { app -> OnboardingViewModel(app.settingsDataStore) }
                PrivacyPolicyScreen(onAccept = {
                    vm.acceptPrivacyPolicy()
                    navController.navigate(NavRoutes.ONBOARDING_TUTORIAL) { popUpTo(NavRoutes.SPLASH) { inclusive = true } }
                })
            }
            composable(NavRoutes.ONBOARDING_TUTORIAL) {
                val vm: OnboardingViewModel = redForgeViewModel { app -> OnboardingViewModel(app.settingsDataStore) }
                TutorialScreen(onDone = {
                    vm.completeTutorial()
                    navController.navigate(NavRoutes.HOME) { popUpTo(NavRoutes.SPLASH) { inclusive = true } }
                })
            }
            composable(NavRoutes.HOME) {
                HomeScreen(
                    onStartWorkout = { navController.navigate(NavRoutes.ACTIVE_WORKOUT) },
                    onResumeWorkout = { navController.navigate(NavRoutes.ACTIVE_WORKOUT) },
                    onOpenSplits = { navController.navigate(NavRoutes.SPLIT_LIST) },
                    onOpenProgress = { navController.navigate(NavRoutes.PROGRESS_DASHBOARD) },
                    onOpenHistory = { navController.navigate(NavRoutes.HISTORY) },
                    onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) },
                    onOpenShare = { navController.navigate(NavRoutes.shareResult(ShareScope.DAY.name)) }
                )
            }
            composable(NavRoutes.HISTORY) {
                HistoryScreen(onOpenSession = { id -> navController.navigate(NavRoutes.historyDetail(id)) })
            }
            composable(
                NavRoutes.HISTORY_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                HistoryDetailScreen(sessionId = sessionId, onBack = { navController.popBackStack() })
            }
            composable(NavRoutes.SPLIT_LIST) {
                SplitListScreen(onOpenSplit = { id -> navController.navigate(NavRoutes.splitEditor(id)) })
            }
            composable(
                NavRoutes.SPLIT_EDITOR,
                arguments = listOf(navArgument("splitId") { type = NavType.LongType })
            ) { entry ->
                val splitId = entry.arguments?.getLong("splitId") ?: 0L
                SplitEditorScreen(
                    splitId = splitId,
                    onOpenDay = { dayId -> navController.navigate(NavRoutes.splitDayEditor(dayId)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                NavRoutes.SPLIT_DAY_EDITOR,
                arguments = listOf(navArgument("dayId") { type = NavType.LongType })
            ) { entry ->
                val dayId = entry.arguments?.getLong("dayId") ?: 0L
                val pickedExerciseId by entry.savedStateHandle
                    .getStateFlow<Long?>("picked_exercise_id", null)
                    .collectAsState()
                SplitDayEditorScreen(
                    dayId = dayId,
                    pickedExerciseId = pickedExerciseId,
                    onPickedConsumed = { entry.savedStateHandle["picked_exercise_id"] = null },
                    onPickExercise = { navController.navigate(NavRoutes.exerciseLibrary(true)) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                NavRoutes.EXERCISE_LIBRARY,
                arguments = listOf(navArgument("pickerMode") { type = NavType.BoolType })
            ) { entry ->
                val pickerMode = entry.arguments?.getBoolean("pickerMode") ?: false
                ExerciseLibraryScreen(
                    pickerMode = pickerMode,
                    onPick = { exercise ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("picked_exercise_id", exercise.id)
                        navController.popBackStack()
                    },
                    onEdit = { id -> navController.navigate(NavRoutes.exerciseEditor(id)) },
                    onOpenDetails = { id -> navController.navigate(NavRoutes.exerciseDetail(id)) },
                    onCreateNew = { navController.navigate(NavRoutes.exerciseEditor(0L)) }
                )
            }
            composable(
                NavRoutes.EXERCISE_EDITOR,
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) { entry ->
                val exerciseId = entry.arguments?.getLong("exerciseId") ?: 0L
                ExerciseEditorScreen(
                    exerciseId = exerciseId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                NavRoutes.EXERCISE_DETAIL,
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) { entry ->
                val exerciseId = entry.arguments?.getLong("exerciseId") ?: 0L
                ExerciseDetailScreen(
                    exerciseId = exerciseId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(NavRoutes.exerciseEditor(id)) }
                )
            }
            composable(NavRoutes.ACTIVE_WORKOUT) {
                ActiveWorkoutScreen(
                    onFinished = {
                        val returnedToHome = navController.popBackStack(NavRoutes.HOME, false)
                        if (!returnedToHome) {
                            navController.navigate(NavRoutes.HOME) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(NavRoutes.PROGRESS_DASHBOARD) {
                ProgressDashboardScreen(
                    onOpenPhotos = { navController.navigate(NavRoutes.PHOTO_TRACKING) },
                    onOpenMeasurements = { navController.navigate(NavRoutes.BODY_MEASUREMENTS) },
                    onOpenExercise = { id -> navController.navigate(NavRoutes.exerciseProgress(id)) }
                )
            }
            composable(
                NavRoutes.EXERCISE_PROGRESS,
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) { entry ->
                val exerciseId = entry.arguments?.getLong("exerciseId") ?: 0L
                ExerciseProgressDetailScreen(exerciseId = exerciseId, onBack = { navController.popBackStack() })
            }
            composable(NavRoutes.PHOTO_TRACKING) { PhotoTrackingScreen() }
            composable(NavRoutes.BODY_MEASUREMENTS) { BodyMeasurementScreen() }
            composable(NavRoutes.SETTINGS) { SettingsScreen() }
            composable(
                NavRoutes.SHARE_RESULT,
                arguments = listOf(navArgument("scope") { type = NavType.StringType })
            ) { entry ->
                val scope = ShareScope.valueOf(entry.arguments?.getString("scope") ?: ShareScope.DAY.name)
                ShareResultScreen(initialScope = scope)
            }
        }
    }
}

private fun navigateAfterSplash(navController: NavHostController, settings: ForgeSettings): String {
    val destination = when {
        !settings.privacyPolicyAccepted -> NavRoutes.PRIVACY_POLICY
        !settings.onboardingTutorialSeen -> NavRoutes.ONBOARDING_TUTORIAL
        else -> NavRoutes.HOME
    }
    navController.navigate(destination) { popUpTo(NavRoutes.SPLASH) { inclusive = true } }
    return destination
}

@Composable
private fun RedForgeBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        modifier = Modifier.shadow(12.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 10.dp
    ) {
        NavigationBarItem(
            selected = currentRoute == NavRoutes.HOME,
            onClick = { navController.navigateBottom(NavRoutes.HOME) },
            icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentRoute == NavRoutes.SETTINGS,
            onClick = { navController.navigateBottom(NavRoutes.SETTINGS) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}

private fun NavHostController.navigateBottom(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
