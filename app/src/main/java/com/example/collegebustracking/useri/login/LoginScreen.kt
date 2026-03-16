package com.example.collegebustracking.useri.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@Composable
fun LoginScreen(navController: NavController) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "College Bus Tracking",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        TextButton(
            onClick = { navController.navigate("register") }
        ) {
            Text("Don't have an account? Register")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {

                if (email.isBlank() || password.isBlank()) {
                    message = "Please enter email and password"
                    return@Button
                }

                isLoading = true
                message = ""

                auth.signInWithEmailAndPassword(email.trim(), password.trim())
                    .addOnSuccessListener {

                        val uid = auth.currentUser?.uid

                        if (uid == null) {
                            isLoading = false
                            message = "User not found"
                            return@addOnSuccessListener
                        }

                        database.child("users").child(uid)
                            .addListenerForSingleValueEvent(object : ValueEventListener {

                                override fun onDataChange(snapshot: DataSnapshot) {

                                    isLoading = false

                                    if (!snapshot.exists()) {
                                        message = "User data not found in database"
                                        return
                                    }

                                    val role = snapshot.child("role")
                                        .getValue(String::class.java)

                                    val approved = snapshot.child("approved")
                                        .getValue(Boolean::class.java) ?: false

                                    if (role == null) {
                                        message = "Role not found in database"
                                        return
                                    }

                                    if (!approved) {
                                        message = "Waiting for admin approval"
                                        return
                                    }

                                    when (role) {

                                        "admin" -> {
                                            navController.navigate("admin_dashboard") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }

                                        "driver" -> {
                                            navController.navigate("driver_map") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }

                                        "student" -> {
                                            navController.navigate("routes") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }

                                        else -> {
                                            message = "Invalid role: $role"
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    isLoading = false
                                    message = error.message
                                }
                            })
                    }
                    .addOnFailureListener {
                        isLoading = false
                        message = it.message ?: "Login Failed"
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Logging in..." else "Login")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (message.isNotEmpty()) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}