package com.example.collegebustracking.useri.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

private val SlateBlue = Color(0xFF6A5ACD)

@Composable
fun RegisterScreen(navController: NavController) {

    // Step: 1 = email+password, 2 = verify email, 3 = complete profile
    var step by remember { mutableStateOf(1) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("student") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isEmailVerified by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

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
            "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            color = SlateBlue
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ==================== STEP 1: Email & Password ====================
        if (step == 1) {

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                textStyle = TextStyle(color = SlateBlue),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 6 characters)") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                textStyle = TextStyle(color = SlateBlue),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        message = "Please enter email and password"
                        return@Button
                    }
                    if (password.trim().length < 6) {
                        message = "Password must be at least 6 characters"
                        return@Button
                    }

                    isLoading = true
                    message = ""

                    // Create the Firebase account
                    auth.createUserWithEmailAndPassword(email.trim(), password.trim())
                        .addOnSuccessListener {
                            val user = auth.currentUser!!
                            // Send verification email
                            user.sendEmailVerification()
                                .addOnSuccessListener {
                                    isLoading = false
                                    message = "Verification email sent to ${email.trim()}! Please check your inbox."
                                    step = 2
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    message = "Failed to send verification email: ${e.message}"
                                    step = 2 // Still go to step 2 so they can resend
                                }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            message = e.message ?: "Registration failed"
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlateBlue,
                    contentColor = Color.White
                )
            ) {
                Text(if (isLoading) "Sending..." else "Send Verification Email")
            }
        }

        // ==================== STEP 2: Verify Email ====================
        if (step == 2) {

            // Verification status badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEmailVerified) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEmailVerified) "✅" else "⏳",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = email.trim(),
                            style = MaterialTheme.typography.titleSmall,
                            color = SlateBlue
                        )
                        Text(
                            text = if (isEmailVerified) "Email Verified!" else "Email Not Verified",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isEmailVerified) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isEmailVerified) {
                Text(
                    "Open your email inbox and click the verification link, then tap the button below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateBlue.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Check verification button
                Button(
                    onClick = {
                        isLoading = true
                        message = ""

                        // Sign in to get fresh user state
                        auth.signInWithEmailAndPassword(email.trim(), password.trim())
                            .addOnSuccessListener {
                                val user = auth.currentUser
                                // Reload to get latest verification status
                                user?.reload()?.addOnSuccessListener {
                                    isLoading = false
                                    if (user.isEmailVerified) {
                                        isEmailVerified = true
                                        message = "Email verified! Now complete your profile below."
                                        step = 3
                                    } else {
                                        isEmailVerified = false
                                        message = "Email not yet verified. Please click the link in your inbox."
                                    }
                                    auth.signOut()
                                }?.addOnFailureListener { e ->
                                    isLoading = false
                                    message = "Error checking status: ${e.message}"
                                    auth.signOut()
                                }
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
                    Text(if (isLoading) "Checking..." else "I've Verified — Check Status")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Resend verification email
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        auth.signInWithEmailAndPassword(email.trim(), password.trim())
                            .addOnSuccessListener {
                                auth.currentUser?.sendEmailVerification()
                                    ?.addOnSuccessListener {
                                        isLoading = false
                                        message = "Verification email resent! Check your inbox."
                                        auth.signOut()
                                    }
                                    ?.addOnFailureListener { e ->
                                        isLoading = false
                                        message = "Failed to resend: ${e.message}"
                                        auth.signOut()
                                    }
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                message = "Error: ${e.message}"
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateBlue)
                ) {
                    Text("📧 Resend Verification Email")
                }
            }
        }

        // ==================== STEP 3: Complete Profile ====================
        if (step == 3) {

            // Verified badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${email.trim()} — Verified",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                textStyle = TextStyle(color = SlateBlue),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Select Role:", color = SlateBlue)

            Row {
                RadioButton(
                    selected = role == "student",
                    onClick = { role = "student" },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = SlateBlue,
                        unselectedColor = SlateBlue.copy(alpha = 0.5f)
                    )
                )
                Text("Student", color = SlateBlue, modifier = Modifier.align(Alignment.CenterVertically))

                Spacer(modifier = Modifier.width(16.dp))

                RadioButton(
                    selected = role == "driver",
                    onClick = { role = "driver" },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = SlateBlue,
                        unselectedColor = SlateBlue.copy(alpha = 0.5f)
                    )
                )
                Text("Driver", color = SlateBlue, modifier = Modifier.align(Alignment.CenterVertically))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        message = "Please enter your name"
                        return@Button
                    }

                    isLoading = true
                    message = ""

                    // Sign in to get the UID
                    auth.signInWithEmailAndPassword(email.trim(), password.trim())
                        .addOnSuccessListener {
                            val uid = auth.currentUser!!.uid

                            val userMap = mapOf(
                                "name" to name.trim(),
                                "role" to role,
                                "approved" to false
                            )

                            database.child("users").child(uid).setValue(userMap)
                                .addOnSuccessListener {
                                    isLoading = false
                                    message = "Account created! Waiting for admin approval."
                                    auth.signOut()

                                    navController.navigate("login") {
                                        popUpTo("register") { inclusive = true }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    message = "Error saving profile: ${e.message}"
                                    auth.signOut()
                                }
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
                Text(if (isLoading) "Creating..." else "Complete Registration")
            }
        }

        // ==================== Common: Back to Login + Messages ====================
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = {
            // Sign out any temporary account if going back
            auth.signOut()
            navController.navigate("login")
        }) {
            Text("Already have an account? Login", color = SlateBlue)
        }

        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                color = if (message.contains("Verified") || message.contains("sent") || message.contains("created"))
                    Color(0xFF2E7D32)
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}