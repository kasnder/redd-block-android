package net.kollnig.reddblockandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.kollnig.reddblockandroid.assistant.ScheduleProposal
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.ui.screen.CreateScheduleScreen
import net.kollnig.reddblockandroid.ui.screen.AssistantScreen
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.screen.HomeScreen
import net.kollnig.reddblockandroid.ui.theme.ReDDBlockAndroidTheme
import java.util.UUID

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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // State for friction gate callbacks
    var pendingFrictionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var frictionWordCount by remember { mutableIntStateOf(15) }
    var draftSchedule by remember { mutableStateOf<Schedule?>(null) }

    Scaffold(
        bottomBar = {
            if (currentRoute == "home" || currentRoute == "assistant") {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                        label = { Text("Schedules") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "assistant",
                        onClick = {
                            navController.navigate("assistant") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                        label = { Text("Assistant") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onCreateSchedule = {
                        draftSchedule = null
                        navController.navigate("create_schedule")
                    },
                    onEditSchedule = { schedule ->
                        draftSchedule = null
                        navController.navigate("edit_schedule/${schedule.id}")
                    },
                    onFrictionGateRequired = { schedule: Schedule, onPassed: () -> Unit ->
                        frictionWordCount = schedule.frictionWordCount
                        pendingFrictionAction = onPassed
                        navController.navigate("friction_gate")
                    }
                )
            }

            composable("assistant") {
                AssistantScreen(
                    onReviewProposal = { proposal ->
                        draftSchedule = proposal.toDraftSchedule()
                        navController.navigate("create_schedule")
                    },
                    onReviewAmendment = { amendment ->
                        draftSchedule = amendment.updatedSchedule
                        navController.navigate("edit_schedule/${amendment.scheduleId}")
                    }
                )
            }

            composable("create_schedule") {
                CreateScheduleScreen(
                    scheduleId = null,
                    draftSchedule = draftSchedule,
                    onBackPressed = { navController.popBackStack() },
                    onSaveComplete = {
                        draftSchedule = null
                        navController.popBackStack()
                    },
                    onFrictionGateRequired = { wordCount, onPassed ->
                        frictionWordCount = wordCount
                        pendingFrictionAction = onPassed
                        navController.navigate("friction_gate")
                    }
                )
            }

            composable(
                "edit_schedule/{scheduleId}",
                arguments = listOf(navArgument("scheduleId") { type = NavType.StringType })
            ) { navEntry ->
                CreateScheduleScreen(
                    scheduleId = navEntry.arguments?.getString("scheduleId"),
                    draftSchedule = draftSchedule,
                    onBackPressed = { navController.popBackStack() },
                    onSaveComplete = {
                        draftSchedule = null
                        navController.popBackStack()
                    },
                    onFrictionGateRequired = { wordCount, onPassed ->
                        frictionWordCount = wordCount
                        pendingFrictionAction = onPassed
                        navController.navigate("friction_gate")
                    }
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
        }
    }
}

private fun ScheduleProposal.toDraftSchedule(): Schedule {
    return Schedule(
        id = UUID.randomUUID().toString(),
        name = name,
        isEnabled = timing.type != ScheduleTiming.ScheduleType.MANUAL,
        timing = ScheduleTiming(
            type = timing.type,
            timeHour = timing.timeHour,
            timeMinute = timing.timeMinute,
            endTimeHour = timing.endTimeHour,
            endTimeMinute = timing.endTimeMinute,
            daysOfWeek = timing.daysOfWeek,
            motionCondition = timing.motionCondition,
            wifiCondition = timing.wifiCondition
        ),
        blockedApps = blockedApps,
        blockedWebsites = blockedWebsites,
        frictionWordCount = frictionWordCount,
        autoReenableMinutes = autoReenableMinutes
    )
}
