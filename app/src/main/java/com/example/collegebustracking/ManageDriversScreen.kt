package com.example.collegebustracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDriversScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference
        .child("users")

    var driversList by remember {
        mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList())
    }

    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val tempList = mutableListOf<Pair<String, Map<String, Any>>>()

                for (userSnapshot in snapshot.children) {

                    val uid = userSnapshot.key ?: continue
                    val userData =
                        userSnapshot.value as? Map<String, Any> ?: continue

                    val role = userData["role"] as? String ?: continue

                    if (role == "driver") {
                        tempList.add(uid to userData)
                    }
                }

                // 🔥 Pending drivers first
                driversList = tempList.sortedBy {
                    val approved = it.second["approved"] as? Boolean ?: false
                    approved
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // 🔍 Filter list based on search
    val filteredList = driversList.filter {
        val name = it.second["name"]?.toString() ?: ""
        name.contains(searchText, ignoreCase = true)
    }

    val totalDrivers = driversList.size
    val pendingCount = driversList.count {
        it.second["approved"] as? Boolean == false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Drivers") }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            // 📊 Stats Section
            Text("Total Drivers: $totalDrivers")
            Text("Pending Approval: $pendingCount")

            Spacer(modifier = Modifier.height(12.dp))

            // 🔍 Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search driver...") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {

                items(filteredList) { (uid, userData) ->

                    val name = userData["name"] as? String ?: "No Name"
                    val approved =
                        userData["approved"] as? Boolean ?: false

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {

                                Text("Name: $name")

                                //  Status Badge
                                Text(
                                    text = if (approved) "Approved" else "Pending",
                                    color = Color.White,
                                    modifier = Modifier
                                        .background(
                                            if (approved) Color(0xFF2E7D32)
                                            else Color(0xFFC62828)
                                        )
                                        .padding(
                                            horizontal = 8.dp,
                                            vertical = 4.dp
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row {

                                if (!approved) {
                                    Button(
                                        onClick = {
                                            database.child(uid)
                                                .child("approved")
                                                .setValue(true)
                                        }
                                    ) {
                                        Text("Approve")
                                    }

                                    Spacer(
                                        modifier = Modifier.width(8.dp)
                                    )
                                }

                                Button(
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    onClick = {
                                        database.child(uid)
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
    }
}