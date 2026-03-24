package com.example.collegebustracking.useri.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

private val SlateBlue = Color(0xFF6A5ACD)

@Composable
fun LoginScreen(navController: NavController) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Profile completion (for users who have Auth but no database entry)
    var showCompleteProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var profileRole by remember { mutableStateOf("student") }
    var pendingUid by remember { mutableStateOf("") }

    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    // Shared text field colors: white background, SlateBlue typed text
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedTextColor = SlateBlue,
        unfocusedTextColor = SlateBlue,
        focusedBorderColor = SlateBlue,
        unfocusedBorderColor = SlateBlue.copy(alpha = 0.5f),
        focusedLabelColor = SlateBlue,
        unfocusedLabelColor = SlateBlue.copy(alpha = 0.7f),
        cursorColor = SlateBlue
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "BUS WATCH",
            style = MaterialTheme.typography.headlineMedium,
            color = SlateBlue
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Profile Completion Form (shown when Auth exists but no DB entry) ----
        if (showCompleteProfile) {

            Text(
                "Complete Your Profile",
                style = MaterialTheme.typography.titleMedium,
                color = SlateBlue
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Your email is verified but your profile is incomplete. Please fill in the details below.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlue.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors,
                textStyle = TextStyle(color = SlateBlue)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Select Role:", color = SlateBlue)
            Row {
                RadioButton(
                    selected = profileRole == "student",
                    onClick = { profileRole = "student" },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = SlateBlue,
                        unselectedColor = SlateBlue.copy(alpha = 0.5f)
                    )
                )
                Text("Student", color = SlateBlue)

                Spacer(modifier = Modifier.width(16.dp))

                RadioButton(
                    selected = profileRole == "driver",
                    onClick = { profileRole = "driver" },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = SlateBlue,
                        unselectedColor = SlateBlue.copy(alpha = 0.5f)
                    )
                )
                Text("Driver", color = SlateBlue)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (profileName.isBlank()) {
                        message = "Please enter your name"
                        return@Button
                    }

                    isLoading = true
                    val userMap = mapOf(
                        "name" to profileName.trim(),
                        "role" to profileRole,
                        "approved" to false
                    )

                    database.child("users").child(pendingUid).setValue(userMap)
                        .addOnSuccessListener {
                            isLoading = false
                            message = "Profile saved! Waiting for admin approval."
                            showCompleteProfile = false
                            auth.signOut()
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            message = "Error: ${e.message}"
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlateBlue,
                    contentColor = Color.White
                )
            ) {
                Text(if (isLoading) "Saving..." else "Complete Profile")
            }

        } else {
            // ---- Normal Login Form ----

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors,
                textStyle = TextStyle(color = SlateBlue)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors,
                textStyle = TextStyle(color = SlateBlue)
            )

            TextButton(
                onClick = { navController.navigate("register") }
            ) {
                Text("Don't have an account? Register", color = SlateBlue)
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

                            val user = auth.currentUser

                            if (user == null) {
                                isLoading = false
                                message = "User not found"
                                return@addOnSuccessListener
                            }

                            val uid = user.uid

                            database.child("users").child(uid)
                                .addListenerForSingleValueEvent(object : ValueEventListener {

                                    override fun onDataChange(snapshot: DataSnapshot) {

                                        isLoading = false

                                        if (!snapshot.exists()) {
                                            // Auth exists but no database profile — show profile completion
                                            pendingUid = uid
                                            showCompleteProfile = true
                                            message = "Please complete your profile to continue."
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
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlateBlue,
                    contentColor = Color.White
                )
            ) {
                Text(if (isLoading) "Logging in..." else "Login")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (message.isNotEmpty()) {
            Text(
                text = message,
                color = if (message.contains("saved") || message.contains("complete"))
                    Color(0xFF2E7D32)
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}