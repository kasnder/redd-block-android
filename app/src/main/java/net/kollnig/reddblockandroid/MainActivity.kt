package net.kollnig.reddblockandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.ui.screen.CreateScheduleScreen
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.screen.HomeScreen
import net.kollnig.reddblockandroid.ui.screen.PermissionsScreen
import net.kollnig.reddblockandroid.ui.screen.SchedulesScreen
import net.kollnig.reddblockandroid.ui.theme.ReDDBlockAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReDDBlockAndroidTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // State for friction gate callbacks
    var pendingFrictionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var frictionWordCount by remember { mutableIntStateOf(15) }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToSchedules = { navController.navigate("schedules") },
                onNavigateToPermissions = { navController.navigate("permissions") }
            )
        }

        composable("schedules") {
            SchedulesScreen(
                onCreateSchedule = { navController.navigate("create_schedule") },
                onEditSchedule = { schedule ->
                    navController.navigate("edit_schedule/${schedule.id}")
                },
                onFrictionGateRequired = { schedule: Schedule, onPassed: () -> Unit ->
                    frictionWordCount = schedule.frictionWordCount
                    pendingFrictionAction = onPassed
                    navController.navigate("friction_gate")
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable("create_schedule") {
            CreateScheduleScreen(
                scheduleId = null,
                onBackPressed = { navController.popBackStack() },
                onSaveComplete = { navController.popBackStack() }
            )
        }

        composable(
            "edit_schedule/{scheduleId}",
            arguments = listOf(navArgument("scheduleId") { type = NavType.StringType })
        ) { backStackEntry ->
            CreateScheduleScreen(
                scheduleId = backStackEntry.arguments?.getString("scheduleId"),
                onBackPressed = { navController.popBackStack() },
                onSaveComplete = { navController.popBackStack() }
            )
        }

        composable("friction_gate") {
            FrictionGateScreen(
                wordCount = frictionWordCount,
                onPassed = {
                    navController.popBackStack()
                    pendingFrictionAction?.invoke()
                    pendingFrictionAction = null
                },
                onBackPressed = {
                    pendingFrictionAction = null
                    navController.popBackStack()
                }
            )
        }

        composable("permissions") {
            PermissionsScreen(
                onBackPressed = { navController.popBackStack() }
            )
        }
    }
}