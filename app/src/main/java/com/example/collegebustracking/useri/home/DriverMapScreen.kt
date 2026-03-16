package com.example.collegebustracking.useri.home

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.example.collegebustracking.model.Bus
@SuppressLint("MissingPermission")
@Composable
fun DriverMapScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    var assignedBus by remember { mutableStateOf<Bus?>(null) }
    var isTripActive by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as android.app.Activity

    var hasLocationPermission by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasLocationPermission = isGranted
        }
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    LaunchedEffect(Unit) {
        database.child("buses")
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    for (child in snapshot.children) {

                        val bus = child.getValue(Bus::class.java)

                        android.util.Log.d("DEBUG_DRIVER", "Bus driverId = ${bus?.driverId}")
                        android.util.Log.d("DEBUG_DRIVER", "Logged in UID = $currentUserId")

                        if (bus != null && bus.driverId == currentUserId) {
                            assignedBus = bus.copy(id = child.key ?: "")
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        3000
    ).setMinUpdateIntervalMillis(2000)
     .setWaitForAccurateLocation(false)
     .build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            val location = locationResult.lastLocation ?: return

            if (isTripActive && assignedBus != null) {

                database.child("buses")
                    .child(assignedBus!!.id)
                    .child("currentLocation")
                    .setValue(
                        mapOf(
                            "latitude" to location.latitude,
                            "longitude" to location.longitude,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
            }
        }
    }

    var isTracking by remember { mutableStateOf(false) }


    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Driver GPS Tracking")

        assignedBus?.let { bus ->
            Text("Assigned Bus: ${bus.busNumber}")
        } ?: Text("No Bus Assigned")

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {

                if (!hasLocationPermission) {
                    permissionLauncher.launch(
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    return@Button
                }

                assignedBus?.let { bus ->

                    database.child("buses")
                        .child(bus.id)
                        .child("isTripActive")
                        .setValue(true)

                    isTripActive = true

                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                }
            },
            enabled = !isTripActive
        ) {
            Text("Start Trip")
        }

        Button(
            onClick = {
                assignedBus?.let { bus ->

                    database.child("buses")
                        .child(bus.id)
                        .child("isTripActive")
                        .setValue(false)

                    isTripActive = false
                    fusedLocationClient.removeLocationUpdates(locationCallback)

                    android.util.Log.d("TRIP_DEBUG", "Location updates stopped")
                }
            },
            enabled = isTripActive
        ) {
            Text("Stop Trip")
        }


    }
}