package com.example.collegebustracking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.collegebustracking.model.Route
import com.example.collegebustracking.model.BusStop
import com.google.firebase.database.*

@Composable
fun ManageRoutesScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    var routeName by remember { mutableStateOf("") }
    var startPoint by remember { mutableStateOf("") }
    var endPoint by remember { mutableStateOf("") }

    var routes by remember { mutableStateOf(listOf<Route>()) }
    var editingRouteId by remember { mutableStateOf<String?>(null) }

    // 🔹 Real-time route listener
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
            "Manage Routes",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 Add Route Form
        OutlinedTextField(
            value = routeName,
            onValueChange = { routeName = it },
            label = { Text("Route Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = startPoint,
            onValueChange = { startPoint = it },
            label = { Text("Start Point") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = endPoint,
            onValueChange = { endPoint = it },
            label = { Text("End Point") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {

                if (routeName.isNotBlank()) {

                    val routeId = database.child("routes").push().key!!

                    val route = Route(
                        id = routeId,
                        name = routeName,
                        startPoint = startPoint,
                        endPoint = endPoint
                    )

                    database.child("routes")
                        .child(routeId)
                        .setValue(route)

                    routeName = ""
                    startPoint = ""
                    endPoint = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Route")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 ROUTE LIST (SCROLLABLE)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)   // ⭐ Important for scrolling
        ) {

            items(routes) { route ->

                var editName by remember { mutableStateOf(route.name) }
                var editStart by remember { mutableStateOf(route.startPoint) }
                var editEnd by remember { mutableStateOf(route.endPoint) }

                var stops by remember { mutableStateOf(listOf<BusStop>()) }

                // 🔹 Load stops
                LaunchedEffect(route.id) {

                    database.child("routes")
                        .child(route.id)
                        .child("stops")
                        .addValueEventListener(object : ValueEventListener {

                            override fun onDataChange(snapshot: DataSnapshot) {

                                val stopList = mutableListOf<BusStop>()

                                for (stop in snapshot.children) {

                                    val busStop = stop.getValue(BusStop::class.java)

                                    if (busStop != null) {
                                        stopList.add(
                                            busStop.copy(id = stop.key ?: "")
                                        )
                                    }
                                }

                                stops = stopList
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {

                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {

                        if (editingRouteId == route.id) {

                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Route Name") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editStart,
                                onValueChange = { editStart = it },
                                label = { Text("Start Point") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editEnd,
                                onValueChange = { editEnd = it },
                                label = { Text("End Point") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {

                                    database.child("routes")
                                        .child(route.id)
                                        .updateChildren(
                                            mapOf(
                                                "name" to editName,
                                                "startPoint" to editStart,
                                                "endPoint" to editEnd
                                            )
                                        )

                                    editingRouteId = null
                                }
                            ) {
                                Text("Save")
                            }

                        } else {

                            Text("Route: ${route.name}")
                            Text("From: ${route.startPoint}")
                            Text("To: ${route.endPoint}")

                            Spacer(modifier = Modifier.height(8.dp))

                            Row {

                                Button(
                                    onClick = { editingRouteId = route.id }
                                ) {
                                    Text("Edit")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        database.child("routes")
                                            .child(route.id)
                                            .removeValue()
                                    }
                                ) {
                                    Text("Delete")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 🔹 Stops
                        Text(
                            "Bus Stops:",
                            style = MaterialTheme.typography.titleMedium
                        )

                        stops.forEach { stop ->

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {

                                Text(stop.name)

                                Button(
                                    onClick = {
                                        database.child("routes")
                                            .child(route.id)
                                            .child("stops")
                                            .child(stop.id)
                                            .removeValue()
                                    }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                navController.navigate("add_stop/${route.id}")
                            }
                        ) {
                            Text("Add Stop On Map")
                        }
                    }
                }
            }
        }
    }
}