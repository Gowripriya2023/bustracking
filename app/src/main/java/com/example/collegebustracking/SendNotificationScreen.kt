package com.example.collegebustracking

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendNotificationScreen(navController: NavController) {

    val context = LocalContext.current

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Load past notifications
    var notifications by remember { mutableStateOf(listOf<Map<String, String>>()) }

    LaunchedEffect(Unit) {
        database.child("notifications")
            .orderByChild("timestamp")
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
            TopAppBar(title = { Text("Send Notification") })
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Notification Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Notification Message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (title.isBlank() || message.isBlank()) {
                        Toast.makeText(context, "Please fill title and message", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSending = true

                    val notifData = mapOf(
                        "title" to title,
                        "message" to message,
                        "timestamp" to ServerValue.TIMESTAMP,
                        "date" to java.text.SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date())
                    )

                    database.child("notifications")
                        .push()
                        .setValue(notifData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Notification sent!", Toast.LENGTH_SHORT).show()
                            title = ""
                            message = ""
                            isSending = false
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            isSending = false
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending
            ) {
                Text(if (isSending) "Sending..." else "Send Notification")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Back")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Sent Notifications", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(8.dp))

            if (notifications.isEmpty()) {
                Text("No notifications sent yet", style = MaterialTheme.typography.bodyMedium)
            }

            LazyColumn {
                items(notifications) { notif ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = notif["title"] ?: "",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = notif["message"] ?: "",
                                style = MaterialTheme.typography.bodyMedium
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