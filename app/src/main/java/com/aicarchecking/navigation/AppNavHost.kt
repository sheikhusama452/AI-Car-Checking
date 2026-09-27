package com.aicarchecking.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aicarchecking.domain.model.CaptureMode
import com.aicarchecking.ui.emergency.EmergencyScreen
import com.aicarchecking.ui.history.HistoryScreen
import com.aicarchecking.ui.home.HomeScreen
import com.aicarchecking.ui.inspection.CaptureScreen
import com.aicarchecking.ui.inspection.InspectionFlowScreen
import com.aicarchecking.ui.inspection.StartInspectionScreen
import com.aicarchecking.ui.inspection.StepScreen
import com.aicarchecking.ui.mechanic.MechanicScreen
import com.aicarchecking.ui.mileage.MileageScreen
import com.aicarchecking.ui.obd.ObdScreen
import com.aicarchecking.ui.paint.PaintCheckScreen
import com.aicarchecking.ui.report.ReportScreen
import com.aicarchecking.ui.settings.AboutScreen
import com.aicarchecking.ui.settings.AiProviderScreen
import com.aicarchecking.ui.settings.PrivacyScreen
import com.aicarchecking.ui.settings.SettingsScreen
import com.aicarchecking.ui.settings.StorageScreen
import com.aicarchecking.ui.vehicle.VehicleDetailScreen
import com.aicarchecking.ui.vehicle.VehicleEditScreen
import com.aicarchecking.ui.vehicle.VehicleListScreen

private data class Tab(val route: String, val navRoute: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.CARS, Routes.CARS, "My Cars", Icons.Filled.DirectionsCar),
    Tab(Routes.INSPECT, "inspect", "Inspect", Icons.Filled.CameraAlt),
    Tab(Routes.MECHANIC, Routes.MECHANIC, "AI Mechanic", Icons.Filled.SupportAgent),
    Tab(Routes.HISTORY, Routes.HISTORY, "History", Icons.Filled.History),
)

@Composable
fun AppNavHost(nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    fun go(route: String) = nav.navigate(route)
    fun back() { nav.popBackStack() }
    fun replace(route: String) = nav.navigate(route) {
        nav.currentBackStackEntry?.destination?.route?.let { current -> popUpTo(current) { inclusive = true } }
    }

    Scaffold(
        // Each screen's own Scaffold/TopAppBar handles system-bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.navRoute) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(::go) }
            composable(Routes.CARS) { VehicleListScreen(::go) }
            composable(
                Routes.INSPECT,
                arguments = listOf(navArgument("step") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                StartInspectionScreen(::go) { inspectionId, step ->
                    nav.navigate(Routes.inspection(inspectionId))
                    if (step != null) nav.navigate(Routes.step(inspectionId, step))
                }
            }
            composable(Routes.MECHANIC) { MechanicScreen() }
            composable(Routes.HISTORY) { HistoryScreen(::go) }

            composable(
                Routes.VEHICLE_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                VehicleEditScreen(onBack = ::back, onSaved = { id ->
                    nav.popBackStack()
                    // New car from Home / My Cars → show it. From Start Inspection or Car detail → just return there.
                    val previous = nav.currentBackStackEntry?.destination?.route
                    if (previous == Routes.HOME || previous == Routes.CARS) nav.navigate(Routes.vehicleDetail(id))
                })
            }
            composable(Routes.VEHICLE_DETAIL, arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })) {
                VehicleDetailScreen(::back, ::go)
            }
            composable(Routes.INSPECTION, arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })) {
                InspectionFlowScreen(::back, ::go)
            }
            composable(
                Routes.STEP,
                arguments = listOf(navArgument("inspectionId") { type = NavType.StringType }, navArgument("step") { type = NavType.StringType }),
            ) {
                StepScreen(::back, ::go) { route -> replace(route) }
            }
            composable(
                Routes.CAMERA,
                arguments = listOf(
                    navArgument("inspectionId") { type = NavType.StringType },
                    navArgument("step") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType },
                ),
            ) { backStack ->
                val mode = runCatching { CaptureMode.valueOf(backStack.arguments?.getString("mode").orEmpty()) }.getOrDefault(CaptureMode.PHOTO)
                CaptureScreen(mode) { back() }
            }
            composable(Routes.PAINT, arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })) {
                PaintCheckScreen(::back, ::go)
            }
            composable(Routes.MILEAGE, arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })) {
                MileageScreen(::back, ::go)
            }
            composable(
                Routes.OBD,
                arguments = listOf(navArgument("inspectionId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { ObdScreen(::back) }
            composable(Routes.REPORT, arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })) {
                ReportScreen(::back)
            }
            composable(Routes.EMERGENCY) { EmergencyScreen(::back) }
            composable(Routes.SETTINGS) { SettingsScreen(::back, ::go) }
            composable(Routes.AI_PROVIDER) { AiProviderScreen(::back) }
            composable(Routes.STORAGE) { StorageScreen(::back) }
            composable(Routes.PRIVACY) { PrivacyScreen(::back) }
            composable(Routes.ABOUT) { AboutScreen(::back) }
        }
    }
}
