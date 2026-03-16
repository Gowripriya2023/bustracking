package com.example.collegebustracking.useri.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun AdminDashboardScreen(navController: NavController) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Admin Dashboard",
            style = MaterialTheme.typography.headlineMedium
        )

        Button(
            onClick = { navController.navigate("manage_students") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Students")
        }


        Button(
            onClick = { navController.navigate("approve_users") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Approve Users")
        }


        Button(
            onClick = {
                navController.navigate("manage_routes")
            },modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Routes")
        }

        Button(
            onClick = { navController.navigate("bus_management") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bus Deployment Management")
        }

        Button(
            onClick = { navController.navigate("admin_live_map") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Live Bus")
        }

        Button(
            onClick = { navController.navigate("send_notification") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Notification")
        }

        Button(
            onClick = {
                navController.navigate("login") {
                    popUpTo("admin_dashboard") { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }

    }
}