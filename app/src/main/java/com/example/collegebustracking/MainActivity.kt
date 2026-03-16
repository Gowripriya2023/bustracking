package com.example.collegebustracking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.collegebustracking.ui.theme.CollegeBusTrackingTheme
import com.example.collegebustracking.useri.login.LoginScreen
import com.example.collegebustracking.useri.admin.AdminDashboardScreen
import com.example.collegebustracking.useri.home.MapScreen
import com.example.collegebustracking.useri.home.DriverMapScreen
import com.example.collegebustracking.useri.admin.ApproveUsersScreen
import com.example.collegebustracking.useri.login.RegisterScreen
import com.example.collegebustracking.useri.home.RouteSelectionScreen
import com.example.collegebustracking.BusManagementScreen
import com.example.collegebustracking.ManageRoutesScreen

import com.example.collegebustracking.useri.admin.AdminLiveMapScreen
import com.example.collegebustracking.ManageDriversScreen
import com.example.collegebustracking.SendNotificationScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CollegeBusTrackingTheme {

                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "login"
                ) {

                    composable("login") {
                        LoginScreen(navController)
                    }

                    composable("admin_dashboard") {
                        AdminDashboardScreen(navController)
                    }

                    composable("manage_students") {
                        ManageStudentsScreen(navController)
                    }

                    composable("bus_management") {
                        BusManagementScreen(navController)
                    }

                    // ROUTE SELECTION SCREEN (Student)
                    composable("routes") {
                        RouteSelectionScreen(navController)
                    }

                    // MAP SCREEN WITH ROUTE ID
                    composable(
                        route = "map/{routeId}",
                        arguments = listOf(navArgument("routeId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val routeId = backStackEntry.arguments?.getString("routeId") ?: ""
                        MapScreen(navController, routeId)
                    }
                    composable("driver_map") {
                        DriverMapScreen(navController)
                    }

                    composable("approve_users") {
                        ApproveUsersScreen(navController)
                    }

                    composable("register") {
                        RegisterScreen(navController)
                    }

                    composable("manage_routes") {
                        ManageRoutesScreen(navController)
                    }

                    composable("add_stop/{routeId}") { backStackEntry ->
                        val routeId = backStackEntry.arguments?.getString("routeId") ?: ""
                        AddStopOnMapScreen(navController, routeId)
                    }

                    composable("manage_drivers") {
                        ManageDriversScreen(navController)
                    }

                    composable("send_notification") {
                        SendNotificationScreen(navController)
                    }

                    composable("admin_live_map") {
                        AdminLiveMapScreen(navController)
                    }
                }
            }
        }
    }
}