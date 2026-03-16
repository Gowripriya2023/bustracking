package com.example.collegebustracking.useri.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.collegebustracking.model.Route
import com.google.firebase.database.*

@Composable
fun RouteSelectionScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    var routes by remember { mutableStateOf(listOf<Route>()) }

    LaunchedEffect(Unit) {
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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Select Route",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                        println("DEBUG → route.name = ${route.name}")
                        println("DEBUG → route.busId = '${route.busId}'")
                        println("DEBUG → navigating to: map/${route.id}") // ✅ routeId now

                        if (route.id.isNotEmpty()) {
                            navController.navigate("map/${route.id}")  // ✅ use route.id
                        } else {
                            println("ERROR → RouteId is empty")
                        }
                    }
                ) {
                    Text("View on Map")
                }
                    Column {
                        Text(route.name)
                        Text(
                            "From: ${route.startPoint} → ${route.endPoint}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
