package com.example.collegebustracking.useri.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.collegebustracking.model.Route
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSelectionScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    var routes by remember { mutableStateOf(listOf<Route>()) }

    // Notifications
    var notifications by remember { mutableStateOf(listOf<Map<String, String>>()) }
    var showNotifications by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Load routes
        database.child("routes")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Route>()
                    for (child in snapshot.children) {
                        val route = child.getValue(Route::class.java)
                        if (route != null) {
                            list.add(route.copy(id = child.key ?: ""))
                        }
                    }
                    routes = list
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Load notifications
        database.child("notifications")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Map<String, String>>()
                    for (child in snapshot.children) {
                        val t = child.child("title").getValue(String::class.java) ?: ""
                        val m = child.child("message").getValue(String::class.java) ?: ""
                        val time = child.child("date").getValue(String::class.java) ?: ""
                        list.add(mapOf("title" to t, "message" to m, "date" to time))
                    }
                    notifications = list.reversed()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Route") },
                actions = {
                    // Notification bell
                    Badge(
                        containerColor = if (notifications.isNotEmpty()) Color.Red else Color.Transparent
                    ) {
                        IconButton(onClick = { showNotifications = !showNotifications }) {
                            Text("\uD83D\uDD14", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    // Logout button
                    IconButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
                        Text("\uD83D\uDEAA", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            // Notification panel
            if (showNotifications) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "\uD83D\uDD14 Notifications",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (notifications.isEmpty()) {
                            Text("No notifications",
                                style = MaterialTheme.typography.bodyMedium)
                        } else {
                            notifications.take(5).forEach { notif ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = notif["title"] ?: "",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = notif["message"] ?: "",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = notif["date"] ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (routes.isEmpty()) {
                Text("No routes available")
            }

            LazyColumn {
                items(routes) { route ->
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        onClick = {
                            if (route.id.isNotEmpty()) {
                                navController.navigate("map/${route.id}")
                            }
                        }
                    ) {
                        Column {
                            Text(route.name)
                            Text(
                                "From: ${route.startPoint} \u2192 ${route.endPoint}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("View on Map")
                        }
                    }
                }
            }
        }
    }
}
