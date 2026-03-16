package com.example.collegebustracking.useri.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.*

data class User(
    val uid: String = "",
    val name: String = "",
    val role: String = "",
    val approved: Boolean = false
)

@Composable
fun ApproveUsersScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    var pendingUsers by remember { mutableStateOf(listOf<User>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        database.child("users")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    val list = mutableListOf<User>()
                    for (child in snapshot.children) {

                        val name = child.child("name").getValue(String::class.java) ?: ""
                        val role = child.child("role").getValue(String::class.java) ?: ""
                        val approved = child.child("approved").getValue(Boolean::class.java) ?: false

                        if (!approved) {
                            list.add(
                                User(
                                    uid = child.key ?: "",
                                    name = name,
                                    role = role,
                                    approved = approved
                                )
                            )
                        }
                    }

                    pendingUsers = list
                    isLoading = false
                }

                override fun onCancelled(error: DatabaseError) {
                    isLoading = false
                }
            })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Pending User Approvals",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            pendingUsers.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Pending Users 🎉")
                }
            }

            else -> {
                LazyColumn {
                    items(pendingUsers) { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {

                                Text("Name: ${user.name}")
                                Text("Role: ${user.role}")

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        database.child("users")
                                            .child(user.uid)
                                            .child("approved")
                                            .setValue(true)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Approve")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}