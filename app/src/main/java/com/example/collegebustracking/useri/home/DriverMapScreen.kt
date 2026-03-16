package com.example.collegebustracking.useri.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@SuppressLint("MissingPermission")
@Composable
fun DriverMapScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    val context = LocalContext.current

    // Use MutableState directly (NOT property delegates) so callback can read .value
    val assignedBusState = remember { mutableStateOf<Bus?>(null) }
    val tripActiveState = remember { mutableStateOf(false) }
    val currentLatState = remember { mutableStateOf<Double?>(null) }
    val currentLonState = remember { mutableStateOf<Double?>(null) }
    val statusMessageState = remember { mutableStateOf("Loading...") }

    // Location permission
    var hasLocationPermission by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationRequest = remember {
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000
        ).setMinUpdateIntervalMillis(2000)
         .setWaitForAccurateLocation(false)
         .build()
    }

    // Location callback — uses .value to read CURRENT state (NOT property delegates)
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {

                val location = locationResult.lastLocation ?: return

                // Update UI state
                currentLatState.value = location.latitude
                currentLonState.value = location.longitude

                val bus = assignedBusState.value
                val tripActive = tripActiveState.value

                android.util.Log.d("DRIVER_GPS",
                    "GPS RECEIVED: lat=${location.latitude}, lon=${location.longitude}, " +
                    "tripActive=$tripActive, busId=${bus?.id}")

                if (tripActive && bus != null && bus.id.isNotEmpty()) {

                    database.child("buses")
                        .child(bus.id)
                        .child("currentLocation")
                        .setValue(
                            mapOf(
                                "latitude" to location.latitude,
                                "longitude" to location.longitude,
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                        .addOnSuccessListener {
                            statusMessageState.value =
                                "GPS Sent: %.5f, %.5f".format(location.latitude, location.longitude)
                            android.util.Log.d("DRIVER_GPS", "FIREBASE PUSH SUCCESS")
                        }
                        .addOnFailureListener { e ->
                            statusMessageState.value = "Firebase Error: ${e.message}"
                            android.util.Log.e("DRIVER_GPS", "FIREBASE PUSH FAILED: ${e.message}")
                        }
                } else {
                    android.util.Log.w("DRIVER_GPS",
                        "NOT PUSHING: tripActive=$tripActive, bus=${bus?.id}")
                    statusMessageState.value =
                        "GPS OK but not pushing (trip=$tripActive, bus=${bus?.id})"
                }
            }
        }
    }

    // Find the bus assigned to this driver
    LaunchedEffect(currentUserId) {
        if (currentUserId.isEmpty()) {
            statusMessageState.value = "Not logged in"
            return@LaunchedEffect
        }

        statusMessageState.value = "Finding your assigned bus..."

        database.child("buses")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    var found = false

                    for (child in snapshot.children) {
                        val driverId = child.child("driverId")
                            .getValue(String::class.java) ?: ""

                        if (driverId == currentUserId) {
                            val busNumber = child.child("busNumber")
                                .getValue(String::class.java) ?: ""
                            val routeId = child.child("routeId")
                                .getValue(String::class.java) ?: ""
                            val id = child.key ?: ""

                            assignedBusState.value = Bus(
                                id = id,
                                busNumber = busNumber,
                                routeId = routeId,
                                driverId = driverId
                            )

                            found = true
                            statusMessageState.value = "Bus found: $busNumber"

                            android.util.Log.d("DRIVER_GPS",
                                "ASSIGNED BUS: id=$id, number=$busNumber")
                            break
                        }
                    }

                    if (!found) {
                        statusMessageState.value = "No bus assigned to you"
                        android.util.Log.w("DRIVER_GPS",
                            "NO BUS FOUND for driver UID=$currentUserId")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    statusMessageState.value = "Error: ${error.message}"
                }
            })
    }

    // Request permission on launch
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Clean up location updates when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // Read state values for UI (using .value)
    val assignedBus = assignedBusState.value
    val isTripActive = tripActiveState.value
    val currentLat = currentLatState.value
    val currentLon = currentLonState.value
    val statusMessage = statusMessageState.value

    /* ---- UI ---- */

    Column(modifier = Modifier.fillMaxSize()) {

        // Status bar
        Surface(
            color = if (isTripActive) Color(0xFF1B5E20) else Color(0xFF424242),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isTripActive) "\uD83D\uDFE2 Trip Active" else "\u23F8 Trip Stopped",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                if (assignedBus != null) {
                    Text(
                        text = "Bus: ${assignedBus.busNumber}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = statusMessage,
                    color = Color(0xFFB0BEC5),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Map showing driver's own location
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                Configuration.getInstance().load(
                    ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                )
                val mapView = MapView(ctx)
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setMultiTouchControls(true)
                mapView.controller.setZoom(16.5)
                mapView.controller.setCenter(GeoPoint(10.8505, 76.2711))
                mapView
            },
            update = { map ->
                map.overlays.clear()

                if (currentLat != null && currentLon != null) {
                    val marker = Marker(map)
                    marker.position = GeoPoint(currentLat, currentLon)
                    marker.title = "Your Location"
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(marker)
                    map.controller.setCenter(GeoPoint(currentLat, currentLon))
                }

                map.invalidate()
            }
        )

        // Start / Stop buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (!hasLocationPermission) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        return@Button
                    }

                    val bus = assignedBusState.value
                    if (bus == null) {
                        Toast.makeText(context, "No bus assigned!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // FIRST set state so callback can read it
                    tripActiveState.value = true
                    statusMessageState.value = "Starting GPS tracking..."

                    // Set trip active in Firebase
                    database.child("buses")
                        .child(bus.id)
                        .child("isTripActive")
                        .setValue(true)

                    // Start location updates
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )

                    android.util.Log.d("DRIVER_GPS", "START TRIP pressed, busId=${bus.id}")
                    Toast.makeText(context, "Trip Started!", Toast.LENGTH_SHORT).show()
                },
                enabled = !isTripActive && assignedBus != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Start Trip")
            }

            Button(
                onClick = {
                    val bus = assignedBusState.value
                    if (bus != null) {
                        database.child("buses")
                            .child(bus.id)
                            .child("isTripActive")
                            .setValue(false)

                        database.child("buses")
                            .child(bus.id)
                            .child("currentLocation")
                            .removeValue()
                    }

                    tripActiveState.value = false
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                    statusMessageState.value = "Trip stopped"

                    Toast.makeText(context, "Trip Stopped!", Toast.LENGTH_SHORT).show()
                },
                enabled = isTripActive,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) {
                Text("Stop Trip")
            }
        }

        // GPS coordinates display
        if (currentLat != null && currentLon != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "GPS: $currentLat, $currentLon",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}