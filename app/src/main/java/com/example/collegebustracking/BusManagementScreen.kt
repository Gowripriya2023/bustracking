package com.example.collegebustracking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.collegebustracking.model.Bus
import com.example.collegebustracking.model.Driver
import com.example.collegebustracking.model.Route
import com.google.firebase.database.*

@Composable
fun BusManagementScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    var busNumber by remember { mutableStateOf("") }
    var selectedRouteId by remember { mutableStateOf("") }
    var selectedDriverId by remember { mutableStateOf("") }

    var routes by remember { mutableStateOf(listOf<Route>()) }
    var drivers by remember { mutableStateOf(listOf<Driver>()) }
    var buses by remember { mutableStateOf(listOf<Bus>()) }

    // 🔥 Load Routes
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

    // 🔥 Load Drivers
    LaunchedEffect(Unit) {
        database.child("users")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val list = mutableListOf<Driver>()

                    for (child in snapshot.children) {

                        val role = child.child("role").getValue(String::class.java)

                        if (role == "driver") {

                            val name = child.child("name").getValue(String::class.java) ?: ""
                            val phone = child.child("phone").getValue(String::class.java) ?: ""

                            val driver = Driver(
                                id = child.key ?: "",   // UID becomes driverId
                                name = name,
                                phone = phone
                            )

                            list.add(driver)
                        }
                    }

                    drivers = list
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // 🔥 Load Buses
    LaunchedEffect(Unit) {
        database.child("buses")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Bus>()
                    for (child in snapshot.children) {
                        try {
                            val bus = child.getValue(Bus::class.java)
                            if (bus != null) {
                                list.add(bus.copy(id = child.key ?: ""))
                            }
                        } catch (e: Exception) {
                            // Skip invalid entries
                        }
                    }
                    buses = list
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Bus Deployment Management", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = busNumber,
            onValueChange = { busNumber = it },
            label = { Text("Bus Number") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ROUTE DROPDOWN
        DropdownSelector(
            label = "Select Route",
            items = routes.map { it.name },
            onItemSelected = { index ->
                selectedRouteId = routes[index].id
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // DRIVER DROPDOWN
        DropdownSelector(
            label = "Select Driver",
            items = drivers.map { it.name },
            onItemSelected = { index ->
                selectedDriverId = drivers[index].id
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (busNumber.isNotBlank()
                    && selectedRouteId.isNotBlank()
                    && selectedDriverId.isNotBlank()
                ) {

                    val busId = database.child("buses").push().key!!

                    val bus = Bus(
                        id = busId,
                        busNumber = busNumber,
                        routeId = selectedRouteId,
                        driverId = selectedDriverId
                    )

                    database.child("buses")
                        .child(busId)
                        .setValue(bus)

                    busNumber = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Bus")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {

            items(buses) { bus ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {

                    Column(modifier = Modifier.padding(12.dp)) {

                        val routeName =
                            routes.find { it.id == bus.routeId }?.name ?: "Not Assigned"

                        val driverName =
                            drivers.find { it.id == bus.driverId }?.name ?: "Not Assigned"

                        Text("Bus: ${bus.busNumber}")
                        Text("Route: $routeName")
                        Text("Driver: $driverName")

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                database.child("buses")
                                    .child(bus.id)
                                    .removeValue()
                            }
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownSelector(
    label: String,
    items: List<String>,
    onItemSelected: (Int) -> Unit
) {

    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }

    Column {

        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Text("▼")
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        selectedText = item
                        expanded = false
                        onItemSelected(index)
                    }
                )
            }
        }
    }
}