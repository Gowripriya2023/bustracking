package com.example.collegebustracking.useri.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("student") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

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

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = TextStyle(color = SlateBlue)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = TextStyle(color = SlateBlue)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = TextStyle(color = SlateBlue)
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
            Text("Student", color = SlateBlue)

            Spacer(modifier = Modifier.width(16.dp))

            RadioButton(
                selected = role == "driver",
                onClick = { role = "driver" },
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

                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                    message = "Fill all fields"
                    return@Button
                }

                isLoading = true

                auth.createUserWithEmailAndPassword(email.trim(), password.trim())
                    .addOnSuccessListener {

                        val uid = auth.currentUser!!.uid

                        val userMap = mapOf(
                            "name" to name,
                            "role" to role,
                            "approved" to false
                        )

                        database.child("users").child(uid).setValue(userMap)

                        isLoading = false
                        message = "Registered! Waiting for admin approval."

                        navController.navigate("login") {
                            popUpTo("register") { inclusive = true }
                        }
                    }
                    .addOnFailureListener {
                        isLoading = false
                        message = it.message ?: "Registration Failed"
                    }

            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SlateBlue,
                contentColor = Color.White
            )
        ) {
            Text(if (isLoading) "Registering..." else "Register")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = {
            navController.navigate("login")
        }) {
            Text("Already have an account? Login", color = SlateBlue)
        }

        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}