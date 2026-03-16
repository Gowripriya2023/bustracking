package com.example.collegebustracking.useri

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun RoleSelectionScreen(
    onStudentClick: () -> Unit,
    onDriverClick: () -> Unit,
    onAdminClick: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Button(onClick = onStudentClick) {
            Text("Login as Student")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onDriverClick) {
            Text("Login as Driver")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onAdminClick) {
            Text("Login as Admin")
        }
    }
}